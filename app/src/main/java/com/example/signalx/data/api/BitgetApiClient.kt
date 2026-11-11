package com.example.signalx.data.api

import android.util.Log
import com.example.signalx.data.model.Candle
import com.example.signalx.data.model.MarketType
import com.example.signalx.data.model.OrderBook
import com.example.signalx.data.model.OrderBookLevel
import com.example.signalx.data.model.Ticker
import com.example.signalx.data.model.TimeFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BitgetApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "BitgetApiClient"
        private const val BASE_URL = "https://api.bitget.com"
    }

    /**
     * Synchronize server time with /api/v2/public/time
     */
    suspend fun syncServerTime(): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val request = Request.Builder()
            .url("$BASE_URL/api/v2/public/time")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val end = System.currentTimeMillis()
            val body = response.body?.string() ?: throw IllegalStateException("Empty time response")
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: throw IllegalStateException("Empty time data: ${json.optString("msg")}")
            val serverTimeStr = data.optString("serverTime", "")
            val serverTimeMs = serverTimeStr.toLongOrNull() ?: System.currentTimeMillis()
            BitgetTimeSync.updateServerTime(serverTimeMs, start, end)
            serverTimeMs
        }
    }

    /**
     * Fetch list of online Spot symbols and cache exact pricePrecision
     */
    suspend fun getSpotSymbols(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v2/spot/public/symbols")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@withContext emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val status = obj.optString("status")
                val symbol = obj.optString("symbol")
                val quoteCoin = obj.optString("quoteCoin")
                val precisionStr = obj.optString("pricePrecision", "2")
                val precision = precisionStr.toIntOrNull() ?: 2

                if (symbol.isNotEmpty()) {
                    PriceFormatter.setPrecision(symbol, MarketType.SPOT, precision)
                }

                if (status == "online" && quoteCoin == "USDT") {
                    list.add(symbol)
                }
            }
            list
        }
    }

    /**
     * Fetch list of online USDT-Futures contracts and cache exact pricePlace
     */
    suspend fun getFuturesSymbols(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v2/mix/market/contracts?productType=USDT-FUTURES")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@withContext emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val symbolStatus = obj.optString("symbolStatus")
                val symbol = obj.optString("symbol")
                val pricePlaceStr = obj.optString("pricePlace", "2")
                val pricePlace = pricePlaceStr.toIntOrNull() ?: 2

                if (symbol.isNotEmpty()) {
                    PriceFormatter.setPrecision(symbol, MarketType.FUTURES, pricePlace)
                }

                if (symbolStatus == "normal" || symbolStatus == "online") {
                    list.add(symbol)
                }
            }
            list
        }
    }

    /**
     * Fetch all Spot tickers in one bulk request
     */
    suspend fun getSpotTickers(): List<Ticker> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v2/spot/market/tickers")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@withContext emptyList()
            parseTickers(body, MarketType.SPOT)
        }
    }

    /**
     * Fetch all Futures tickers in one bulk request
     */
    suspend fun getFuturesTickers(): List<Ticker> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v2/mix/market/tickers?productType=USDT-FUTURES")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@withContext emptyList()
            parseTickers(body, MarketType.FUTURES)
        }
    }

    /**
     * Fetch single ticker via REST (fallback when WebSocket is stale or disconnected)
     */
    suspend fun getSingleTicker(symbol: String, marketType: MarketType): Ticker? = withContext(Dispatchers.IO) {
        try {
            val endpoint = if (marketType == MarketType.SPOT) {
                "$BASE_URL/api/v2/spot/market/tickers?symbol=$symbol"
            } else {
                "$BASE_URL/api/v2/mix/market/tickers?productType=USDT-FUTURES&symbol=$symbol"
            }
            val request = Request.Builder().url(endpoint).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                val tickers = parseTickers(body, marketType)
                return@withContext tickers.firstOrNull { it.symbol == symbol }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get single REST ticker for $symbol: ${e.message}")
            null
        }
    }

    private fun parseTickers(jsonString: String, marketType: MarketType): List<Ticker> {
        val list = mutableListOf<Ticker>()
        try {
            val json = JSONObject(jsonString)
            val data = json.optJSONArray("data") ?: return emptyList()
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val symbol = obj.optString("symbol")
                if (!symbol.endsWith("USDT")) continue

                val rawLastPr = obj.optString("lastPr", "")
                val rawBidPr = obj.optString("bidPr", "")
                val rawAskPr = obj.optString("askPr", "")
                val rawIndexPr = obj.optString("indexPrice", "")

                val lastPr = rawLastPr.toDoubleOrNull() ?: 0.0
                val high24h = obj.optDouble("high24h", 0.0)
                val low24h = obj.optDouble("low24h", 0.0)
                val change24h = obj.optDouble("change24h", 0.0)
                val baseVol = obj.optDouble("baseVolume", 0.0)
                val quoteVol = obj.optDouble("quoteVolume", 0.0)
                val ts = obj.optLong("ts", System.currentTimeMillis())
                val bid = rawBidPr.toDoubleOrNull() ?: 0.0
                val ask = rawAskPr.toDoubleOrNull() ?: 0.0
                val indexPrice = rawIndexPr.toDoubleOrNull() ?: 0.0
                val fundingRate = obj.optDouble("fundingRate", 0.0)
                val openInterest = obj.optDouble("holdingAmount", 0.0)
                val precision = PriceFormatter.getPrecision(symbol, marketType) ?: 2

                if (rawLastPr.isNotEmpty() && lastPr > 0.0) {
                    list.add(
                        Ticker(
                            symbol = symbol,
                            lastPrice = lastPr,
                            high24h = high24h,
                            low24h = low24h,
                            change24h = change24h,
                            volume24h = baseVol,
                            quoteVolume24h = quoteVol,
                            timestamp = ts,
                            bid = bid,
                            ask = ask,
                            markPrice = if (marketType == MarketType.FUTURES) lastPr else 0.0,
                            indexPrice = indexPrice,
                            fundingRate = fundingRate,
                            openInterest = openInterest,
                            marketType = marketType,
                            pricePrecision = precision,
                            rawLastPrice = rawLastPr,
                            rawMarkPrice = if (marketType == MarketType.FUTURES) rawLastPr else "",
                            rawIndexPrice = rawIndexPr,
                            rawBidPrice = rawBidPr,
                            rawAskPrice = rawAskPr,
                            priceSource = "REST"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tickers: ${e.message}")
        }
        return list
    }

    /**
     * Fetch single Futures contract mark price and index price
     */
    suspend fun getFuturesSymbolPrice(symbol: String): Pair<Double, Double> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/v2/mix/market/symbol-price?productType=USDT-FUTURES&symbol=$symbol")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext Pair(0.0, 0.0)
                val json = JSONObject(body)
                val code = json.optString("code")
                if (code != "00000") {
                    return@withContext Pair(0.0, 0.0)
                }
                val data = json.optJSONArray("data") ?: return@withContext Pair(0.0, 0.0)
                if (data.length() > 0) {
                    val obj = data.optJSONObject(0) ?: return@withContext Pair(0.0, 0.0)
                    val markPrice = obj.optDouble("markPrice", 0.0)
                    val indexPrice = obj.optDouble("indexPrice", 0.0)
                    return@withContext Pair(markPrice, indexPrice)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get futures mark price for $symbol: ${e.message}")
        }
        Pair(0.0, 0.0)
    }

    /**
     * Fetch Open Interest for active futures symbol
     */
    suspend fun getFuturesOpenInterest(symbol: String): Double = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/v2/mix/market/open-interest?productType=USDT-FUTURES&symbol=$symbol")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext 0.0
                val json = JSONObject(body)
                val code = json.optString("code")
                if (code != "00000") return@withContext 0.0
                val data = json.optJSONObject("data") ?: return@withContext 0.0
                val list = data.optJSONArray("openInterestList") ?: return@withContext 0.0
                if (list.length() > 0) {
                    val obj = list.optJSONObject(0) ?: return@withContext 0.0
                    return@withContext obj.optDouble("size", 0.0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get open interest for $symbol: ${e.message}")
        }
        0.0
    }

    /**
     * Fetch Current Funding Rate for active futures symbol
     */
    suspend fun getFuturesFundingRate(symbol: String): Double = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/v2/mix/market/current-fund-rate?productType=USDT-FUTURES&symbol=$symbol")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext 0.0
                val json = JSONObject(body)
                val code = json.optString("code")
                if (code != "00000") return@withContext 0.0
                val data = json.optJSONArray("data") ?: return@withContext 0.0
                if (data.length() > 0) {
                    val obj = data.optJSONObject(0) ?: return@withContext 0.0
                    return@withContext obj.optDouble("fundingRate", 0.0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get funding rate for $symbol: ${e.message}")
        }
        0.0
    }

    /**
     * Fetch candles for the given symbol and timeframe.
     * Automatically handles native vs aggregated timeframes (e.g. 3m aggregated from 1m, 2h from 1h).
     */
    suspend fun getCandles(
        symbol: String,
        marketType: MarketType,
        timeFrame: TimeFrame,
        limit: Int = 300
    ): List<Candle> = withContext(Dispatchers.IO) {
        if (timeFrame.isAggregated && timeFrame.sourceTimeFrame != null) {
            // Need source candles to aggregate (e.g. 3m needs 3x limit of 1m)
            val multiplier = (timeFrame.durationMs / timeFrame.sourceTimeFrame.durationMs).toInt()
            val fetchLimit = (limit * multiplier).coerceAtMost(1000)
            val sourceCandles = fetchRawCandles(symbol, marketType, timeFrame.sourceTimeFrame, fetchLimit)
            return@withContext aggregateCandles(sourceCandles, timeFrame.durationMs)
        }

        return@withContext fetchRawCandles(symbol, marketType, timeFrame, limit)
    }

    private fun fetchRawCandles(
        symbol: String,
        marketType: MarketType,
        timeFrame: TimeFrame,
        limit: Int
    ): List<Candle> {
        val granularity = if (marketType == MarketType.SPOT) timeFrame.spotGranularity else timeFrame.futuresGranularity
        val endpoint = if (marketType == MarketType.SPOT) {
            "$BASE_URL/api/v2/spot/market/candles?symbol=$symbol&granularity=$granularity&limit=$limit"
        } else {
            "$BASE_URL/api/v2/mix/market/candles?symbol=$symbol&productType=USDT-FUTURES&granularity=$granularity&limit=$limit"
        }

        val request = Request.Builder().url(endpoint).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return emptyList()
            val list = mutableListOf<Candle>()
            for (i in 0 until data.length()) {
                val item = data.getJSONArray(i)
                val ts = item.getString(0).toLong()
                val rawO = item.getString(1)
                val rawH = item.getString(2)
                val rawL = item.getString(3)
                val rawC = item.getString(4)
                val rawV = item.getString(5)
                val o = rawO.toDoubleOrNull() ?: 0.0
                val h = rawH.toDoubleOrNull() ?: 0.0
                val l = rawL.toDoubleOrNull() ?: 0.0
                val c = rawC.toDoubleOrNull() ?: 0.0
                val v = rawV.toDoubleOrNull() ?: 0.0
                val qv = if (item.length() > 6) item.getString(6).toDoubleOrNull() ?: 0.0 else 0.0

                list.add(
                    Candle(
                        timestamp = ts,
                        open = o,
                        high = h,
                        low = l,
                        close = c,
                        volume = v,
                        quoteVolume = qv,
                        isClosed = true,
                        rawOpen = rawO,
                        rawHigh = rawH,
                        rawLow = rawL,
                        rawClose = rawC
                    )
                )
            }
            list.sortBy { it.timestamp }
            return list
        }
    }

    /**
     * Aggregates fine candles (e.g. 1m) into target timeframe (e.g. 3m)
     * using exact exchange-aligned UTC boundaries.
     */
    fun aggregateCandles(sourceCandles: List<Candle>, targetDurationMs: Long): List<Candle> {
        if (sourceCandles.isEmpty()) return emptyList()

        val grouped = sourceCandles.groupBy { (it.timestamp / targetDurationMs) * targetDurationMs }
        val sortedKeys = grouped.keys.sorted()

        val aggregated = mutableListOf<Candle>()
        val currentServerTime = BitgetTimeSync.getCorrectedServerTime()

        for (bucketTime in sortedKeys) {
            val bucketCandles = grouped[bucketTime] ?: continue
            if (bucketCandles.isEmpty()) continue

            val first = bucketCandles.first()
            val last = bucketCandles.last()
            val open = first.open
            val close = last.close
            val rawOpen = first.rawOpen
            val rawClose = last.rawClose

            var high = Double.NEGATIVE_INFINITY
            var low = Double.POSITIVE_INFINITY
            var rawHigh = ""
            var rawLow = ""
            var totalVolume = 0.0
            var totalQuoteVolume = 0.0

            for (c in bucketCandles) {
                if (c.high > high) {
                    high = c.high
                    rawHigh = c.rawHigh
                }
                if (c.low < low) {
                    low = c.low
                    rawLow = c.rawLow
                }
                totalVolume += c.volume
                totalQuoteVolume += c.quoteVolume
            }

            val isClosed = (bucketTime + targetDurationMs) <= currentServerTime
            aggregated.add(
                Candle(
                    timestamp = bucketTime,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = totalVolume,
                    quoteVolume = totalQuoteVolume,
                    isClosed = isClosed,
                    rawOpen = rawOpen,
                    rawHigh = rawHigh,
                    rawLow = rawLow,
                    rawClose = rawClose
                )
            )
        }

        return aggregated
    }

    /**
     * Fetch orderbook top 15 depth
     */
    suspend fun getOrderBook(symbol: String, marketType: MarketType): OrderBook? = withContext(Dispatchers.IO) {
        try {
            val endpoint = if (marketType == MarketType.SPOT) {
                "$BASE_URL/api/v2/spot/market/orderbook?symbol=$symbol&type=step0&limit=15"
            } else {
                "$BASE_URL/api/v2/mix/market/merge-depth?symbol=$symbol&productType=USDT-FUTURES&limit=15"
            }

            val request = Request.Builder().url(endpoint).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val code = json.optString("code")
                if (code != "00000") {
                    return@withContext null
                }
                val data = json.optJSONObject("data") ?: return@withContext null
                val asksArr = data.optJSONArray("asks") ?: JSONArray()
                val bidsArr = data.optJSONArray("bids") ?: JSONArray()

                val asks = mutableListOf<OrderBookLevel>()
                for (i in 0 until asksArr.length()) {
                    val pair = asksArr.optJSONArray(i) ?: continue
                    if (pair.length() < 2) continue
                    val rawP = pair.optString(0, "")
                    val rawQ = pair.optString(1, "")
                    if (rawP.isNotEmpty()) {
                        asks.add(OrderBookLevel(rawP.toDoubleOrNull() ?: 0.0, rawQ.toDoubleOrNull() ?: 0.0, rawP))
                    }
                }

                val bids = mutableListOf<OrderBookLevel>()
                for (i in 0 until bidsArr.length()) {
                    val pair = bidsArr.optJSONArray(i) ?: continue
                    if (pair.length() < 2) continue
                    val rawP = pair.optString(0, "")
                    val rawQ = pair.optString(1, "")
                    if (rawP.isNotEmpty()) {
                        bids.add(OrderBookLevel(rawP.toDoubleOrNull() ?: 0.0, rawQ.toDoubleOrNull() ?: 0.0, rawP))
                    }
                }

                return@withContext OrderBook(
                    asks = asks.sortedBy { it.price },
                    bids = bids.sortedByDescending { it.price },
                    timestamp = data.optLong("ts", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get orderbook for $symbol: ${e.message}")
            null
        }
    }

    /**
     * Fetch external context from free public APIs:
     * - Alternative.me Fear & Greed Index
     * - CoinGecko Global Market (BTC/ETH Dominance, Market Cap)
     * All non-free data (Whale/on-chain, ETF, news, liquidation heatmaps, Bookmap/ATAS/CoinGlass/CryptoQuant)
     * are strictly tagged as "NOT AVAILABLE (no free API)".
     */
    suspend fun getMarketContext(): com.example.signalx.data.model.MarketContextInfo = withContext(Dispatchers.IO) {
        var fngVal = 50
        var fngClass = "Neutral"
        var btcDom = 58.0
        var ethDom = 14.0
        var mcap = "$2.65T"

        // 1. Fear and Greed from alternative.me
        try {
            val fngReq = Request.Builder().url("https://api.alternative.me/fng/?limit=1").get().build()
            client.newCall(fngReq).execute().use { resp ->
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val data = json.optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        val item = data.optJSONObject(0)
                        fngVal = item?.optString("value", "50")?.toIntOrNull() ?: 50
                        fngClass = item?.optString("value_classification", "Neutral") ?: "Neutral"
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Fear & Greed fetch failed: ${e.message}")
        }

        // 2. CoinGecko Global Market (Dominance & Cap)
        try {
            val cgReq = Request.Builder().url("https://api.coingecko.com/api/v3/global").get().build()
            client.newCall(cgReq).execute().use { resp ->
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val data = json.optJSONObject("data")
                    val domMap = data?.optJSONObject("market_cap_percentage")
                    if (domMap != null) {
                        btcDom = domMap.optDouble("btc", 58.0)
                        ethDom = domMap.optDouble("eth", 14.0)
                    }
                    val totalMcap = data?.optJSONObject("total_market_cap")
                    val usdCap = totalMcap?.optDouble("usd", 2.65e12) ?: 2.65e12
                    mcap = "$" + String.format(java.util.Locale.US, "%.2fT", usdCap / 1e12)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "CoinGecko global fetch failed: ${e.message}")
        }

        com.example.signalx.data.model.MarketContextInfo(
            fearAndGreedValue = fngVal,
            fearAndGreedClassification = fngClass,
            btcDominancePct = btcDom,
            ethDominancePct = ethDom,
            totalMarketCapUsd = mcap
        )
    }
}
