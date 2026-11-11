package com.example.signalx.engine.decision

import com.example.signalx.data.api.PriceFormatter
import com.example.signalx.data.model.Direction
import com.example.signalx.data.model.ForecastResult
import com.example.signalx.data.model.MarketType
import com.example.signalx.data.model.SignalType
import com.example.signalx.data.model.TradingDecision
import com.example.signalx.engine.analysis.MarketAnalysisReport
import com.example.signalx.engine.analysis.MarketRegime
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.sqrt

data class DecisionLevels(
    val entryMin: Double,
    val entryMax: Double,
    val stopLoss: Double,
    val tp1: Double,
    val tp2: Double,
    val tp3: Double,
    val invPrice: Double
)

object TradingDecisionEngine {

    fun generateDecision(
        report: MarketAnalysisReport,
        forecast1m: ForecastResult?,
        marketType: MarketType,
        symbol: String = "BTCUSDT",
        isDataStale: Boolean = false,
        confidenceThreshold: Double = 55.0,
        paperBalance: Double = 10_000.0,
        riskPctPerTrade: Double = 2.0,
        historicalWinRate: Double? = null,
        historicalSampleCount: Int = 0,
        btcCorrelation: com.example.signalx.data.model.BtcCorrelationAnalysis? = null
    ): TradingDecision {
        val currentPrice = report.currentPrice
        val atr = report.atr.coerceAtLeast(currentPrice * 0.001)

        val reasonsFor = mutableListOf<String>()
        val reasonsAgainst = mutableListOf<String>()

        // 6 Confirmation Groups
        var bullGroups = 0
        var bearGroups = 0
        var hasHtfConflict = false

        // Group 1: Trend & Structure
        if (report.ema7 > report.ema25 && report.ema25 > report.ema99) {
            bullGroups++
            reasonsFor.add("Trend: Bullish EMA stack (7 > 25 > 99)")
        } else if (report.ema7 < report.ema25 && report.ema25 < report.ema99) {
            bearGroups++
            reasonsFor.add("Trend: Bearish EMA stack (7 < 25 < 99)")
        } else {
            reasonsAgainst.add("Trend: Mixed EMAs, ranging structure")
        }

        // Group 2: Momentum
        if (report.rsi14 in 50.0..72.0 && report.rsiSlope > 0) {
            bullGroups++
            reasonsFor.add("Momentum: RSI rising (${report.rsi14.toInt()})")
        } else if (report.rsi14 in 28.0..50.0 && report.rsiSlope < 0) {
            bearGroups++
            reasonsFor.add("Momentum: RSI falling (${report.rsi14.toInt()})")
        } else if (report.rsi14 > 75.0) {
            reasonsAgainst.add("Momentum: RSI overbought (${report.rsi14.toInt()})")
            hasHtfConflict = true
        } else if (report.rsi14 < 25.0) {
            reasonsAgainst.add("Momentum: RSI oversold (${report.rsi14.toInt()})")
            hasHtfConflict = true
        }

        // Group 3: Volume & Pressure
        if (report.buyersPressurePct >= 60) {
            bullGroups++
            reasonsFor.add("Volume: Buyers dominating (${report.buyersPressurePct}%)")
        } else if (report.sellersPressurePct >= 60) {
            bearGroups++
            reasonsFor.add("Volume: Sellers dominating (${report.sellersPressurePct}%)")
        } else {
            reasonsAgainst.add("Volume: Neutral pressure (${report.buyersPressurePct}% vs ${report.sellersPressurePct}%)")
        }

        // Group 4: Price Action & Structure Breaks
        report.lastStructureBreak?.let { sb ->
            if (sb.isBullish) {
                bullGroups++
                val breakFormatted = PriceFormatter.formatPrice(symbol, marketType, sb.breakPrice)
                reasonsFor.add("Structure: Bullish ${if (sb.isBOS) "BOS" else "CHoCH"} break above $breakFormatted")
            } else {
                bearGroups++
                val breakFormatted = PriceFormatter.formatPrice(symbol, marketType, sb.breakPrice)
                reasonsFor.add("Structure: Bearish ${if (sb.isBOS) "BOS" else "CHoCH"} break below $breakFormatted")
            }
        }

        // Group 5: Higher Timeframe Alignment
        if (report.regime == MarketRegime.TRENDING_BULLISH) {
            bullGroups++
            reasonsFor.add("Regime: Higher-timeframe Bullish Trend")
        } else if (report.regime == MarketRegime.TRENDING_BEARISH) {
            bearGroups++
            reasonsFor.add("Regime: Higher-timeframe Bearish Trend")
        } else {
            reasonsAgainst.add("Regime: Market in ranging consolidation")
        }

        // Group 6: AI Forecast & Probability Confluence
        if (forecast1m?.direction == Direction.UP && forecast1m.probUp >= 50.0) {
            bullGroups++
            reasonsFor.add("Forecast: Next candle ${forecast1m.probUp.toInt()}% UP probability")
        } else if (forecast1m?.direction == Direction.DOWN && forecast1m.probDown >= 50.0) {
            bearGroups++
            reasonsFor.add("Forecast: Next candle ${forecast1m.probDown.toInt()}% DOWN probability")
        }

        val rawConfidence = forecast1m?.confidence ?: 50.0

        // Strict WAIT Conditions per Section 3.3 and 8.4
        var waitReason: String? = null
        if (isDataStale) {
            waitReason = "Data feed is STALE (>15s without update). Signals paused for safety."
        } else if (report.isLiquidityTrap) {
            waitReason = "Liquidity trap / sweep detected. High risk of stop-hunt."
        } else if (report.isFalseBreakout) {
            waitReason = "False breakout detected. Price rejected at key level."
        } else if (report.regime == MarketRegime.RANGING_SIDEWAYS && bullGroups < 4 && bearGroups < 4) {
            waitReason = "Market in ranging / sideways regime without clear edge."
        } else if (rawConfidence < confidenceThreshold) {
            waitReason = "Confidence (${rawConfidence.toInt()}%) below required threshold (${confidenceThreshold.toInt()}%)."
        } else if (abs(bullGroups - bearGroups) < 2) {
            waitReason = "Conflicting evidence: Bullish ($bullGroups) vs Bearish ($bearGroups)."
        }

        // Determine Signal Candidate
        val isBullishCandidate = bullGroups >= 4 && bullGroups > bearGroups && waitReason == null
        val isBearishCandidate = bearGroups >= 4 && bearGroups > bullGroups && waitReason == null

        // Calculate provisional levels
        val provisionalLevels = if (isBullishCandidate) {
            val entryL = currentPrice - atr * 0.2
            val entryH = currentPrice + atr * 0.1
            val sl = (report.keySupport.coerceAtMost(currentPrice - atr * 1.5))
            val risk = (currentPrice - sl).coerceAtLeast(atr)
            val t1 = currentPrice + risk * 1.5
            val t2 = currentPrice + risk * 2.5
            val t3 = currentPrice + risk * 4.0
            val inv = sl
            DecisionLevels(entryL, entryH, sl, t1, t2, t3, inv)
        } else if (isBearishCandidate) {
            val entryL = currentPrice - atr * 0.1
            val entryH = currentPrice + atr * 0.2
            val sl = (report.keyResistance.coerceAtLeast(currentPrice + atr * 1.5))
            val risk = (sl - currentPrice).coerceAtLeast(atr)
            val t1 = currentPrice - risk * 1.5
            val t2 = currentPrice - risk * 2.5
            val t3 = currentPrice - risk * 4.0
            val inv = sl
            DecisionLevels(entryL, entryH, sl, t1, t2, t3, inv)
        } else {
            DecisionLevels(currentPrice, currentPrice, currentPrice - atr, currentPrice + atr * 1.5, currentPrice + atr * 2.5, currentPrice + atr * 4.0, currentPrice - atr)
        }

        // Round calculated prices to tick size precision with RoundingMode.HALF_UP
        val entryMinBd = PriceFormatter.roundToPrecision(symbol, marketType, provisionalLevels.entryMin)
        val entryMaxBd = PriceFormatter.roundToPrecision(symbol, marketType, provisionalLevels.entryMax)
        val stopLossBd = PriceFormatter.roundToPrecision(symbol, marketType, provisionalLevels.stopLoss)
        val tp1Bd = PriceFormatter.roundToPrecision(symbol, marketType, provisionalLevels.tp1)
        val tp2Bd = PriceFormatter.roundToPrecision(symbol, marketType, provisionalLevels.tp2)
        val tp3Bd = PriceFormatter.roundToPrecision(symbol, marketType, provisionalLevels.tp3)
        val invBd = PriceFormatter.roundToPrecision(symbol, marketType, provisionalLevels.invPrice)

        val entryMin = entryMinBd.toDouble()
        val entryMax = entryMaxBd.toDouble()
        val stopLoss = stopLossBd.toDouble()
        val tp1 = tp1Bd.toDouble()
        val tp2 = tp2Bd.toDouble()
        val tp3 = tp3Bd.toDouble()
        val invPrice = invBd.toDouble()

        val riskDist = abs(currentPrice - stopLoss).coerceAtLeast(0.00000001)
        val rr1 = abs(tp1 - currentPrice) / riskDist
        val rr2 = abs(tp2 - currentPrice) / riskDist
        val rr3 = abs(tp3 - currentPrice) / riskDist

        if (rr1 < 1.5 && waitReason == null && (isBullishCandidate || isBearishCandidate)) {
            waitReason = "Risk/Reward ratio (1:${String.format("%.1f", rr1)}) below required 1.5 minimum."
        }

        // Downgrade if BTC opposes altcoin signal
        if (btcCorrelation != null) {
            if (btcCorrelation.isDowngradedByBtc) {
                reasonsAgainst.add("BTC opposes this signal (${btcCorrelation.btcSupportState})")
                if (waitReason == null && (isBullishCandidate || isBearishCandidate)) {
                    waitReason = "Downgraded to WAIT: ${btcCorrelation.btcSupportState}"
                }
            } else if (btcCorrelation.btcSupportState.contains("supports")) {
                reasonsFor.add(btcCorrelation.btcSupportState)
            }
        }

        // Final Signal determination
        val isStrongBull = bullGroups >= 5 && report.relativeVolume >= 1.2 && rr1 >= 2.0 && !hasHtfConflict && (btcCorrelation?.isDowngradedByBtc != true)
        val isStrongBear = bearGroups >= 5 && report.relativeVolume >= 1.2 && rr1 >= 2.0 && !hasHtfConflict && (btcCorrelation?.isDowngradedByBtc != true)

        val signal = when {
            waitReason != null -> SignalType.WAIT
            isStrongBull -> SignalType.STRONG_LONG
            isBullishCandidate -> SignalType.LONG
            isStrongBear -> SignalType.STRONG_SHORT
            isBearishCandidate -> SignalType.SHORT
            else -> SignalType.WAIT
        }

        // Risk-based position sizing in USDT
        val dollarRisk = paperBalance * (riskPctPerTrade / 100.0)
        val positionSize = if (riskDist > 0 && currentPrice > 0) {
            (dollarRisk / (riskDist / currentPrice)).coerceIn(10.0, paperBalance * 5.0)
        } else 0.0

        // Wilson 95% Confidence Interval Calculation
        val (wilsonLower, wilsonUpper) = if (historicalSampleCount > 0 && historicalWinRate != null) {
            calculateWilsonInterval(historicalWinRate / 100.0, historicalSampleCount)
        } else Pair(0.0, 0.0)

        return TradingDecision(
            signal = signal,
            entryMin = entryMin,
            entryMax = entryMax,
            stopLoss = stopLoss,
            takeProfit1 = tp1,
            takeProfit2 = tp2,
            takeProfit3 = tp3,
            riskReward1 = rr1,
            riskReward2 = rr2,
            riskReward3 = rr3,
            invalidationPrice = invPrice,
            confidence = rawConfidence,
            reasonsFor = reasonsFor,
            reasonsAgainst = reasonsAgainst,
            waitReason = if (signal == SignalType.WAIT) (waitReason ?: "Insufficient confirmations (need >= 4 groups)") else null,
            positionSizeRecommended = positionSize,
            entryMinStr = entryMinBd.toPlainString(),
            entryMaxStr = entryMaxBd.toPlainString(),
            stopLossStr = stopLossBd.toPlainString(),
            takeProfit1Str = tp1Bd.toPlainString(),
            takeProfit2Str = tp2Bd.toPlainString(),
            takeProfit3Str = tp3Bd.toPlainString(),
            invalidationPriceStr = invBd.toPlainString(),
            historicalWinRate = historicalWinRate,
            historicalSampleCount = historicalSampleCount,
            wilsonIntervalLower = wilsonLower * 100.0,
            wilsonIntervalUpper = wilsonUpper * 100.0
        )
    }

    /**
     * Pro Confluence 6-Group Scoring Engine:
     * Groups: Trend, Momentum, Order Flow, Key Levels, Futures/Context, Multi-Timeframe.
     * Rules: LONG/SHORT need >= 4 agreeing groups; STRONG needs >= 5; else WAIT.
     */
    fun generateConfluenceReport(
        report: MarketAnalysisReport,
        forecast1m: ForecastResult?,
        marketType: MarketType,
        symbol: String = "BTCUSDT",
        proIndicators: com.example.signalx.data.model.ProIndicators? = null,
        cvdData: com.example.signalx.data.model.CvdData? = null,
        orderBookImbalance: com.example.signalx.data.model.OrderBookImbalanceData? = null,
        marketContext: com.example.signalx.data.model.MarketContextInfo? = null,
        btcCorrelation: com.example.signalx.data.model.BtcCorrelationAnalysis? = null,
        dataAgeSeconds: Long = 0L,
        historicalSampleCount: Int = 0
    ): com.example.signalx.data.model.ConfluenceReport {
        val currentPrice = report.currentPrice
        val atr = report.atr.coerceAtLeast(currentPrice * 0.001)

        val groups = mutableListOf<com.example.signalx.data.model.ConfluenceGroup>()
        val reasonsFor = mutableListOf<String>()
        val reasonsAgainst = mutableListOf<String>()

        // 1. Trend Group (EMA 7/25/99, Ichimoku Cloud, Supertrend)
        var trendScore = 0
        var trendBullishCount = 0
        var trendBearishCount = 0

        if (report.ema7 > report.ema25 && report.ema25 > report.ema99) {
            trendScore += 4
            trendBullishCount++
            reasonsFor.add("Trend: EMA stack bullish (7 > 25 > 99)")
        } else if (report.ema7 < report.ema25 && report.ema25 < report.ema99) {
            trendScore += 4
            trendBearishCount++
            reasonsAgainst.add("Trend: EMA stack bearish (7 < 25 < 99)")
        }

        proIndicators?.let { pro ->
            if (pro.isPriceAboveCloud && pro.isBullishTkCross) {
                trendScore += 3
                trendBullishCount++
                reasonsFor.add("Trend: Ichimoku bullish (price above Kumo & Tenkan > Kijun)")
            } else if (pro.isPriceBelowCloud && !pro.isBullishTkCross) {
                trendScore += 3
                trendBearishCount++
                reasonsAgainst.add("Trend: Ichimoku bearish (price below Kumo & Tenkan < Kijun)")
            }

            if (pro.isSupertrendBullish) {
                trendScore += 3
                trendBullishCount++
                reasonsFor.add("Trend: Supertrend bullish (green line support)")
            } else {
                trendScore += 3
                trendBearishCount++
                reasonsAgainst.add("Trend: Supertrend bearish (red line resistance)")
            }
        }
        val trendDir: Boolean? = when {
            trendBullishCount > trendBearishCount -> true
            trendBearishCount > trendBullishCount -> false
            else -> null
        }
        groups.add(
            com.example.signalx.data.model.ConfluenceGroup(
                name = "Trend",
                isBullish = trendDir,
                score = trendScore.coerceIn(0, 10),
                summary = if (trendDir == true) "Bullish EMA stack & Supertrend" else if (trendDir == false) "Bearish EMA stack & Supertrend" else "Ranging / Mixed Trend"
            )
        )

        // 2. Momentum Group (RSI 14, MACD, MFI, CCI)
        var momScore = 0
        var momBullishCount = 0
        var momBearishCount = 0

        if (report.rsi14 in 50.0..72.0 && report.rsiSlope > 0) {
            momScore += 3
            momBullishCount++
            reasonsFor.add("Momentum: RSI rising (${report.rsi14.toInt()})")
        } else if (report.rsi14 in 28.0..50.0 && report.rsiSlope < 0) {
            momScore += 3
            momBearishCount++
            reasonsAgainst.add("Momentum: RSI falling (${report.rsi14.toInt()})")
        }

        proIndicators?.let { pro ->
            if (pro.mfi14 > 50.0 && !pro.isMfiOverbought) {
                momScore += 3
                momBullishCount++
                reasonsFor.add("Momentum: MFI positive money flow (${pro.mfi14.toInt()})")
            } else if (pro.mfi14 < 50.0 && !pro.isMfiOversold) {
                momScore += 3
                momBearishCount++
                reasonsAgainst.add("Momentum: MFI negative money outflow (${pro.mfi14.toInt()})")
            }

            if (pro.isCciBullish) {
                momScore += 4
                momBullishCount++
                reasonsFor.add("Momentum: CCI strong bullish expansion (> +100)")
            } else if (pro.isCciBearish) {
                momScore += 4
                momBearishCount++
                reasonsAgainst.add("Momentum: CCI strong bearish expansion (< -100)")
            }
        }
        val momDir: Boolean? = when {
            momBullishCount > momBearishCount -> true
            momBearishCount > momBullishCount -> false
            else -> null
        }
        groups.add(
            com.example.signalx.data.model.ConfluenceGroup(
                name = "Momentum",
                isBullish = momDir,
                score = momScore.coerceIn(0, 10),
                summary = if (momDir == true) "RSI & MFI expanding upward" else if (momDir == false) "RSI & MFI contracting downward" else "Neutral Momentum"
            )
        )

        // 3. Order Flow Group (CVD, Order Book Imbalance, Buyers vs Sellers Pressure)
        var ofScore = 0
        var ofBullishCount = 0
        var ofBearishCount = 0

        cvdData?.let { cvd ->
            if (cvd.cvdTrend == Direction.UP) {
                ofScore += 4
                ofBullishCount++
                reasonsFor.add("Order Flow: CVD rising (net aggressive buying)")
            } else if (cvd.cvdTrend == Direction.DOWN) {
                ofScore += 4
                ofBearishCount++
                reasonsAgainst.add("Order Flow: CVD falling (net aggressive selling)")
            }
        }

        orderBookImbalance?.let { imb ->
            if (imb.imbalancePct > 15.0) {
                ofScore += 3
                ofBullishCount++
                reasonsFor.add("Order Flow: Bid dominance (+${String.format(java.util.Locale.US, "%.1f", imb.imbalancePct)}%)")
            } else if (imb.imbalancePct < -15.0) {
                ofScore += 3
                ofBearishCount++
                reasonsAgainst.add("Order Flow: Ask dominance (${String.format(java.util.Locale.US, "%.1f", imb.imbalancePct)}%)")
            }
            if (imb.isAbsorptionDetected) {
                reasonsFor.add("Order Flow: ${imb.absorptionDescription}")
            }
        }

        if (report.buyersPressurePct >= 55) {
            ofScore += 3
            ofBullishCount++
            reasonsFor.add("Order Flow: ${report.buyersPressurePct}% buying pressure")
        } else if (report.sellersPressurePct >= 55) {
            ofScore += 3
            ofBearishCount++
            reasonsAgainst.add("Order Flow: ${report.sellersPressurePct}% selling pressure")
        }

        val ofDir: Boolean? = when {
            ofBullishCount > ofBearishCount -> true
            ofBearishCount > ofBullishCount -> false
            else -> null
        }
        groups.add(
            com.example.signalx.data.model.ConfluenceGroup(
                name = "Order Flow",
                isBullish = ofDir,
                score = ofScore.coerceIn(0, 10),
                summary = if (ofDir == true) "CVD & Bid depth dominating" else if (ofDir == false) "CVD & Ask depth dominating" else "Balanced delta & book depth"
            )
        )

        // 4. Key Levels Group (POC, VAH, VAL, Structure Breaks, Liquidity Sweeps)
        var klScore = 0
        var klBullishCount = 0
        var klBearishCount = 0

        val vp = report.volumeProfile
        if (currentPrice > vp.poc && currentPrice <= vp.vah) {
            klScore += 3
            klBullishCount++
            reasonsFor.add("Key Levels: Price trading above Volume Profile POC")
        } else if (currentPrice < vp.poc && currentPrice >= vp.valLevel) {
            klScore += 3
            klBearishCount++
            reasonsAgainst.add("Key Levels: Price trading below Volume Profile POC")
        }

        report.lastStructureBreak?.let { sb ->
            if (sb.isBullish) {
                klScore += 4
                klBullishCount++
                val bFmt = PriceFormatter.formatPrice(symbol, marketType, sb.breakPrice)
                reasonsFor.add("Key Levels: Bullish ${if (sb.isBOS) "BOS" else "CHoCH"} break above $bFmt")
            } else {
                klScore += 4
                klBearishCount++
                val bFmt = PriceFormatter.formatPrice(symbol, marketType, sb.breakPrice)
                reasonsAgainst.add("Key Levels: Bearish ${if (sb.isBOS) "BOS" else "CHoCH"} break below $bFmt")
            }
        }

        if (report.isLiquidityTrap) {
            reasonsAgainst.add("Key Levels: Liquidity trap detected near key level")
        }

        val klDir: Boolean? = when {
            klBullishCount > klBearishCount -> true
            klBearishCount > klBullishCount -> false
            else -> null
        }
        groups.add(
            com.example.signalx.data.model.ConfluenceGroup(
                name = "Key Levels",
                isBullish = klDir,
                score = klScore.coerceIn(0, 10),
                summary = if (klDir == true) "Above POC with bullish structure break" else if (klDir == false) "Below POC with bearish structure break" else "Consolidating near value area"
            )
        )

        // 5. Futures & Context Group (Funding, Open Interest, Fear & Greed, BTC correlation)
        var fcScore = 0
        var fcBullishCount = 0
        var fcBearishCount = 0

        marketContext?.let { ctx ->
            if (ctx.fearAndGreedValue in 55..75) {
                fcScore += 3
                fcBullishCount++
                reasonsFor.add("Context: Healthy market sentiment (${ctx.fearAndGreedClassification} ${ctx.fearAndGreedValue})")
            } else if (ctx.fearAndGreedValue < 30) {
                fcScore += 3
                fcBullishCount++
                reasonsFor.add("Context: Contrarian accumulation zone (Extreme Fear ${ctx.fearAndGreedValue})")
            } else if (ctx.fearAndGreedValue > 80) {
                fcScore += 3
                fcBearishCount++
                reasonsAgainst.add("Context: Overheated sentiment risk (${ctx.fearAndGreedClassification} ${ctx.fearAndGreedValue})")
            }
        }

        btcCorrelation?.let { btc ->
            if (btc.isDowngradedByBtc) {
                fcScore += 4
                fcBearishCount++
                reasonsAgainst.add("Context: BTC opposes active symbol (${btc.btcSupportState})")
            } else if (btc.btcSupportState.contains("supports")) {
                fcScore += 4
                fcBullishCount++
                reasonsFor.add("Context: BTC supports active symbol")
            }
        }

        val fcDir: Boolean? = when {
            fcBullishCount > fcBearishCount -> true
            fcBearishCount > fcBullishCount -> false
            else -> null
        }
        groups.add(
            com.example.signalx.data.model.ConfluenceGroup(
                name = "Futures & Context",
                isBullish = fcDir,
                score = fcScore.coerceIn(0, 10),
                summary = if (fcDir == true) "Favorable sentiment & BTC support" else if (fcDir == false) "Conflicting BTC trend or overheated market" else "Neutral Market Context"
            )
        )

        // 6. Multi-Timeframe (MTF) Group (Regime & Next Candle Forecast)
        var mtfScore = 0
        var mtfBullishCount = 0
        var mtfBearishCount = 0

        if (report.regime == MarketRegime.TRENDING_BULLISH) {
            mtfScore += 5
            mtfBullishCount++
            reasonsFor.add("MTF: Higher-timeframe Bullish Trend")
        } else if (report.regime == MarketRegime.TRENDING_BEARISH) {
            mtfScore += 5
            mtfBearishCount++
            reasonsAgainst.add("MTF: Higher-timeframe Bearish Trend")
        }

        if (forecast1m?.direction == Direction.UP && forecast1m.probUp >= 52.0) {
            mtfScore += 5
            mtfBullishCount++
            reasonsFor.add("MTF: Next candle ${forecast1m.probUp.toInt()}% UP forecast")
        } else if (forecast1m?.direction == Direction.DOWN && forecast1m.probDown >= 52.0) {
            mtfScore += 5
            mtfBearishCount++
            reasonsAgainst.add("MTF: Next candle ${forecast1m.probDown.toInt()}% DOWN forecast")
        }

        val mtfDir: Boolean? = when {
            mtfBullishCount > mtfBearishCount -> true
            mtfBearishCount > mtfBullishCount -> false
            else -> null
        }
        groups.add(
            com.example.signalx.data.model.ConfluenceGroup(
                name = "Multi-Timeframe",
                isBullish = mtfDir,
                score = mtfScore.coerceIn(0, 10),
                summary = if (mtfDir == true) "Aligned across HTF and 1m horizons" else if (mtfDir == false) "Bearish alignment across HTF and 1m" else "Timeframes in conflict"
            )
        )

        val agreeingLong = groups.count { it.isBullish == true }
        val agreeingShort = groups.count { it.isBullish == false }

        val finalSignal = when {
            btcCorrelation?.isDowngradedByBtc == true -> SignalType.WAIT
            agreeingLong >= 5 -> SignalType.STRONG_LONG
            agreeingLong >= 4 -> SignalType.LONG
            agreeingShort >= 5 -> SignalType.STRONG_SHORT
            agreeingShort >= 4 -> SignalType.SHORT
            else -> SignalType.WAIT
        }

        val bullishPct = (agreeingLong * 16.6).toInt().coerceIn(10, 95)
        val bearishPct = (agreeingShort * 16.6).toInt().coerceIn(10, 95)

        val rawConfidence = ((maxOf(agreeingLong, agreeingShort).toDouble() / 6.0) * 100.0).toInt().coerceIn(35, 95)
        val confLabel = if (historicalSampleCount < 100) {
            "$rawConfidence% (uncalibrated, n=$historicalSampleCount)"
        } else {
            "$rawConfidence% (Wilson 95% CI)"
        }

        val missingReason = when {
            btcCorrelation?.isDowngradedByBtc == true -> "Signal downgraded to WAIT because BTC opposes active coin direction."
            finalSignal == SignalType.WAIT -> "Insufficient confluence: need at least 4 agreeing groups (current: $agreeingLong Long, $agreeingShort Short)."
            else -> "Strong multi-group confirmation achieved ($agreeingLong groups agreeing)."
        }

        val entryZoneStr = if (finalSignal == SignalType.LONG || finalSignal == SignalType.STRONG_LONG) {
            "${PriceFormatter.formatPrice(symbol, marketType, currentPrice - atr * 0.2)} - ${PriceFormatter.formatPrice(symbol, marketType, currentPrice + atr * 0.1)}"
        } else if (finalSignal == SignalType.SHORT || finalSignal == SignalType.STRONG_SHORT) {
            "${PriceFormatter.formatPrice(symbol, marketType, currentPrice - atr * 0.1)} - ${PriceFormatter.formatPrice(symbol, marketType, currentPrice + atr * 0.2)}"
        } else {
            PriceFormatter.formatPrice(symbol, marketType, currentPrice)
        }

        val slPrice = if (finalSignal == SignalType.LONG || finalSignal == SignalType.STRONG_LONG) {
            currentPrice - atr * 1.5
        } else if (finalSignal == SignalType.SHORT || finalSignal == SignalType.STRONG_SHORT) {
            currentPrice + atr * 1.5
        } else currentPrice - atr

        val tp1Price = if (finalSignal == SignalType.LONG || finalSignal == SignalType.STRONG_LONG) {
            currentPrice + atr * 1.5
        } else currentPrice - atr * 1.5

        val tp2Price = if (finalSignal == SignalType.LONG || finalSignal == SignalType.STRONG_LONG) {
            currentPrice + atr * 2.5
        } else currentPrice - atr * 2.5

        val tp3Price = if (finalSignal == SignalType.LONG || finalSignal == SignalType.STRONG_LONG) {
            currentPrice + atr * 4.0
        } else currentPrice - atr * 4.0

        val rr = if (abs(currentPrice - slPrice) > 0) abs(tp1Price - currentPrice) / abs(currentPrice - slPrice) else 1.5

        val liquidityTargets = listOf(
            PriceFormatter.formatPrice(symbol, marketType, tp1Price),
            PriceFormatter.formatPrice(symbol, marketType, tp2Price),
            PriceFormatter.formatPrice(symbol, marketType, tp3Price)
        )

        val missingDataWarnings = listOf(
            "Whale/on-chain, ETF, news, liquidation heatmaps, Bookmap/ATAS/CoinGlass/CryptoQuant = NOT AVAILABLE (no free API)"
        )

        return com.example.signalx.data.model.ConfluenceReport(
            groups = groups,
            agreeingLongCount = agreeingLong,
            agreeingShortCount = agreeingShort,
            finalSignal = finalSignal,
            bullishPct = bullishPct,
            bearishPct = bearishPct,
            confidence = rawConfidence,
            confidenceLabel = confLabel,
            missingConfluenceReason = missingReason,
            entryZone = entryZoneStr,
            stopLoss = PriceFormatter.formatPrice(symbol, marketType, slPrice),
            takeProfit1 = PriceFormatter.formatPrice(symbol, marketType, tp1Price),
            takeProfit2 = PriceFormatter.formatPrice(symbol, marketType, tp2Price),
            takeProfit3 = PriceFormatter.formatPrice(symbol, marketType, tp3Price),
            riskRewardRatio = rr,
            keySupport = PriceFormatter.formatPrice(symbol, marketType, report.keySupport),
            keyResistance = PriceFormatter.formatPrice(symbol, marketType, report.keyResistance),
            liquidityTargets = liquidityTargets,
            invalidationPrice = PriceFormatter.formatPrice(symbol, marketType, slPrice),
            volatilityState = if (report.isAtrSqueeze) "ATR Squeeze (Compression)" else "Normal Volatility",
            reasonsFor = reasonsFor,
            reasonsAgainst = reasonsAgainst,
            dataAgeSeconds = dataAgeSeconds,
            missingDataWarnings = missingDataWarnings
        )
    }

    /**
     * Wilson score 95% confidence interval for win rate
     */
    fun calculateWilsonInterval(p: Double, n: Int): Pair<Double, Double> {
        if (n <= 0) return Pair(0.0, 0.0)
        val z = 1.96 // 95% confidence
        val z2 = z * z
        val denominator = 1.0 + z2 / n
        val center = (p + z2 / (2 * n)) / denominator
        val margin = (z * sqrt((p * (1 - p) / n) + (z2 / (4 * n * n)))) / denominator
        val lower = (center - margin).coerceIn(0.0, 1.0)
        val upper = (center + margin).coerceIn(0.0, 1.0)
        return Pair(lower, upper)
    }

    fun calculateWilsonInterval(wins: Int, total: Int, z: Double = 1.96): Pair<Double, Double> {
        if (total <= 0) return Pair(0.0, 0.0)
        val p = wins.toDouble() / total.toDouble()
        val z2 = z * z
        val denominator = 1.0 + z2 / total
        val center = (p + z2 / (2 * total)) / denominator
        val margin = (z * sqrt((p * (1 - p) / total) + (z2 / (4 * total * total)))) / denominator
        val lower = (center - margin).coerceIn(0.0, 1.0)
        val upper = (center + margin).coerceIn(0.0, 1.0)
        return Pair(lower, upper)
    }
}
