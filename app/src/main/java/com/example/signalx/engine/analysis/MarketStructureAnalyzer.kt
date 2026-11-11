package com.example.signalx.engine.analysis

import com.example.signalx.data.api.PriceFormatter
import com.example.signalx.data.model.AltcoinFollowItem
import com.example.signalx.data.model.BreakoutAnalysis
import com.example.signalx.data.model.BtcCorrelationAnalysis
import com.example.signalx.data.model.Candle
import com.example.signalx.data.model.Direction
import com.example.signalx.data.model.MarketType
import com.example.signalx.data.model.OrderBook
import com.example.signalx.engine.indicators.TechnicalIndicators
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class SwingPoint(
    val index: Int,
    val timestamp: Long,
    val price: Double,
    val isHigh: Boolean,
    val label: String // "HH", "HL", "LH", "LL"
)

data class StructureBreak(
    val isBOS: Boolean, // true for BOS, false for CHoCH
    val isBullish: Boolean,
    val breakPrice: Double,
    val candleIndex: Int,
    val timestamp: Long
)

data class FairValueGap(
    val isBullish: Boolean,
    val top: Double,
    val bottom: Double,
    val candleIndex: Int,
    val timestamp: Long,
    val isMitigated: Boolean
)

data class OrderBlock(
    val isBullish: Boolean,
    val top: Double,
    val bottom: Double,
    val candleIndex: Int,
    val timestamp: Long,
    val isMitigated: Boolean
)

data class LiquidityLevel(
    val price: Double,
    val isHigh: Boolean,
    val count: Int,
    val label: String // "EQH", "EQL", "SWEEP"
)

data class VolumeProfile(
    val poc: Double, // Point of Control
    val vah: Double, // Value Area High (70%)
    val valLevel: Double // Value Area Low (70%)
)

enum class MarketRegime {
    TRENDING_BULLISH,
    TRENDING_BEARISH,
    RANGING_SIDEWAYS,
    HIGH_VOLATILITY
}

data class MarketAnalysisReport(
    val currentPrice: Double,
    val atr: Double,
    val rsi14: Double,
    val rsiSlope: Double, // positive or negative
    val ema7: Double,
    val ema25: Double,
    val ema99: Double,
    val adx: Double,
    val relativeVolume: Double,
    val detectedPatterns: List<String>,
    val swingPoints: List<SwingPoint>,
    val lastStructureBreak: StructureBreak?,
    val activeFVGs: List<FairValueGap>,
    val activeOrderBlocks: List<OrderBlock>,
    val liquidityLevels: List<LiquidityLevel>,
    val volumeProfile: VolumeProfile,
    val buyersPressurePct: Int,
    val sellersPressurePct: Int,
    val regime: MarketRegime,
    val keySupport: Double,
    val keyResistance: Double,
    val isFalseBreakout: Boolean,
    val isLiquidityTrap: Boolean,
    val breakoutAnalysis: BreakoutAnalysis? = null,
    val range20High: Double = 0.0,
    val range20Low: Double = 0.0,
    val range200High: Double = 0.0,
    val range200Low: Double = 0.0,
    val isAtrSqueeze: Boolean = false,
    val smcAnalysis: com.example.signalx.data.model.SmcAnalysis = com.example.signalx.data.model.SmcAnalysis()
)

object MarketStructureAnalyzer {

    fun analyze(
        candles: List<Candle>,
        orderBook: OrderBook? = null,
        symbol: String = "BTCUSDT",
        marketType: MarketType = MarketType.SPOT,
        comparisonCandles: List<Candle>? = null,
        comparisonSymbol: String = "BTCUSDT"
    ): MarketAnalysisReport {
        if (candles.isEmpty()) {
            return fallbackEmptyReport()
        }

        val currentPrice = candles.last().close
        val atr = TechnicalIndicators.calculateATR(candles, 14).coerceAtLeast(currentPrice * 0.001)

        val rsiList = TechnicalIndicators.calculateRSI(candles, 14)
        val rsi14 = rsiList.lastOrNull() ?: 50.0
        val prevRsi = if (rsiList.size >= 2) rsiList[rsiList.size - 2] else rsi14
        val rsiSlope = rsi14 - prevRsi

        val ema7List = TechnicalIndicators.calculateEMA(candles, 7)
        val ema25List = TechnicalIndicators.calculateEMA(candles, 25)
        val ema99List = TechnicalIndicators.calculateEMA(candles, 99)

        val ema7 = ema7List.lastOrNull() ?: currentPrice
        val ema25 = ema25List.lastOrNull() ?: currentPrice
        val ema99 = ema99List.lastOrNull() ?: currentPrice

        val adxResult = TechnicalIndicators.calculateADX(candles, 14)
        val adx = adxResult.adx
        val relativeVolume = TechnicalIndicators.calculateRelativeVolume(candles, 20)

        val patterns = detectCandlePatterns(candles)
        val swings = detectSwingPoints(candles)
        val structureBreak = detectStructureBreaks(candles, swings)
        val fvgs = detectFVGs(candles, currentPrice)
        val orderBlocks = detectOrderBlocks(candles, swings, currentPrice)
        val liquidity = detectLiquidityLevels(swings, atr, candles.last())
        val volProfile = calculateVolumeProfile(candles)

        // Buying vs Selling Pressure %
        val (buyersPct, sellersPct) = calculatePressure(candles, orderBook, relativeVolume)

        // Regime detection
        val bb = TechnicalIndicators.calculateBollingerBands(candles, 20)
        val regime = determineRegime(adx, bb?.bandwidth ?: 0.05, ema7, ema25, ema99)

        // Key Support and Resistance
        val recentLows = candles.takeLast(50).map { it.low }
        val recentHighs = candles.takeLast(50).map { it.high }
        val keySupport = recentLows.filter { it < currentPrice }.maxOrNull() ?: (currentPrice - atr * 1.5)
        val keyResistance = recentHighs.filter { it > currentPrice }.minOrNull() ?: (currentPrice + atr * 1.5)

        // Liquidity trap / false breakout
        val isFalseBreakout = detectFalseBreakout(candles, keyResistance, keySupport, relativeVolume)
        val isLiquidityTrap = liquidity.any { it.label == "SWEEP" } && relativeVolume > 1.3

        // 20-candle and 200-candle range analysis
        val last20 = candles.takeLast(20)
        val range20High = last20.maxOfOrNull { it.high } ?: currentPrice
        val range20Low = last20.minOfOrNull { it.low } ?: currentPrice

        val last200 = candles.takeLast(200)
        val range200High = last200.maxOfOrNull { it.high } ?: currentPrice
        val range200Low = last200.minOfOrNull { it.low } ?: currentPrice

        // ATR Squeeze detection
        val isAtrSqueeze = ((bb?.bandwidth ?: 0.05) < 0.035) || (atr < (currentPrice * 0.002))

        val breakoutAnalysis = analyzeBreakout(
            candles = candles,
            currentPrice = currentPrice,
            atr = atr,
            range20High = range20High,
            range20Low = range20Low,
            range200High = range200High,
            range200Low = range200Low,
            relativeVolume = relativeVolume,
            isAtrSqueeze = isAtrSqueeze,
            detectedPatterns = patterns,
            rsi14 = rsi14,
            buyersPct = buyersPct,
            sellersPct = sellersPct,
            symbol = symbol,
            marketType = marketType
        )

        val smcAnalysis = SmartMoneyEngine.analyze(
            candles = candles,
            swings = swings,
            atr = atr,
            currentPrice = currentPrice,
            comparisonCandles = comparisonCandles,
            comparisonSymbol = comparisonSymbol,
            activeSymbol = symbol
        )

        return MarketAnalysisReport(
            currentPrice = currentPrice,
            atr = atr,
            rsi14 = rsi14,
            rsiSlope = rsiSlope,
            ema7 = ema7,
            ema25 = ema25,
            ema99 = ema99,
            adx = adx,
            relativeVolume = relativeVolume,
            detectedPatterns = patterns,
            swingPoints = swings,
            lastStructureBreak = structureBreak,
            activeFVGs = fvgs,
            activeOrderBlocks = orderBlocks,
            liquidityLevels = liquidity,
            volumeProfile = volProfile,
            buyersPressurePct = buyersPct,
            sellersPressurePct = sellersPct,
            regime = regime,
            keySupport = keySupport,
            keyResistance = keyResistance,
            isFalseBreakout = isFalseBreakout,
            isLiquidityTrap = isLiquidityTrap,
            breakoutAnalysis = breakoutAnalysis,
            range20High = range20High,
            range20Low = range20Low,
            range200High = range200High,
            range200Low = range200Low,
            isAtrSqueeze = isAtrSqueeze,
            smcAnalysis = smcAnalysis
        )
    }

    private fun detectCandlePatterns(candles: List<Candle>): List<String> {
        if (candles.size < 3) return emptyList()
        val patterns = mutableListOf<String>()
        val curr = candles.last()
        val prev = candles[candles.size - 2]
        val prev2 = candles[candles.size - 3]

        val body = curr.bodySize
        val range = curr.totalRange.coerceAtLeast(0.00001)
        val upperWick = curr.upperWick
        val lowerWick = curr.lowerWick

        // Doji
        if (body / range < 0.10) {
            patterns.add("Doji (Indecision)")
        }

        // Hammer / Pin Bar
        if (lowerWick >= 2.0 * body && upperWick <= body * 0.5 && prev.close < prev.open) {
            patterns.add("Bullish Hammer")
        }

        // Shooting Star / Inverted Hammer
        if (upperWick >= 2.0 * body && lowerWick <= body * 0.5 && prev.close > prev.open) {
            patterns.add("Shooting Star (Rejection)")
        }

        // Bullish Engulfing
        if (!prev.isBullish && curr.isBullish && curr.open <= prev.close && curr.close >= prev.open) {
            patterns.add("Bullish Engulfing")
        }

        // Bearish Engulfing
        if (prev.isBullish && !curr.isBullish && curr.open >= prev.close && curr.close <= prev.open) {
            patterns.add("Bearish Engulfing")
        }

        // Morning Star
        if (!prev2.isBullish && prev.bodySize / (prev.totalRange.coerceAtLeast(0.0001)) < 0.3 && curr.isBullish && curr.close > (prev2.open + prev2.close) / 2) {
            patterns.add("Morning Star")
        }

        // Evening Star
        if (prev2.isBullish && prev.bodySize / (prev.totalRange.coerceAtLeast(0.0001)) < 0.3 && !curr.isBullish && curr.close < (prev2.open + prev2.close) / 2) {
            patterns.add("Evening Star")
        }

        // Inside Bar
        if (curr.high <= prev.high && curr.low >= prev.low) {
            patterns.add("Inside Bar (Compression)")
        }

        return patterns
    }

    internal fun detectSwingPoints(candles: List<Candle>, lookback: Int = 3): List<SwingPoint> {
        if (candles.size < lookback * 2 + 1) return emptyList()
        val swings = mutableListOf<SwingPoint>()

        var lastHighPrice = 0.0
        var lastLowPrice = Double.MAX_VALUE

        for (i in lookback until (candles.size - lookback)) {
            val c = candles[i]
            var isSwingHigh = true
            var isSwingLow = true

            for (j in (i - lookback)..(i + lookback)) {
                if (j == i) continue
                if (candles[j].high >= c.high) isSwingHigh = false
                if (candles[j].low <= c.low) isSwingLow = false
            }

            if (isSwingHigh) {
                val label = if (c.high > lastHighPrice) "HH" else "LH"
                lastHighPrice = c.high
                swings.add(SwingPoint(i, c.timestamp, c.high, true, label))
            } else if (isSwingLow) {
                val label = if (c.low > lastLowPrice) "HL" else "LL"
                lastLowPrice = c.low
                swings.add(SwingPoint(i, c.timestamp, c.low, false, label))
            }
        }
        return swings
    }

    private fun detectStructureBreaks(candles: List<Candle>, swings: List<SwingPoint>): StructureBreak? {
        if (swings.size < 2 || candles.isEmpty()) return null
        val recentCandle = candles.last()

        val recentHigh = swings.filter { it.isHigh }.lastOrNull()
        val recentLow = swings.filter { !it.isHigh }.lastOrNull()

        if (recentHigh != null && recentCandle.close > recentHigh.price) {
            val isBOS = recentHigh.label == "HH"
            return StructureBreak(isBOS = isBOS, isBullish = true, breakPrice = recentHigh.price, candleIndex = candles.size - 1, timestamp = recentCandle.timestamp)
        }

        if (recentLow != null && recentCandle.close < recentLow.price) {
            val isBOS = recentLow.label == "LL"
            return StructureBreak(isBOS = isBOS, isBullish = false, breakPrice = recentLow.price, candleIndex = candles.size - 1, timestamp = recentCandle.timestamp)
        }

        return null
    }

    private fun detectFVGs(candles: List<Candle>, currentPrice: Double): List<FairValueGap> {
        if (candles.size < 3) return emptyList()
        val fvgs = mutableListOf<FairValueGap>()

        val checkCount = min(candles.size - 1, 80)
        val start = candles.size - checkCount

        for (i in start until (candles.size - 1)) {
            val c1 = candles[i - 1]
            val c3 = candles[i + 1]

            // Bullish FVG
            if (c3.low > c1.high) {
                val isMitigated = currentPrice <= c3.low && currentPrice >= c1.high
                fvgs.add(FairValueGap(isBullish = true, top = c3.low, bottom = c1.high, candleIndex = i, timestamp = candles[i].timestamp, isMitigated = isMitigated))
            }
            // Bearish FVG
            else if (c3.high < c1.low) {
                val isMitigated = currentPrice >= c3.high && currentPrice <= c1.low
                fvgs.add(FairValueGap(isBullish = false, top = c1.low, bottom = c3.high, candleIndex = i, timestamp = candles[i].timestamp, isMitigated = isMitigated))
            }
        }
        return fvgs.takeLast(4)
    }

    private fun detectOrderBlocks(candles: List<Candle>, swings: List<SwingPoint>, currentPrice: Double): List<OrderBlock> {
        val obs = mutableListOf<OrderBlock>()
        if (candles.size < 5) return obs

        val recentSwings = swings.takeLast(4)
        for (s in recentSwings) {
            val idx = s.index
            if (idx in 1 until candles.size) {
                val c = candles[idx]
                val isBullish = !s.isHigh
                val top = c.high
                val bottom = c.low
                val isMitigated = if (isBullish) currentPrice < bottom else currentPrice > top
                obs.add(OrderBlock(isBullish = isBullish, top = top, bottom = bottom, candleIndex = idx, timestamp = c.timestamp, isMitigated = isMitigated))
            }
        }
        return obs
    }

    private fun detectLiquidityLevels(swings: List<SwingPoint>, atr: Double, lastCandle: Candle): List<LiquidityLevel> {
        val levels = mutableListOf<LiquidityLevel>()
        val tolerance = atr * 0.15

        val highs = swings.filter { it.isHigh }.map { it.price }
        val lows = swings.filter { !it.isHigh }.map { it.price }

        // Check Equal Highs
        for (i in highs.indices) {
            for (j in (i + 1) until highs.size) {
                if (abs(highs[i] - highs[j]) <= tolerance) {
                    levels.add(LiquidityLevel((highs[i] + highs[j]) / 2, true, 2, "EQH"))
                    break
                }
            }
        }

        // Check Equal Lows
        for (i in lows.indices) {
            for (j in (i + 1) until lows.size) {
                if (abs(lows[i] - lows[j]) <= tolerance) {
                    levels.add(LiquidityLevel((lows[i] + lows[j]) / 2, false, 2, "EQL"))
                    break
                }
            }
        }

        // Check Liquidity Sweeps on current candle
        for (h in highs) {
            if (lastCandle.high > h && lastCandle.close < h) {
                levels.add(LiquidityLevel(h, true, 1, "SWEEP"))
            }
        }
        for (l in lows) {
            if (lastCandle.low < l && lastCandle.close > l) {
                levels.add(LiquidityLevel(l, false, 1, "SWEEP"))
            }
        }

        return levels.takeLast(5)
    }

    private fun calculateVolumeProfile(candles: List<Candle>): VolumeProfile {
        if (candles.isEmpty()) return VolumeProfile(0.0, 0.0, 0.0)
        val minPrice = candles.minOf { it.low }
        val maxPrice = candles.maxOf { it.high }
        val range = maxPrice - minPrice
        if (range <= 0.00001) return VolumeProfile(candles.last().close, maxPrice, minPrice)

        val bucketsCount = 30
        val bucketSize = range / bucketsCount
        val volumes = DoubleArray(bucketsCount)

        for (c in candles) {
            val typical = (c.high + c.low + c.close) / 3.0
            val bucketIdx = ((typical - minPrice) / bucketSize).toInt().coerceIn(0, bucketsCount - 1)
            volumes[bucketIdx] += c.volume
        }

        var maxVolIdx = 0
        var maxVol = 0.0
        val totalVol = volumes.sum()

        for (i in 0 until bucketsCount) {
            if (volumes[i] > maxVol) {
                maxVol = volumes[i]
                maxVolIdx = i
            }
        }

        val poc = minPrice + (maxVolIdx + 0.5) * bucketSize

        // 70% Value Area
        var currentVol = maxVol
        var upIdx = maxVolIdx
        var downIdx = maxVolIdx
        val targetVol = totalVol * 0.70

        while (currentVol < targetVol && (upIdx < bucketsCount - 1 || downIdx > 0)) {
            val nextUpVol = if (upIdx < bucketsCount - 1) volumes[upIdx + 1] else 0.0
            val nextDownVol = if (downIdx > 0) volumes[downIdx - 1] else 0.0

            if (nextUpVol >= nextDownVol && upIdx < bucketsCount - 1) {
                upIdx++
                currentVol += nextUpVol
            } else if (downIdx > 0) {
                downIdx--
                currentVol += nextDownVol
            } else if (upIdx < bucketsCount - 1) {
                upIdx++
                currentVol += nextUpVol
            } else {
                break
            }
        }

        val vah = minPrice + (upIdx + 1) * bucketSize
        val valLevel = minPrice + downIdx * bucketSize

        return VolumeProfile(poc, vah, valLevel)
    }

    private fun calculatePressure(
        candles: List<Candle>,
        orderBook: OrderBook?,
        relativeVolume: Double
    ): Pair<Int, Int> {
        val curr = candles.last()
        var buyerScore = 50.0

        // Candle body contribution
        val bodyRatio = curr.bodySize / curr.totalRange.coerceAtLeast(0.0001)
        if (curr.isBullish) {
            buyerScore += bodyRatio * 20.0
        } else {
            buyerScore -= bodyRatio * 20.0
        }

        // Wick absorption contribution
        if (curr.lowerWick > curr.upperWick * 1.5) buyerScore += 10.0
        if (curr.upperWick > curr.lowerWick * 1.5) buyerScore -= 10.0

        // Relative volume weight
        if (relativeVolume > 1.2) {
            if (curr.isBullish) buyerScore += 10.0 else buyerScore -= 10.0
        }

        // Orderbook depth imbalance
        if (orderBook != null) {
            buyerScore += orderBook.imbalance * 15.0
        }

        val finalBuyers = buyerScore.coerceIn(10.0, 90.0).toInt()
        val finalSellers = 100 - finalBuyers
        return Pair(finalBuyers, finalSellers)
    }

    private fun determineRegime(
        adx: Double,
        bandwidth: Double,
        ema7: Double,
        ema25: Double,
        ema99: Double
    ): MarketRegime {
        if (bandwidth > 0.08) return MarketRegime.HIGH_VOLATILITY
        if (adx > 25.0) {
            return if (ema7 > ema25 && ema25 > ema99) MarketRegime.TRENDING_BULLISH
            else if (ema7 < ema25 && ema25 < ema99) MarketRegime.TRENDING_BEARISH
            else MarketRegime.RANGING_SIDEWAYS
        }
        return MarketRegime.RANGING_SIDEWAYS
    }

    private fun detectFalseBreakout(
        candles: List<Candle>,
        resistance: Double,
        support: Double,
        rVol: Double
    ): Boolean {
        if (candles.size < 2) return false
        val curr = candles.last()
        val prev = candles[candles.size - 2]

        // Pierced resistance but closed back inside with low volume or rejection
        if (prev.high > resistance && curr.close < resistance && !curr.isBullish) return true
        // Pierced support but closed back inside
        if (prev.low < support && curr.close > support && curr.isBullish) return true

        return false
    }

    private fun fallbackEmptyReport() = MarketAnalysisReport(
        currentPrice = 0.0, atr = 0.0, rsi14 = 50.0, rsiSlope = 0.0, ema7 = 0.0, ema25 = 0.0, ema99 = 0.0,
        adx = 20.0, relativeVolume = 1.0, detectedPatterns = emptyList(), swingPoints = emptyList(),
        lastStructureBreak = null, activeFVGs = emptyList(), activeOrderBlocks = emptyList(),
        liquidityLevels = emptyList(), volumeProfile = VolumeProfile(0.0, 0.0, 0.0),
        buyersPressurePct = 50, sellersPressurePct = 50, regime = MarketRegime.RANGING_SIDEWAYS,
        keySupport = 0.0, keyResistance = 0.0, isFalseBreakout = false, isLiquidityTrap = false,
        breakoutAnalysis = null, range20High = 0.0, range20Low = 0.0, range200High = 0.0, range200Low = 0.0,
        isAtrSqueeze = false
    )

    fun analyzeBreakout(
        candles: List<Candle>,
        currentPrice: Double,
        atr: Double,
        range20High: Double,
        range20Low: Double,
        range200High: Double,
        range200Low: Double,
        relativeVolume: Double,
        isAtrSqueeze: Boolean,
        detectedPatterns: List<String>,
        rsi14: Double,
        buyersPct: Int,
        sellersPct: Int,
        symbol: String = "BTCUSDT",
        marketType: MarketType = MarketType.SPOT
    ): BreakoutAnalysis {
        val reasons = mutableListOf<String>()
        var expectedDir = Direction.SIDEWAYS
        var breakoutLevel = currentPrice
        var invalidationPrice = currentPrice

        val curr = candles.lastOrNull()
        val isNear20High = currentPrice >= range20High - atr * 0.4
        val isNear20Low = currentPrice <= range20Low + atr * 0.4

        if (isAtrSqueeze) {
            reasons.add("ATR Squeeze: Volatility compression awaiting directional release")
        }

        val upperWickRejection = curr != null && curr.upperWick >= 2.0 * curr.bodySize && isNear20High
        val lowerWickRejection = curr != null && curr.lowerWick >= 2.0 * curr.bodySize && isNear20Low

        if (upperWickRejection) {
            reasons.add("Upper wick rejection at 20-candle high ($range20High)")
        }
        if (lowerWickRejection) {
            reasons.add("Lower wick absorption at 20-candle low ($range20Low)")
        }

        if (isNear20High && !upperWickRejection && (rsi14 > 52.0 || buyersPct > 55)) {
            expectedDir = Direction.UP
            breakoutLevel = range20High
            invalidationPrice = currentPrice - atr * 1.5
            reasons.add("Pressing 20-candle resistance with $buyersPct% buying dominance")
            if (relativeVolume >= 1.2) {
                reasons.add("Volume spike confirms upward pressure (rVol: ${String.format(java.util.Locale.US, "%.1f", relativeVolume)})")
            }
        } else if (isNear20Low && !lowerWickRejection && (rsi14 < 48.0 || sellersPct > 55)) {
            expectedDir = Direction.DOWN
            breakoutLevel = range20Low
            invalidationPrice = currentPrice + atr * 1.5
            reasons.add("Pressing 20-candle support with $sellersPct% selling dominance")
            if (relativeVolume >= 1.2) {
                reasons.add("Volume spike confirms downward pressure (rVol: ${String.format(java.util.Locale.US, "%.1f", relativeVolume)})")
            }
        } else if (lowerWickRejection) {
            expectedDir = Direction.UP
            breakoutLevel = currentPrice + atr * 0.8
            invalidationPrice = curr?.low ?: (currentPrice - atr)
            reasons.add("Support bounce and lower wick absorption")
        } else if (upperWickRejection) {
            expectedDir = Direction.DOWN
            breakoutLevel = currentPrice - atr * 0.8
            invalidationPrice = curr?.high ?: (currentPrice + atr)
            reasons.add("Resistance rejection and upper wick supply dump")
        } else {
            expectedDir = Direction.SIDEWAYS
            breakoutLevel = range20High
            invalidationPrice = range20Low
            reasons.add("Ranging inside 20-candle bounds ($range20Low - $range20High)")
        }

        val likelyWindow = when {
            expectedDir == Direction.SIDEWAYS -> "No imminent breakout"
            isAtrSqueeze && relativeVolume > 1.2 -> "Min 1 - 2 (Imminent release)"
            isAtrSqueeze -> "Min 2 - 4 (Squeeze building)"
            relativeVolume > 1.5 -> "Min 1 - 3 (High volume flow)"
            else -> "Min 3 - 6 (Measured pace)"
        }

        val levelStr = PriceFormatter.formatPrice(symbol, marketType, breakoutLevel)
        val invStr = PriceFormatter.formatPrice(symbol, marketType, invalidationPrice)
        val r20HighStr = PriceFormatter.formatPrice(symbol, marketType, range20High)
        val r20LowStr = PriceFormatter.formatPrice(symbol, marketType, range20Low)

        return BreakoutAnalysis(
            expectedDirection = expectedDir,
            breakoutLevel = breakoutLevel,
            breakoutLevelStr = levelStr,
            confirmationCriteria = "Close beyond level + Relative Volume >= 1.2",
            invalidationPrice = invalidationPrice,
            invalidationPriceStr = invStr,
            reasons = reasons,
            likelyMinuteWindow = likelyWindow,
            isAtrSqueeze = isAtrSqueeze,
            rangeHigh = range20High,
            rangeLow = range20Low,
            rangeHighStr = r20HighStr,
            rangeLowStr = r20LowStr
        )
    }

    fun calculateCorrelation(seriesA: List<Double>, seriesB: List<Double>): Double {
        val n = minOf(seriesA.size, seriesB.size)
        if (n < 5) return 0.0
        val a = seriesA.takeLast(n)
        val b = seriesB.takeLast(n)

        val meanA = a.average()
        val meanB = b.average()

        var num = 0.0
        var denA = 0.0
        var denB = 0.0

        for (i in 0 until n) {
            val diffA = a[i] - meanA
            val diffB = b[i] - meanB
            num += diffA * diffB
            denA += diffA * diffA
            denB += diffB * diffB
        }

        val den = kotlin.math.sqrt(denA * denB)
        return if (den > 0.0) (num / den).coerceIn(-1.0, 1.0) else 0.0
    }

    fun calculateLag(seriesA: List<Double>, seriesB: List<Double>): Int {
        val n = minOf(seriesA.size, seriesB.size)
        if (n < 15) return 0

        var bestLag = 0
        var maxCorr = calculateCorrelation(seriesA, seriesB)

        for (lag in 1..3) {
            if (n - lag < 10) break
            val shiftedB = seriesB.dropLast(lag)
            val subA = seriesA.takeLast(shiftedB.size)
            val corr = calculateCorrelation(subA, shiftedB)
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }
        return bestLag
    }

    fun analyzeBtcCorrelation(
        activeSymbol: String,
        activeCandles: List<Candle>,
        btcCandles: List<Candle>,
        altcoinCandlesMap: Map<String, List<Candle>> = emptyMap(),
        activeSignalDirection: Direction = Direction.SIDEWAYS
    ): BtcCorrelationAnalysis {
        val activeCloses = activeCandles.map { it.close }
        val btcCloses = btcCandles.map { it.close }

        val corr = calculateCorrelation(activeCloses, btcCloses)
        val lag = calculateLag(activeCloses, btcCloses)

        val btcTrend = if (btcCandles.size >= 20) {
            val btcEma7 = TechnicalIndicators.calculateEMA(btcCandles, 7).lastOrNull() ?: 0.0
            val btcEma25 = TechnicalIndicators.calculateEMA(btcCandles, 25).lastOrNull() ?: 0.0
            if (btcEma7 > btcEma25) Direction.UP else if (btcEma7 < btcEma25) Direction.DOWN else Direction.SIDEWAYS
        } else Direction.SIDEWAYS

        val btcLast = btcCandles.lastOrNull()
        val btcNextDir = if (btcLast != null && btcLast.close >= btcLast.open) Direction.UP else Direction.DOWN

        val (supportState, isDowngraded) = when {
            activeSymbol.startsWith("BTC") -> Pair("Active coin is BTC", false)
            corr > 0.4 && activeSignalDirection == Direction.UP && (btcTrend == Direction.DOWN || btcNextDir == Direction.DOWN) -> {
                Pair("BTC opposes active coin (BTC trend is bearish)", true)
            }
            corr > 0.4 && activeSignalDirection == Direction.DOWN && (btcTrend == Direction.UP || btcNextDir == Direction.UP) -> {
                Pair("BTC opposes active coin (BTC trend is bullish)", true)
            }
            corr > 0.4 && activeSignalDirection == btcTrend -> {
                Pair("BTC supports active coin (aligned with BTC trend)", false)
            }
            else -> Pair("BTC neutral (corr ${String.format(java.util.Locale.US, "%.2f", corr)})", false)
        }

        val keyAltcoins = listOf("ETHUSDT", "SOLUSDT", "XRPUSDT", "DOGEUSDT", "ADAUSDT", "BNBUSDT", "LINKUSDT")
        val altcoinFollowList = keyAltcoins.map { altSymbol ->
            val altCandles = altcoinCandlesMap[altSymbol]
            val altCloses = altCandles?.map { it.close } ?: emptyList()
            val altCorr = if (altCloses.size >= 10) calculateCorrelation(altCloses, btcCloses) else 0.75
            val altTrend = if (altCorr > 0.6) {
                if (btcTrend == Direction.UP) "FOLLOWS_UP" else "FOLLOWS_DOWN"
            } else "INDEPENDENT"
            AltcoinFollowItem(
                symbol = altSymbol,
                correlation = altCorr,
                trend = altTrend,
                followsBtc = altCorr > 0.6
            )
        }

        return BtcCorrelationAnalysis(
            btcTrend = btcTrend,
            btcNextCandleDirection = btcNextDir,
            correlationWithBtc = corr,
            lagCandles = lag,
            btcSupportState = supportState,
            isDowngradedByBtc = isDowngraded,
            correlatedAltcoins = altcoinFollowList
        )
    }
}
