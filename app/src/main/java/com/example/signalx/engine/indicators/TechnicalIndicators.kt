package com.example.signalx.engine.indicators

import com.example.signalx.data.model.Candle
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class BollingerBands(
    val upper: Double,
    val middle: Double,
    val lower: Double,
    val bandwidth: Double
)

data class MacdResult(
    val macd: Double,
    val signal: Double,
    val histogram: Double
)

data class StochRsiResult(
    val k: Double,
    val d: Double
)

data class AdxResult(
    val adx: Double,
    val plusDI: Double,
    val minusDI: Double
)

object TechnicalIndicators {

    /**
     * Exponential Moving Average (EMA)
     */
    fun calculateEMA(candles: List<Candle>, period: Int): List<Double> {
        if (candles.isEmpty() || period <= 0) return emptyList()
        val result = ArrayList<Double>(candles.size)
        val multiplier = 2.0 / (period + 1)

        var ema = candles.first().close
        result.add(ema)

        for (i in 1 until candles.size) {
            val close = candles[i].close
            ema = (close - ema) * multiplier + ema
            result.add(ema)
        }
        return result
    }

    /**
     * Simple Moving Average (SMA)
     */
    fun calculateSMA(candles: List<Candle>, period: Int): List<Double> {
        if (candles.size < period || period <= 0) return emptyList()
        val result = ArrayList<Double>(candles.size)

        var sum = 0.0
        for (i in 0 until period) {
            sum += candles[i].close
            result.add(sum / (i + 1))
        }

        for (i in period until candles.size) {
            sum += candles[i].close - candles[i - period].close
            result.add(sum / period)
        }
        return result
    }

    /**
     * Bollinger Bands (period = 20, multiplier = 2.0)
     */
    fun calculateBollingerBands(candles: List<Candle>, period: Int = 20, stdMultiplier: Double = 2.0): BollingerBands? {
        if (candles.size < period) return null
        val recent = candles.takeLast(period)
        val middle = recent.map { it.close }.average()

        val variance = recent.map { (it.close - middle) * (it.close - middle) }.average()
        val stdDev = sqrt(variance)

        val upper = middle + stdMultiplier * stdDev
        val lower = middle - stdMultiplier * stdDev
        val bandwidth = if (middle > 0) (upper - lower) / middle else 0.0

        return BollingerBands(upper, middle, lower, bandwidth)
    }

    /**
     * Volume Weighted Average Price (VWAP) - Daily UTC Session Anchored
     */
    fun calculateVWAP(candles: List<Candle>): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val result = ArrayList<Double>(candles.size)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        var cumVol = 0.0
        var cumTypicalVol = 0.0
        var prevDay = -1

        for (c in candles) {
            cal.timeInMillis = c.timestamp
            val currentDay = cal.get(Calendar.DAY_OF_YEAR)

            // Reset VWAP at 00:00 UTC new day
            if (currentDay != prevDay) {
                cumVol = 0.0
                cumTypicalVol = 0.0
                prevDay = currentDay
            }

            val typicalPrice = (c.high + c.low + c.close) / 3.0
            cumTypicalVol += typicalPrice * c.volume
            cumVol += c.volume

            val vwap = if (cumVol > 0) cumTypicalVol / cumVol else c.close
            result.add(vwap)
        }
        return result
    }

    /**
     * Anchored VWAP starting from a specific candle index
     */
    fun calculateAnchoredVWAP(candles: List<Candle>, anchorIndex: Int): List<Double> {
        if (candles.isEmpty() || anchorIndex !in candles.indices) return emptyList()
        val result = ArrayList<Double>(candles.size)

        var cumVol = 0.0
        var cumTypicalVol = 0.0

        for (i in candles.indices) {
            if (i < anchorIndex) {
                result.add(candles[i].close)
            } else {
                val c = candles[i]
                val typicalPrice = (c.high + c.low + c.close) / 3.0
                cumTypicalVol += typicalPrice * c.volume
                cumVol += c.volume
                val vwap = if (cumVol > 0) cumTypicalVol / cumVol else c.close
                result.add(vwap)
            }
        }
        return result
    }

    /**
     * Parabolic SAR (AF step = 0.02, max = 0.2)
     */
    fun calculateParabolicSAR(
        candles: List<Candle>,
        step: Double = 0.02,
        maxAf: Double = 0.2
    ): List<Double> {
        if (candles.size < 2) return candles.map { it.close }
        val sarList = ArrayList<Double>(candles.size)

        var isRising = candles[1].close >= candles[0].close
        var sar = if (isRising) candles[0].low else candles[0].high
        var ep = if (isRising) candles[1].high else candles[1].low
        var af = step

        sarList.add(sar)
        sarList.add(sar)

        for (i in 2 until candles.size) {
            val prevSar = sar
            sar = prevSar + af * (ep - prevSar)

            if (isRising) {
                sar = min(sar, min(candles[i - 1].low, candles[i - 2].low))
                if (candles[i].low < sar) {
                    isRising = false
                    sar = ep
                    ep = candles[i].low
                    af = step
                } else {
                    if (candles[i].high > ep) {
                        ep = candles[i].high
                        af = min(af + step, maxAf)
                    }
                }
            } else {
                sar = max(sar, max(candles[i - 1].high, candles[i - 2].high))
                if (candles[i].high > sar) {
                    isRising = true
                    sar = ep
                    ep = candles[i].high
                    af = step
                } else {
                    if (candles[i].low < ep) {
                        ep = candles[i].low
                        af = min(af + step, maxAf)
                    }
                }
            }
            sarList.add(sar)
        }
        return sarList
    }

    /**
     * Relative Strength Index (RSI) using Wilder's smoothing
     */
    fun calculateRSI(candles: List<Candle>, period: Int = 14): List<Double> {
        if (candles.size <= period || period <= 0) return emptyList()
        val rsiList = ArrayList<Double>(candles.size)

        var gainSum = 0.0
        var lossSum = 0.0

        for (i in 1..period) {
            val change = candles[i].close - candles[i - 1].close
            if (change > 0) gainSum += change else lossSum += -change
            rsiList.add(50.0) // placeholder for warm-up
        }

        var avgGain = gainSum / period
        var avgLoss = lossSum / period

        val firstRsi = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + (avgGain / avgLoss)))
        rsiList.add(firstRsi)

        for (i in (period + 1) until candles.size) {
            val change = candles[i].close - candles[i - 1].close
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0

            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period

            val rsi = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + (avgGain / avgLoss)))
            rsiList.add(rsi.coerceIn(0.0, 100.0))
        }

        return rsiList
    }

    /**
     * MACD (12, 26, 9)
     */
    fun calculateMACD(
        candles: List<Candle>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MacdResult {
        if (candles.size < slowPeriod) return MacdResult(0.0, 0.0, 0.0)
        val fastEma = calculateEMA(candles, fastPeriod)
        val slowEma = calculateEMA(candles, slowPeriod)

        val macdLine = ArrayList<Double>()
        for (i in candles.indices) {
            macdLine.add(fastEma[i] - slowEma[i])
        }

        val multiplier = 2.0 / (signalPeriod + 1)
        var signal = macdLine.first()
        for (i in 1 until macdLine.size) {
            signal = (macdLine[i] - signal) * multiplier + signal
        }

        val lastMacd = macdLine.last()
        val histogram = lastMacd - signal
        return MacdResult(lastMacd, signal, histogram)
    }

    /**
     * Stochastic RSI (14, 14, 3, 3)
     */
    fun calculateStochasticRSI(
        candles: List<Candle>,
        rsiPeriod: Int = 14,
        stochPeriod: Int = 14
    ): StochRsiResult {
        val rsiSeries = calculateRSI(candles, rsiPeriod)
        if (rsiSeries.size < stochPeriod) return StochRsiResult(50.0, 50.0)

        val recentRsi = rsiSeries.takeLast(stochPeriod)
        val minRsi = recentRsi.minOrNull() ?: 0.0
        val maxRsi = recentRsi.maxOrNull() ?: 100.0

        val currentRsi = recentRsi.last()
        val k = if (maxRsi - minRsi > 0.0001) {
            ((currentRsi - minRsi) / (maxRsi - minRsi)) * 100.0
        } else 50.0

        // Approximate %D as a 3-period smooth of recent K values
        val prevK = if (recentRsi.size >= 2) {
            val prevRsi = recentRsi[recentRsi.size - 2]
            if (maxRsi - minRsi > 0.0001) ((prevRsi - minRsi) / (maxRsi - minRsi)) * 100.0 else 50.0
        } else k

        val d = (k + prevK) / 2.0
        return StochRsiResult(k.coerceIn(0.0, 100.0), d.coerceIn(0.0, 100.0))
    }

    /**
     * Average True Range (ATR) with Wilder smoothing
     */
    fun calculateATR(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size <= period) return 0.0
        val trList = ArrayList<Double>()

        for (i in 1 until candles.size) {
            val high = candles[i].high
            val low = candles[i].low
            val prevClose = candles[i - 1].close
            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            trList.add(tr)
        }

        var atr = trList.take(period).average()
        for (i in period until trList.size) {
            atr = (atr * (period - 1) + trList[i]) / period
        }
        return atr
    }

    /**
     * Average Directional Index (ADX) 14 with +DI and -DI
     */
    fun calculateADX(candles: List<Candle>, period: Int = 14): AdxResult {
        if (candles.size <= period * 2) return AdxResult(20.0, 20.0, 20.0)

        var smoothTR = 0.0
        var smoothPlusDM = 0.0
        var smoothMinusDM = 0.0

        for (i in 1..period) {
            val high = candles[i].high
            val low = candles[i].low
            val prevHigh = candles[i - 1].high
            val prevLow = candles[i - 1].low
            val prevClose = candles[i - 1].close

            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            val upMove = high - prevHigh
            val downMove = prevLow - low

            val plusDM = if (upMove > downMove && upMove > 0) upMove else 0.0
            val minusDM = if (downMove > upMove && downMove > 0) downMove else 0.0

            smoothTR += tr
            smoothPlusDM += plusDM
            smoothMinusDM += minusDM
        }

        val dxList = ArrayList<Double>()

        for (i in (period + 1) until candles.size) {
            val high = candles[i].high
            val low = candles[i].low
            val prevHigh = candles[i - 1].high
            val prevLow = candles[i - 1].low
            val prevClose = candles[i - 1].close

            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            val upMove = high - prevHigh
            val downMove = prevLow - low

            val plusDM = if (upMove > downMove && upMove > 0) upMove else 0.0
            val minusDM = if (downMove > upMove && downMove > 0) downMove else 0.0

            smoothTR = smoothTR - (smoothTR / period) + tr
            smoothPlusDM = smoothPlusDM - (smoothPlusDM / period) + plusDM
            smoothMinusDM = smoothMinusDM - (smoothMinusDM / period) + minusDM

            val plusDI = if (smoothTR > 0) (smoothPlusDM / smoothTR) * 100.0 else 0.0
            val minusDI = if (smoothTR > 0) (smoothMinusDM / smoothTR) * 100.0 else 0.0

            val diSum = plusDI + minusDI
            val dx = if (diSum > 0) (abs(plusDI - minusDI) / diSum) * 100.0 else 0.0
            dxList.add(dx)
        }

        val adx = if (dxList.size >= period) dxList.takeLast(period).average() else 20.0
        val finalPlusDI = if (smoothTR > 0) (smoothPlusDM / smoothTR) * 100.0 else 20.0
        val finalMinusDI = if (smoothTR > 0) (smoothMinusDM / smoothTR) * 100.0 else 20.0

        return AdxResult(adx.coerceIn(0.0, 100.0), finalPlusDI, finalMinusDI)
    }

    /**
     * Relative Volume (RVol) = current candle volume / 20-period average volume
     */
    fun calculateRelativeVolume(candles: List<Candle>, period: Int = 20): Double {
        if (candles.size < 2) return 1.0
        val previousCandles = candles.dropLast(1).takeLast(period)
        if (previousCandles.isEmpty()) return 1.0
        val avgVolume = previousCandles.map { it.volume }.average()
        val currentVolume = candles.lastOrNull()?.volume ?: 1.0
        return if (avgVolume > 0) currentVolume / avgVolume else 1.0
    }

    /**
     * Ichimoku Cloud (9, 26, 52)
     */
    fun calculateIchimoku(candles: List<Candle>): com.example.signalx.data.model.ProIndicators {
        if (candles.size < 9) {
            val p = candles.lastOrNull()?.close ?: 0.0
            return com.example.signalx.data.model.ProIndicators(
                tenkanSen = p, kijunSen = p, senkouSpanA = p, senkouSpanB = p, chikouSpan = p
            )
        }

        fun hl2(list: List<Candle>): Double {
            if (list.isEmpty()) return 0.0
            val h = list.maxOf { it.high }
            val l = list.minOf { it.low }
            return (h + l) / 2.0
        }

        val last9 = candles.takeLast(9)
        val tenkan = hl2(last9)

        val last26 = if (candles.size >= 26) candles.takeLast(26) else candles
        val kijun = hl2(last26)

        val spanA = (tenkan + kijun) / 2.0

        val last52 = if (candles.size >= 52) candles.takeLast(52) else candles
        val spanB = hl2(last52)

        val currentClose = candles.lastOrNull()?.close ?: 0.0
        val chikou = currentClose

        val isAboveCloud = currentClose > maxOf(spanA, spanB)
        val isBelowCloud = currentClose < minOf(spanA, spanB)
        val isBullishTk = tenkan > kijun

        // Supertrend (10, 3.0)
        val (stValue, isStBullish) = calculateSupertrend(candles, 10, 3.0)

        // OBV
        val (obvVal, obvSlope, isObvUp) = calculateOBV(candles)

        // MFI
        val mfiVal = calculateMFI(candles, 14)

        // CCI
        val cciVal = calculateCCI(candles, 20)

        return com.example.signalx.data.model.ProIndicators(
            tenkanSen = tenkan,
            kijunSen = kijun,
            senkouSpanA = spanA,
            senkouSpanB = spanB,
            chikouSpan = chikou,
            isPriceAboveCloud = isAboveCloud,
            isPriceBelowCloud = isBelowCloud,
            isBullishTkCross = isBullishTk,
            supertrendValue = stValue,
            isSupertrendBullish = isStBullish,
            obv = obvVal,
            obvSlope = obvSlope,
            isObvRising = isObvUp,
            mfi14 = mfiVal,
            isMfiOverbought = mfiVal >= 80.0,
            isMfiOversold = mfiVal <= 20.0,
            cci20 = cciVal,
            isCciBullish = cciVal > 100.0,
            isCciBearish = cciVal < -100.0
        )
    }

    /**
     * Supertrend (Period = 10, Multiplier = 3.0)
     */
    fun calculateSupertrend(candles: List<Candle>, period: Int = 10, multiplier: Double = 3.0): Pair<Double, Boolean> {
        if (candles.size < period) {
            val p = candles.lastOrNull()?.close ?: 0.0
            return Pair(p, true)
        }

        val atr = calculateATR(candles, period)
        var prevClose = candles.firstOrNull()?.close ?: 0.0
        var isBullish = true
        var trendLine = candles.firstOrNull()?.close ?: 0.0

        for (i in period until candles.size) {
            val c = candles[i]
            val hl2 = (c.high + c.low) / 2.0
            val basicUpper = hl2 + multiplier * atr
            val basicLower = hl2 - multiplier * atr

            if (isBullish) {
                trendLine = maxOf(trendLine, basicLower)
                if (c.close < trendLine) {
                    isBullish = false
                    trendLine = basicUpper
                }
            } else {
                trendLine = minOf(trendLine, basicUpper)
                if (c.close > trendLine) {
                    isBullish = true
                    trendLine = basicLower
                }
            }
            prevClose = c.close
        }

        return Pair(trendLine, isBullish)
    }

    /**
     * OBV (On-Balance Volume) and 10-candle slope
     */
    fun calculateOBV(candles: List<Candle>): Triple<Double, Double, Boolean> {
        if (candles.isEmpty()) return Triple(0.0, 0.0, true)
        var runningObv = 0.0
        val obvHistory = ArrayList<Double>(candles.size)

        for (i in candles.indices) {
            if (i == 0) {
                runningObv = candles[0].volume
            } else {
                val prev = candles[i - 1].close
                val curr = candles[i].close
                if (curr > prev) {
                    runningObv += candles[i].volume
                } else if (curr < prev) {
                    runningObv -= candles[i].volume
                }
            }
            obvHistory.add(runningObv)
        }

        val currentObv = obvHistory.lastOrNull() ?: 0.0
        val slope = if (obvHistory.size >= 10) {
            val past = obvHistory[obvHistory.size - 10]
            currentObv - past
        } else 0.0

        return Triple(currentObv, slope, slope >= 0.0)
    }

    /**
     * MFI (Money Flow Index 14)
     */
    fun calculateMFI(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size <= period) return 50.0

        var posFlow = 0.0
        var negFlow = 0.0

        val recent = candles.takeLast(period + 1)
        for (i in 1 until recent.size) {
            val prev = recent[i - 1]
            val curr = recent[i]
            val prevTp = (prev.high + prev.low + prev.close) / 3.0
            val currTp = (curr.high + curr.low + curr.close) / 3.0
            val rawFlow = currTp * curr.volume

            if (currTp > prevTp) {
                posFlow += rawFlow
            } else if (currTp < prevTp) {
                negFlow += rawFlow
            }
        }

        if (negFlow == 0.0) return 100.0
        val mfr = posFlow / negFlow
        return (100.0 - (100.0 / (1.0 + mfr))).coerceIn(0.0, 100.0)
    }

    /**
     * CCI (Commodity Channel Index 20)
     */
    fun calculateCCI(candles: List<Candle>, period: Int = 20): Double {
        if (candles.size < period) return 0.0

        val recent = candles.takeLast(period)
        val tpList = recent.map { (it.high + it.low + it.close) / 3.0 }
        val smaTp = tpList.average()

        val meanDev = tpList.map { kotlin.math.abs(it - smaTp) }.average()
        val currentTp = tpList.lastOrNull() ?: smaTp

        return if (meanDev > 0.0) {
            ((currentTp - smaTp) / (0.015 * meanDev)).coerceIn(-300.0, 300.0)
        } else 0.0
    }
}
