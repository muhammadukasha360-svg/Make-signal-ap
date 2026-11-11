package com.example.signalx.engine.forecast

import com.example.signalx.data.api.PriceFormatter
import com.example.signalx.data.model.BreakoutAnalysis
import com.example.signalx.data.model.Candle
import com.example.signalx.data.model.Direction
import com.example.signalx.data.model.EvidenceItem
import com.example.signalx.data.model.ForecastResult
import com.example.signalx.data.model.MarketType
import com.example.signalx.data.model.MinuteForecastItem
import com.example.signalx.data.model.NextCandleReport
import com.example.signalx.data.model.OrderBook
import com.example.signalx.engine.analysis.MarketAnalysisReport
import com.example.signalx.engine.analysis.MarketRegime
import com.example.signalx.engine.analysis.MarketStructureAnalyzer
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ForecastEngine {

    val HORIZONS = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 30, 45, 60)

    fun generateForecasts(
        candles1m: List<Candle>,
        higherTfCandles: List<Candle>? = null,
        orderBook: OrderBook? = null
    ): List<ForecastResult> {
        if (candles1m.isEmpty()) return emptyList()

        val report1m = MarketStructureAnalyzer.analyze(candles1m, orderBook)
        val reportHTF = if (!higherTfCandles.isNullOrEmpty()) {
            MarketStructureAnalyzer.analyze(higherTfCandles)
        } else report1m

        val currentPrice = report1m.currentPrice
        val baseAtr = report1m.atr.coerceAtLeast(currentPrice * 0.0005)

        // Compile evidence items and net score
        val (evidenceList, netScore, confidence) = evaluateEvidence(report1m, reportHTF)

        val results = mutableListOf<ForecastResult>()

        for (horizon in HORIZONS) {
            // Expected range: ATR_1m * sqrt(horizon) * regime multiplier
            val regimeMultiplier = when (report1m.regime) {
                MarketRegime.HIGH_VOLATILITY -> 1.4
                MarketRegime.TRENDING_BULLISH, MarketRegime.TRENDING_BEARISH -> 1.15
                MarketRegime.RANGING_SIDEWAYS -> 0.85
            }
            val expectedRange = baseAtr * sqrt(horizon.toDouble()) * regimeMultiplier
            val expectedPctMove = if (currentPrice > 0) (expectedRange / currentPrice) * 100.0 else 0.0

            // Horizon-dependent decay of net score
            val horizonFactor = 1.0 / (1.0 + 0.02 * horizon)
            val adjustedScore = netScore * horizonFactor

            // Convert score (-100 to +100) to probabilities using calibrated logistic softmax
            val zUp = (adjustedScore / 30.0).coerceIn(-4.0, 4.0)
            val zDown = (-adjustedScore / 30.0).coerceIn(-4.0, 4.0)
            val zSideways = if (report1m.regime == MarketRegime.RANGING_SIDEWAYS) 0.5 else -0.5

            val expUp = exp(zUp)
            val expDown = exp(zDown)
            val expSide = exp(zSideways)
            val sumExp = expUp + expDown + expSide

            var pUp = ((expUp / sumExp) * 100.0).roundToInt()
            var pDown = ((expDown / sumExp) * 100.0).roundToInt()
            var pSide = 100 - pUp - pDown
            if (pSide < 0) {
                pSide = 5
                pUp -= 3
                pDown -= 2
            }

            // Determine direction
            val direction = if (pSide >= pUp && pSide >= pDown) {
                Direction.SIDEWAYS
            } else if (pUp >= pDown) {
                Direction.UP
            } else {
                Direction.DOWN
            }

            val totalProbNonSide = (pUp + pDown).coerceAtLeast(1).toDouble()
            val upWeight = pUp / totalProbNonSide
            val downWeight = pDown / totalProbNonSide

            val estHigh = currentPrice + expectedRange * (0.5 + 0.5 * upWeight)
            val estLow = currentPrice - expectedRange * (0.5 + 0.5 * downWeight)

            val momentum = adjustedScore.coerceIn(-100.0, 100.0)

            results.add(
                ForecastResult(
                    horizonMinutes = horizon,
                    direction = direction,
                    probUp = pUp.toDouble(),
                    probDown = pDown.toDouble(),
                    probSideways = pSide.toDouble(),
                    estimatedHigh = estHigh,
                    estimatedLow = estLow,
                    expectedPctMove = expectedPctMove,
                    momentumScore = momentum,
                    confidence = confidence,
                    evidence = evidenceList,
                    timestamp = System.currentTimeMillis(),
                    basePrice = currentPrice
                )
            )
        }

        return results
    }

    private fun evaluateEvidence(
        report1m: MarketAnalysisReport,
        reportHTF: MarketAnalysisReport
    ): Triple<List<EvidenceItem>, Double, Double> {
        val evidence = mutableListOf<EvidenceItem>()
        var netScore = 0.0
        var totalWeight = 0.0

        // 1. Trend & Structure
        if (report1m.ema7 > report1m.ema25 && report1m.ema25 > report1m.ema99) {
            evidence.add(EvidenceItem("EMA Stack Bullish", Direction.UP, 15.0, "7 > 25 > 99 EMA aligned upward"))
            netScore += 15.0
        } else if (report1m.ema7 < report1m.ema25 && report1m.ema25 < report1m.ema99) {
            evidence.add(EvidenceItem("EMA Stack Bearish", Direction.DOWN, 15.0, "7 < 25 < 99 EMA aligned downward"))
            netScore -= 15.0
        }
        totalWeight += 15.0

        report1m.lastStructureBreak?.let { sb ->
            if (sb.isBullish) {
                val label = if (sb.isBOS) "BOS Bullish Break" else "CHoCH Bullish Character Change"
                evidence.add(EvidenceItem(label, Direction.UP, 12.0, "Recent structure break above ${sb.breakPrice}"))
                netScore += 12.0
            } else {
                val label = if (sb.isBOS) "BOS Bearish Break" else "CHoCH Bearish Character Change"
                evidence.add(EvidenceItem(label, Direction.DOWN, 12.0, "Recent structure break below ${sb.breakPrice}"))
                netScore -= 12.0
            }
        }
        totalWeight += 12.0

        // 2. Momentum
        if (report1m.rsi14 > 55.0 && report1m.rsiSlope > 0) {
            evidence.add(EvidenceItem("RSI Bullish Momentum", Direction.UP, 10.0, "RSI at ${report1m.rsi14.roundToInt()} rising"))
            netScore += 10.0
        } else if (report1m.rsi14 < 45.0 && report1m.rsiSlope < 0) {
            evidence.add(EvidenceItem("RSI Bearish Momentum", Direction.DOWN, 10.0, "RSI at ${report1m.rsi14.roundToInt()} falling"))
            netScore -= 10.0
        } else {
            evidence.add(EvidenceItem("RSI Neutral", Direction.SIDEWAYS, 5.0, "RSI oscillating near 50"))
        }
        totalWeight += 10.0

        // 3. Candle Patterns
        for (p in report1m.detectedPatterns) {
            if (p.contains("Bullish") || p.contains("Hammer") || p.contains("Morning")) {
                evidence.add(EvidenceItem("Pattern: $p", Direction.UP, 10.0, "Bullish candle formation detected"))
                netScore += 10.0
            } else if (p.contains("Bearish") || p.contains("Shooting") || p.contains("Evening")) {
                evidence.add(EvidenceItem("Pattern: $p", Direction.DOWN, 10.0, "Bearish candle formation detected"))
                netScore -= 10.0
            } else {
                evidence.add(EvidenceItem("Pattern: $p", Direction.SIDEWAYS, 5.0, "Compression / Doji pattern"))
            }
            totalWeight += 10.0
        }

        // 4. Volume & Buying Pressure
        if (report1m.buyersPressurePct >= 60) {
            evidence.add(EvidenceItem("Buyers Dominating", Direction.UP, 12.0, "Buyers at ${report1m.buyersPressurePct}% vs ${report1m.sellersPressurePct}%"))
            netScore += 12.0
        } else if (report1m.sellersPressurePct >= 60) {
            evidence.add(EvidenceItem("Sellers Dominating", Direction.DOWN, 12.0, "Sellers at ${report1m.sellersPressurePct}% vs ${report1m.buyersPressurePct}%"))
            netScore -= 12.0
        }
        totalWeight += 12.0

        // 5. Higher Timeframe Alignment
        if (reportHTF.regime == MarketRegime.TRENDING_BULLISH) {
            evidence.add(EvidenceItem("HTF Trend Bullish", Direction.UP, 15.0, "Higher timeframe in bullish trend"))
            netScore += 15.0
        } else if (reportHTF.regime == MarketRegime.TRENDING_BEARISH) {
            evidence.add(EvidenceItem("HTF Trend Bearish", Direction.DOWN, 15.0, "Higher timeframe in bearish trend"))
            netScore -= 15.0
        }
        totalWeight += 15.0

        // Confidence calculation: agreement of evidence items
        val agreementRatio = (kotlin.math.abs(netScore) / totalWeight.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        val rawConfidence = (45.0 + agreementRatio * 45.0).coerceIn(35.0, 92.0)

        // Penalize confidence if false breakout or trap detected
        val adjustedConfidence = if (report1m.isFalseBreakout || report1m.isLiquidityTrap) {
            rawConfidence * 0.75
        } else rawConfidence

        return Triple(evidence, netScore, adjustedConfidence)
    }

    fun generateNextCandleReport(
        candles1m: List<Candle>,
        higherTfCandles: List<Candle>? = null,
        orderBook: OrderBook? = null,
        symbol: String = "BTCUSDT",
        marketType: MarketType = MarketType.SPOT
    ): NextCandleReport {
        val forecasts = generateForecasts(candles1m, higherTfCandles, orderBook)
        val report = MarketStructureAnalyzer.analyze(candles1m, orderBook, symbol, marketType)
        val breakout = report.breakoutAnalysis ?: MarketStructureAnalyzer.analyzeBreakout(
            candles = candles1m,
            currentPrice = report.currentPrice,
            atr = report.atr,
            range20High = report.range20High,
            range20Low = report.range20Low,
            range200High = report.range200High,
            range200Low = report.range200Low,
            relativeVolume = report.relativeVolume,
            isAtrSqueeze = report.isAtrSqueeze,
            detectedPatterns = report.detectedPatterns,
            rsi14 = report.rsi14,
            buyersPct = report.buyersPressurePct,
            sellersPct = report.sellersPressurePct,
            symbol = symbol,
            marketType = marketType
        )

        // Find best entry minute among minutes 1..5
        var bestMinute = 1
        var bestScore = -1.0
        for (m in 1..5) {
            val f = forecasts.firstOrNull { it.horizonMinutes == m }
            if (f != null) {
                val dirEdge = maxOf(f.probUp, f.probDown) - f.probSideways
                if (dirEdge > bestScore) {
                    bestScore = dirEdge
                    bestMinute = m
                }
            }
        }

        // Build minutes table: 1..10, 15, 30, 45, 60
        val targetMinutes = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 30, 45, 60)
        val table = targetMinutes.map { minute ->
            val f = forecasts.firstOrNull { it.horizonMinutes == minute }
            val dir = f?.direction ?: Direction.SIDEWAYS
            val prob = when (dir) {
                Direction.UP -> f?.probUp?.roundToInt() ?: 40
                Direction.DOWN -> f?.probDown?.roundToInt() ?: 40
                Direction.SIDEWAYS -> f?.probSideways?.roundToInt() ?: 40
            }
            val estHigh = f?.estimatedHigh ?: report.currentPrice
            val estLow = f?.estimatedLow ?: report.currentPrice
            val estHighStr = PriceFormatter.formatPrice(symbol, marketType, estHigh)
            val estLowStr = PriceFormatter.formatPrice(symbol, marketType, estLow)

            val isBest = (minute == bestMinute)
            val isAvoid = (!isBest && (f?.probSideways ?: 30.0) >= 42.0) ||
                    (!isBest && minute in listOf(4, 6, 8) && report.regime == MarketRegime.RANGING_SIDEWAYS)

            val note = when {
                isBest -> "BEST ENTRY: Peak directional momentum & edge"
                isAvoid -> "AVOID: High probability of chop / consolidation"
                minute > 10 -> "Macro Trend Alignment"
                else -> "Progressive move window"
            }

            MinuteForecastItem(
                minute = minute,
                direction = dir,
                probPercent = prob,
                estimatedHigh = estHigh,
                estimatedLow = estLow,
                estimatedHighStr = estHighStr,
                estimatedLowStr = estLowStr,
                isBestEntry = isBest,
                isAvoid = isAvoid,
                note = note
            )
        }

        return NextCandleReport(
            timestamp = System.currentTimeMillis(),
            symbol = symbol,
            marketType = marketType,
            minutesTable = table,
            bestEntryMinute = bestMinute,
            breakout = breakout,
            disclaimer = "This is a statistical probability estimate, not a guaranteed outcome."
        )
    }
}
