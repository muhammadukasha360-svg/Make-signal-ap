package com.example.signalx.data.api

import android.util.Log
import com.example.signalx.data.model.Candle
import com.example.signalx.data.model.CvdData
import com.example.signalx.data.model.Direction
import com.example.signalx.data.model.LiquidityWall
import com.example.signalx.data.model.MarketType
import com.example.signalx.data.model.OrderBookImbalanceData
import com.example.signalx.data.model.OrderBookLevel
import com.example.signalx.data.model.Ticker
import com.example.signalx.data.model.TimeFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

enum class ConnectionStatus {
    LIVE,
    RECONNECTING,
    OFFLINE
}

class BitgetWebSocketManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(0, TimeUnit.MILLISECONDS)
        .build()
) {
    companion object {
        private const val TAG = "BitgetWS"
        private const val WS_URL = "wss://ws.bitget.com/v2/ws/public"
        private const val PING_INTERVAL_MS = 25_000L
        private const val STALE_THRESHOLD_SECONDS = 15L
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var staleCheckJob: Job? = null

    private var activeSymbol: String = "BTCUSDT"
    private var activeMarketType: MarketType = MarketType.SPOT
    private var activeTimeFrame: TimeFrame = TimeFrame.M15

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.OFFLINE)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _lastTickTime = MutableStateFlow(0L)
    val lastTickTime: StateFlow<Long> = _lastTickTime.asStateFlow()

    private val _dataAgeSeconds = MutableStateFlow(0L)
    val dataAgeSeconds: StateFlow<Long> = _dataAgeSeconds.asStateFlow()

    private val _isDataStale = MutableStateFlow(false)
    val isDataStale: StateFlow<Boolean> = _isDataStale.asStateFlow()

    private val _latestTicker = MutableStateFlow<Ticker?>(null)
    val latestTicker: StateFlow<Ticker?> = _latestTicker.asStateFlow()

    private val _latestCandleTick = MutableStateFlow<Candle?>(null)
    val latestCandleTick: StateFlow<Candle?> = _latestCandleTick.asStateFlow()

    private val _cvdData = MutableStateFlow(CvdData())
    val cvdData: StateFlow<CvdData> = _cvdData.asStateFlow()

    private val _orderBookImbalance = MutableStateFlow(OrderBookImbalanceData())
    val orderBookImbalance: StateFlow<OrderBookImbalanceData> = _orderBookImbalance.asStateFlow()

    private var runningCvd = BigDecimal.ZERO
    private var sessionBuyVol = BigDecimal.ZERO
    private var sessionSellVol = BigDecimal.ZERO
    private var recentDelta = BigDecimal.ZERO
    private val cvdHistory = ArrayDeque<BigDecimal>()

    private var reconnectAttempts = 0
    private var isUserDisconnected = false

    fun connect(symbol: String, marketType: MarketType, timeFrame: TimeFrame = TimeFrame.M15) {
        activeSymbol = symbol
        activeMarketType = marketType
        activeTimeFrame = timeFrame
        isUserDisconnected = false
        reconnectAttempts = 0
        initiateConnection()
        startPingLoop()
        startStaleChecker()
    }

    fun switchSubscription(symbol: String, marketType: MarketType, timeFrame: TimeFrame = activeTimeFrame) {
        if (activeSymbol == symbol && activeMarketType == marketType && activeTimeFrame == timeFrame && webSocket != null) {
            return
        }
        val oldSymbol = activeSymbol
        val oldMarketType = activeMarketType
        val oldTf = activeTimeFrame

        activeSymbol = symbol
        activeMarketType = marketType
        activeTimeFrame = timeFrame

        runningCvd = BigDecimal.ZERO
        sessionBuyVol = BigDecimal.ZERO
        sessionSellVol = BigDecimal.ZERO
        recentDelta = BigDecimal.ZERO
        cvdHistory.clear()
        _cvdData.value = CvdData()
        _orderBookImbalance.value = OrderBookImbalanceData()

        if (_connectionStatus.value == ConnectionStatus.LIVE && webSocket != null) {
            unsubscribe(oldSymbol, oldMarketType, oldTf)
            subscribe(activeSymbol, activeMarketType, activeTimeFrame)
        } else {
            connect(symbol, marketType, timeFrame)
        }
    }

    private fun initiateConnection() {
        _connectionStatus.value = ConnectionStatus.RECONNECTING
        val request = Request.Builder().url(WS_URL).build()
        webSocket = client.newWebSocket(request, createListener())
    }

    private fun createListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully to Bitget")
                _connectionStatus.value = ConnectionStatus.LIVE
                _isDataStale.value = false
                _dataAgeSeconds.value = 0L
                reconnectAttempts = 0
                subscribe(activeSymbol, activeMarketType, activeTimeFrame)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                _lastTickTime.value = System.currentTimeMillis()
                _dataAgeSeconds.value = 0L
                _isDataStale.value = false

                if (text == "pong") {
                    return
                }

                try {
                    val json = JSONObject(text)
                    val action = json.optString("action")
                    if (action == "snapshot" || action == "update") {
                        val arg = json.optJSONObject("arg")
                        val channel = arg?.optString("channel") ?: ""
                        val instId = arg?.optString("instId") ?: ""
                        val dataArr = json.optJSONArray("data") ?: return

                        if (channel == "ticker" && instId == activeSymbol) {
                            if (dataArr.length() > 0) {
                                val obj = dataArr.optJSONObject(0) ?: return
                                val rawLastPr = obj.optString("lastPr", "")
                                val rawBidPr = obj.optString("bidPr", "")
                                val rawAskPr = obj.optString("askPr", "")
                                val rawIndexPr = obj.optString("indexPrice", "")
                                val rawMarkPr = obj.optString("markPrice", rawLastPr)

                                val lastPr = rawLastPr.toDoubleOrNull() ?: 0.0
                                val high24h = obj.optDouble("high24h", 0.0)
                                val low24h = obj.optDouble("low24h", 0.0)
                                val change24h = obj.optDouble("change24h", 0.0)
                                val baseVol = obj.optDouble("baseVolume", 0.0)
                                val quoteVol = obj.optDouble("quoteVolume", 0.0)
                                val ts = obj.optLong("ts", System.currentTimeMillis())
                                val bid = rawBidPr.toDoubleOrNull() ?: 0.0
                                val ask = rawAskPr.toDoubleOrNull() ?: 0.0
                                val markPrice = rawMarkPr.toDoubleOrNull() ?: lastPr
                                val indexPrice = rawIndexPr.toDoubleOrNull() ?: 0.0
                                val fundingRate = obj.optDouble("fundingRate", 0.0)
                                val openInterest = obj.optDouble("holdingAmount", 0.0)
                                val precision = PriceFormatter.getPrecision(instId, activeMarketType) ?: 2

                                if (rawLastPr.isNotEmpty() && lastPr > 0.0) {
                                    _latestTicker.value = Ticker(
                                        symbol = instId,
                                        lastPrice = lastPr,
                                        high24h = high24h,
                                        low24h = low24h,
                                        change24h = change24h,
                                        volume24h = baseVol,
                                        quoteVolume24h = quoteVol,
                                        timestamp = ts,
                                        bid = bid,
                                        ask = ask,
                                        markPrice = markPrice,
                                        indexPrice = indexPrice,
                                        fundingRate = fundingRate,
                                        openInterest = openInterest,
                                        marketType = activeMarketType,
                                        pricePrecision = precision,
                                        rawLastPrice = rawLastPr,
                                        rawMarkPrice = rawMarkPr,
                                        rawIndexPrice = rawIndexPr,
                                        rawBidPrice = rawBidPr,
                                        rawAskPrice = rawAskPr,
                                        priceSource = "WS"
                                    )
                                }
                            }
                        } else if (channel.startsWith("candle") && instId == activeSymbol) {
                            if (dataArr.length() > 0) {
                                val candleData = dataArr.optJSONArray(0) ?: return
                                if (candleData.length() < 6) return
                                val ts = candleData.optString(0, "0").toLongOrNull() ?: 0L
                                val rawO = candleData.optString(1, "0")
                                val rawH = candleData.optString(2, "0")
                                val rawL = candleData.optString(3, "0")
                                val rawC = candleData.optString(4, "0")
                                val rawV = candleData.optString(5, "0")
                                val o = rawO.toDoubleOrNull() ?: 0.0
                                val h = rawH.toDoubleOrNull() ?: 0.0
                                val l = rawL.toDoubleOrNull() ?: 0.0
                                val c = rawC.toDoubleOrNull() ?: 0.0
                                val v = rawV.toDoubleOrNull() ?: 0.0
                                val qv = if (candleData.length() > 6) candleData.optString(6, "0").toDoubleOrNull() ?: 0.0 else 0.0

                                _latestCandleTick.value = Candle(
                                    timestamp = ts,
                                    open = o,
                                    high = h,
                                    low = l,
                                    close = c,
                                    volume = v,
                                    quoteVolume = qv,
                                    isClosed = false,
                                    rawOpen = rawO,
                                    rawHigh = rawH,
                                    rawLow = rawL,
                                    rawClose = rawC
                                )
                            }
                        } else if (channel == "trade" && instId == activeSymbol) {
                            processTrades(dataArr, instId)
                        } else if ((channel == "books15" || channel == "books") && instId == activeSymbol) {
                            processOrderBook(dataArr, instId)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse WS payload: ${e.message}")
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                _connectionStatus.value = ConnectionStatus.RECONNECTING
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed ($code): $reason")
                if (!isUserDisconnected) {
                    scheduleReconnect()
                } else {
                    _connectionStatus.value = ConnectionStatus.OFFLINE
                }
            }
        }
    }

    fun processTrades(dataArr: org.json.JSONArray, symbol: String) {
        val count = dataArr.length()
        if (count == 0) return

        var batchDelta = BigDecimal.ZERO
        for (i in 0 until count) {
            val item = dataArr.optJSONObject(i) ?: continue
            val side = item.optString("side").lowercase()
            val sizeStr = item.optString("size", "0")
            val size = sizeStr.toBigDecimalOrNull() ?: BigDecimal.ZERO

            if (side == "buy") {
                sessionBuyVol = sessionBuyVol.add(size)
                runningCvd = runningCvd.add(size)
                batchDelta = batchDelta.add(size)
            } else if (side == "sell") {
                sessionSellVol = sessionSellVol.add(size)
                runningCvd = runningCvd.subtract(size)
                batchDelta = batchDelta.subtract(size)
            }
        }
        recentDelta = batchDelta

        cvdHistory.addLast(runningCvd)
        while (cvdHistory.size > 30) {
            cvdHistory.removeFirst()
        }

        val trend = if (cvdHistory.size >= 5) {
            val oldest = cvdHistory.first()
            if (runningCvd > oldest) Direction.UP else if (runningCvd < oldest) Direction.DOWN else Direction.SIDEWAYS
        } else Direction.SIDEWAYS

        val formatted = PriceFormatter.formatClean(runningCvd.toDouble(), 4)
        _cvdData.value = CvdData(
            cumulativeVolumeDelta = runningCvd,
            sessionBuyVolume = sessionBuyVol,
            sessionSellVolume = sessionSellVol,
            recentTradeDelta = recentDelta,
            cvdTrend = trend,
            formattedCvd = formatted
        )
    }

    fun injectTrade(side: String, sizeStr: String) {
        val size = sizeStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
        injectTrade(side, size)
    }

    fun injectTrade(side: String, size: BigDecimal) {
        if (side.equals("buy", ignoreCase = true)) {
            sessionBuyVol = sessionBuyVol.add(size)
            runningCvd = runningCvd.add(size)
            recentDelta = recentDelta.add(size)
        } else if (side.equals("sell", ignoreCase = true)) {
            sessionSellVol = sessionSellVol.add(size)
            runningCvd = runningCvd.subtract(size)
            recentDelta = recentDelta.subtract(size)
        }
        cvdHistory.addLast(runningCvd)
        while (cvdHistory.size > 30) cvdHistory.removeFirst()

        val trend = if (cvdHistory.size >= 5) {
            val oldest = cvdHistory.first()
            if (runningCvd > oldest) Direction.UP else if (runningCvd < oldest) Direction.DOWN else Direction.SIDEWAYS
        } else Direction.SIDEWAYS

        val formatted = PriceFormatter.formatClean(runningCvd.toDouble(), 4)
        _cvdData.value = CvdData(
            cumulativeVolumeDelta = runningCvd,
            sessionBuyVolume = sessionBuyVol,
            sessionSellVolume = sessionSellVol,
            recentTradeDelta = recentDelta,
            cvdTrend = trend,
            formattedCvd = formatted
        )
    }

    fun processOrderBook(dataArr: org.json.JSONArray, symbol: String) {
        if (dataArr.length() == 0) return
        val item = dataArr.optJSONObject(0) ?: return
        val asksArr = item.optJSONArray("asks") ?: org.json.JSONArray()
        val bidsArr = item.optJSONArray("bids") ?: org.json.JSONArray()

        var totalAskVol = BigDecimal.ZERO
        var totalBidVol = BigDecimal.ZERO
        val askLevels = mutableListOf<OrderBookLevel>()
        val bidLevels = mutableListOf<OrderBookLevel>()

        for (i in 0 until asksArr.length()) {
            val pair = asksArr.optJSONArray(i) ?: continue
            val pStr = pair.optString(0, "")
            val qStr = pair.optString(1, "")
            val p = pStr.toDoubleOrNull() ?: 0.0
            val q = qStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
            if (p > 0.0 && q > BigDecimal.ZERO) {
                askLevels.add(OrderBookLevel(p, q.toDouble(), pStr))
                totalAskVol = totalAskVol.add(q)
            }
        }

        for (i in 0 until bidsArr.length()) {
            val pair = bidsArr.optJSONArray(i) ?: continue
            val pStr = pair.optString(0, "")
            val qStr = pair.optString(1, "")
            val p = pStr.toDoubleOrNull() ?: 0.0
            val q = qStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
            if (p > 0.0 && q > BigDecimal.ZERO) {
                bidLevels.add(OrderBookLevel(p, q.toDouble(), pStr))
                totalBidVol = totalBidVol.add(q)
            }
        }

        val totalVol = totalBidVol.add(totalAskVol)
        val imbalancePct = if (totalVol > BigDecimal.ZERO) {
            (totalBidVol.subtract(totalAskVol).toDouble() / totalVol.toDouble()) * 100.0
        } else 0.0

        val totalLevels = askLevels.size + bidLevels.size
        val avgVol = if (totalLevels > 0) totalVol.divide(BigDecimal.valueOf(totalLevels.toLong()), 4, RoundingMode.HALF_UP) else BigDecimal.ONE
        val wallThreshold = avgVol.multiply(BigDecimal.valueOf(2.5))

        val currentPrice = _latestTicker.value?.lastPrice ?: (bidLevels.firstOrNull()?.price ?: 0.0)

        val bidWalls = bidLevels.filter { it.quantity.toBigDecimal() >= wallThreshold }.take(3).map {
            val dist = if (currentPrice > 0) kotlin.math.abs(currentPrice - it.price) / currentPrice * 100.0 else 0.0
            LiquidityWall(
                price = it.price.toBigDecimal(),
                formattedPrice = PriceFormatter.formatPrice(symbol, activeMarketType, it.rawPrice.ifEmpty { it.price.toString() }),
                volume = it.quantity.toBigDecimal(),
                isBid = true,
                distancePct = dist
            )
        }

        val askWalls = askLevels.filter { it.quantity.toBigDecimal() >= wallThreshold }.take(3).map {
            val dist = if (currentPrice > 0) kotlin.math.abs(it.price - currentPrice) / currentPrice * 100.0 else 0.0
            LiquidityWall(
                price = it.price.toBigDecimal(),
                formattedPrice = PriceFormatter.formatPrice(symbol, activeMarketType, it.rawPrice.ifEmpty { it.price.toString() }),
                volume = it.quantity.toBigDecimal(),
                isBid = false,
                distancePct = dist
            )
        }

        val isAbsorption = (recentDelta.abs() > avgVol.multiply(BigDecimal.valueOf(1.5))) && (bidWalls.isNotEmpty() || askWalls.isNotEmpty())
        val absDesc = if (isAbsorption) {
            if (recentDelta > BigDecimal.ZERO) "Buyer absorption into ask liquidity wall" else "Seller absorption into bid liquidity wall"
        } else "Normal order flow"

        _orderBookImbalance.value = OrderBookImbalanceData(
            imbalancePct = imbalancePct,
            totalBidVolume = totalBidVol,
            totalAskVolume = totalAskVol,
            bidWalls = bidWalls,
            askWalls = askWalls,
            isAbsorptionDetected = isAbsorption,
            absorptionDescription = absDesc
        )
    }

    private fun getChannelForTimeFrame(tf: TimeFrame): String {
        return when (tf) {
            TimeFrame.M1, TimeFrame.M3 -> "candle1m"
            TimeFrame.M5 -> "candle5m"
            TimeFrame.M15 -> "candle15m"
            TimeFrame.M30 -> "candle30m"
            TimeFrame.H1, TimeFrame.H2 -> "candle1H"
            TimeFrame.H4 -> "candle4H"
            TimeFrame.H6 -> "candle6H"
            TimeFrame.H12 -> "candle12H"
            TimeFrame.D1 -> "candle1D"
            TimeFrame.W1 -> "candle1W"
            TimeFrame.MONTH1 -> "candle1M"
        }
    }

    private fun subscribe(symbol: String, marketType: MarketType, tf: TimeFrame) {
        val instType = if (marketType == MarketType.SPOT) "SPOT" else "USDT-FUTURES"
        val tfChannel = getChannelForTimeFrame(tf)
        val channels = mutableSetOf("ticker", "trade", "books15", "candle1m", tfChannel)

        val args = channels.map { ch ->
            """{"instType": "$instType", "channel": "$ch", "instId": "$symbol"}"""
        }.joinToString(",")

        val payload = """
            {
                "op": "subscribe",
                "args": [$args]
            }
        """.trimIndent()
        webSocket?.send(payload)
        Log.d(TAG, "Subscribed to $symbol ($instType) on $channels")
    }

    private fun unsubscribe(symbol: String, marketType: MarketType, tf: TimeFrame) {
        val instType = if (marketType == MarketType.SPOT) "SPOT" else "USDT-FUTURES"
        val tfChannel = getChannelForTimeFrame(tf)
        val channels = mutableSetOf("ticker", "trade", "books15", "candle1m", tfChannel)

        val args = channels.map { ch ->
            """{"instType": "$instType", "channel": "$ch", "instId": "$symbol"}"""
        }.joinToString(",")

        val payload = """
            {
                "op": "unsubscribe",
                "args": [$args]
            }
        """.trimIndent()
        webSocket?.send(payload)
    }

    private fun scheduleReconnect() {
        if (isUserDisconnected) return
        scope.launch {
            _connectionStatus.value = ConnectionStatus.RECONNECTING
            reconnectAttempts++
            val backoffMs = (1000L * (1 shl (reconnectAttempts - 1).coerceAtMost(5))).coerceAtMost(30_000L)
            Log.d(TAG, "Reconnecting in ${backoffMs}ms (attempt $reconnectAttempts)")
            delay(backoffMs)
            initiateConnection()
        }
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                if (_connectionStatus.value == ConnectionStatus.LIVE) {
                    webSocket?.send("ping")
                }
            }
        }
    }

    private fun startStaleChecker() {
        staleCheckJob?.cancel()
        staleCheckJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val lastTick = _lastTickTime.value
                if (lastTick > 0L) {
                    val ageSecs = (System.currentTimeMillis() - lastTick) / 1000L
                    _dataAgeSeconds.value = ageSecs
                    _isDataStale.value = ageSecs > STALE_THRESHOLD_SECONDS
                }
            }
        }
    }

    fun disconnect() {
        isUserDisconnected = true
        pingJob?.cancel()
        staleCheckJob?.cancel()
        webSocket?.close(1000, "User closed")
        webSocket = null
        _connectionStatus.value = ConnectionStatus.OFFLINE
    }
}
