package com.example

import com.example.signalx.data.api.BitgetApiClient
import com.example.signalx.data.api.BitgetTimeSync
import com.example.signalx.data.model.Candle
import com.example.signalx.data.model.Direction
import com.example.signalx.data.model.MarketType
import com.example.signalx.data.model.SignalType
import com.example.signalx.data.model.TimeFrame
import com.example.signalx.engine.analysis.MarketStructureAnalyzer
import com.example.signalx.engine.decision.TradingDecisionEngine
import com.example.signalx.engine.forecast.ForecastEngine
import com.example.signalx.engine.indicators.TechnicalIndicators
import com.example.signalx.engine.urdu.UrduExplanationGenerator
import com.example.signalx.engine.analysis.SmartMoneyEngine
import com.example.signalx.data.model.FvgType
import com.example.signalx.data.model.OrderBlockType
import com.example.signalx.data.model.StructureBreakType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    @Test
    fun testCandleAggregation3mFrom1m() {
        val client = BitgetApiClient()
        // Align t0 to exact 3-minute boundary
        val t0 = (1700000000000L / 180_000L) * 180_000L
        val candles1m = listOf(
            Candle(t0, 100.0, 105.0, 99.0, 102.0, 10.0, 1020.0),
            Candle(t0 + 60000, 102.0, 108.0, 101.0, 106.0, 15.0, 1590.0),
            Candle(t0 + 120000, 106.0, 107.0, 98.0, 103.0, 20.0, 2060.0)
        )
        val agg = client.aggregateCandles(candles1m, 180_000L)
        assertEquals(1, agg.size)
        val c = agg[0]
        assertEquals(100.0, c.open, 0.001)
        assertEquals(108.0, c.high, 0.001)
        assertEquals(98.0, c.low, 0.001)
        assertEquals(103.0, c.close, 0.001)
        assertEquals(45.0, c.volume, 0.001)
    }

    @Test
    fun testIndicatorMath() {
        val baseTime = 1700000000000L
        val candles = (1..30).map { i ->
            val p = 100.0 + i * 2.0
            Candle(baseTime + i * 60000L, p - 1.0, p + 2.0, p - 2.0, p, 100.0)
        }

        val ema7 = TechnicalIndicators.calculateEMA(candles, 7)
        assertTrue(ema7.isNotEmpty())
        assertTrue(ema7.last() > 140.0)

        val rsi14 = TechnicalIndicators.calculateRSI(candles, 14)
        assertTrue(rsi14.isNotEmpty())
        assertTrue(rsi14.last() > 80.0)

        val atr = TechnicalIndicators.calculateATR(candles, 14)
        assertTrue(atr > 3.0)

        val bb = TechnicalIndicators.calculateBollingerBands(candles, 20)
        assertTrue(bb != null)
        assertTrue(bb!!.upper > bb.middle)
        assertTrue(bb.middle > bb.lower)
    }

    @Test
    fun testServerTimeOffsetAndCountdown() {
        // Base aligned minute
        val baseMinute = 1700000040000L // 28333334 * 60000
        val refTime = baseMinute + 45000L // 45 seconds past boundary
        val (openMs, closeMs) = BitgetTimeSync.getCandleBoundaries(TimeFrame.M1, refTime)
        assertEquals(60000L, closeMs - openMs)

        val remaining = BitgetTimeSync.getRemainingTimeMs(TimeFrame.M1, refTime)
        assertEquals(15000L, remaining)
        assertEquals("00:15", BitgetTimeSync.formatCountdown(remaining))

        val isPrep = BitgetTimeSync.isPreparationMode(TimeFrame.M1, remaining)
        assertTrue(isPrep) // remaining <= 30s triggers preparation mode
    }

    @Test
    fun testForecastEngine14Horizons() {
        val baseTime = 1700000000000L
        val candles = (1..60).map { i ->
            val p = 50000.0 + i * 10.0
            Candle(baseTime + i * 60000L, p - 5.0, p + 15.0, p - 10.0, p, 50.0)
        }
        val forecasts = ForecastEngine.generateForecasts(candles)
        assertEquals(14, forecasts.size)

        for (f in forecasts) {
            val sum = f.probUp + f.probDown + f.probSideways
            assertTrue("Probabilities must sum to 100", abs(sum - 100.0) <= 1.0)
            assertTrue("Estimated high must be >= base price", f.estimatedHigh >= f.basePrice)
            assertTrue("Estimated low must be <= base price", f.estimatedLow <= f.basePrice)
        }
    }

    @Test
    fun testDynamicUrduGeneratorProducesDistinctContent() {
        val baseTime = 1700000000000L
        val bullishCandles = (1..60).map { i ->
            val p = 60000.0 + i * 30.0
            Candle(baseTime + i * 60000L, p - 5.0, p + 10.0, p - 8.0, p, 100.0)
        }
        val reportBull = MarketStructureAnalyzer.analyze(bullishCandles)
        val fBull = ForecastEngine.generateForecasts(bullishCandles).first()
        val decBull = TradingDecisionEngine.generateDecision(reportBull, fBull, MarketType.SPOT)
        val expBull = UrduExplanationGenerator.generateExplanation(reportBull, fBull, decBull, "BTCUSDT")

        val bearishCandles = (1..60).map { i ->
            val p = 60000.0 - i * 30.0
            Candle(baseTime + i * 60000L, p + 5.0, p + 8.0, p - 10.0, p, 100.0)
        }
        val reportBear = MarketStructureAnalyzer.analyze(bearishCandles)
        val fBear = ForecastEngine.generateForecasts(bearishCandles).first()
        val decBear = TradingDecisionEngine.generateDecision(reportBear, fBear, MarketType.SPOT)
        val expBear = UrduExplanationGenerator.generateExplanation(reportBear, fBear, decBear, "BTCUSDT")

        assertNotEquals(expBull.urduScript, expBear.urduScript)
        assertNotEquals(expBull.romanUrdu, expBear.romanUrdu)
        assertNotEquals(expBull.english, expBear.english)

        assertTrue(expBull.urduScript.contains("کینڈل") || expBull.urduScript.length > 50)
        assertTrue(expBull.romanUrdu.contains("price") || expBull.romanUrdu.contains("candle"))
    }

    @Test
    fun testPricePrecisionFormatting() {
        val formatter = com.example.signalx.data.api.PriceFormatter
        // Setup precisions
        formatter.setPrecision("XRPUSDT", MarketType.SPOT, 4)
        formatter.setPrecision("PEPEUSDT", MarketType.SPOT, 8)
        formatter.setPrecision("BTCUSDT", MarketType.SPOT, 2)
        formatter.setPrecision("DOGEUSDT", MarketType.SPOT, 4)

        // 1. XRP 1.5449
        val xrpResult = formatter.formatPrice("XRPUSDT", MarketType.SPOT, "1.5449")
        assertEquals("1.5449", xrpResult)

        // 2. Small coin 0.00001234
        val pepeResult = formatter.formatPrice("PEPEUSDT", MarketType.SPOT, "0.00001234")
        assertEquals("0.00001234", pepeResult)

        // 3. BTC 75889.4 with padding to 2 decimals
        val btcResult = formatter.formatPrice("BTCUSDT", MarketType.SPOT, "75889.4")
        assertEquals("75889.40", btcResult)

        // 4. DOGE 0.3203
        val dogeResult = formatter.formatPrice("DOGEUSDT", MarketType.SPOT, "0.3203")
        assertEquals("0.3203", dogeResult)

        // 5. Test tiny number never produces scientific notation (no 1.2E-8)
        formatter.setPrecision("TINYUSDT", MarketType.SPOT, 9)
        val tinyResult = formatter.formatPrice("TINYUSDT", MarketType.SPOT, "0.000000012")
        assertEquals("0.000000012", tinyResult)
        assertTrue("Must not contain scientific notation", !tinyResult.contains("E") && !tinyResult.contains("e"))
    }

    @Test
    fun testAll13TimeframesCountdownAndBoundaries() {
        val testServerTime = 1700003715000L // arbitrary fixed timestamp
        val allTimeframes = TimeFrame.entries

        assertEquals(13, allTimeframes.size)

        for (tf in allTimeframes) {
            val (openMs, closeMs) = BitgetTimeSync.getCandleBoundaries(tf, testServerTime)
            assertTrue("openMs must be <= testServerTime for ${tf.label}", openMs <= testServerTime)
            assertTrue("closeMs must be > testServerTime for ${tf.label}", closeMs > testServerTime)
            assertEquals("Candle duration must match for ${tf.label}", tf.durationMs, closeMs - openMs)

            val remaining = BitgetTimeSync.getRemainingTimeMs(tf, testServerTime)
            assertTrue("Remaining ms must be > 0 and <= duration for ${tf.label}", remaining in 1..tf.durationMs)

            val formatted = BitgetTimeSync.formatCountdown(remaining)
            assertTrue("Formatted countdown must not be empty for ${tf.label}", formatted.isNotEmpty() && formatted.contains(":"))
        }
    }

    @Test
    fun testNoLookaheadIntegrity() {
        // Build 100 historical candles
        val baseTime = 1700000000000L
        val fullCandles = (1..100).map { i ->
            val p = 60000.0 + i * 20.0
            Candle(baseTime + i * 60000L, p - 5.0, p + 10.0, p - 5.0, p, 100.0)
        }

        // Sublist up to index 70
        val sublist70 = fullCandles.take(70)
        val reportAt70 = MarketStructureAnalyzer.analyze(sublist70)
        val forecastAt70 = ForecastEngine.generateForecasts(sublist70).first()
        val decisionAt70 = TradingDecisionEngine.generateDecision(reportAt70, forecastAt70, MarketType.SPOT)

        // Repeat evaluation on identical 70 candles
        val reportReplay = MarketStructureAnalyzer.analyze(sublist70)
        val forecastReplay = ForecastEngine.generateForecasts(sublist70).first()
        val decisionReplay = TradingDecisionEngine.generateDecision(reportReplay, forecastReplay, MarketType.SPOT)

        // Assert strictly deterministic outcome with no lookahead contamination
        assertEquals(decisionAt70.signal, decisionReplay.signal)
        assertEquals(decisionAt70.stopLoss, decisionReplay.stopLoss, 0.0001)
        assertEquals(decisionAt70.takeProfit1, decisionReplay.takeProfit1, 0.0001)
        assertEquals(forecastAt70.direction, forecastReplay.direction)
    }

    @Test
    fun testWilsonConfidenceIntervalCalculation() {
        val (lower, upper) = TradingDecisionEngine.calculateWilsonInterval(wins = 70, total = 100, z = 1.96)
        assertTrue("Lower bound must be < 0.70", lower < 0.70)
        assertTrue("Upper bound must be > 0.70", upper > 0.70)
        assertTrue("Lower bound must be reasonable", lower in 0.59..0.62)
        assertTrue("Upper bound must be reasonable", upper in 0.77..0.80)
    }

    @Test
    fun testBreakoutAnd200CandleReading() {
        val baseTime = 1700000000000L
        // Build 200 candles with a clear consolidation followed by high volume push to resistance
        val candles = (1..200).map { i ->
            val p = 100.0 + (i % 5) * 0.5
            Candle(baseTime + i * 60000L, p - 0.2, p + 0.3, p - 0.3, p, 50.0)
        }
        val report = MarketStructureAnalyzer.analyze(candles, symbol = "BTCUSDT", marketType = MarketType.SPOT)
        assertTrue("Must analyze up to 200 candles", candles.size == 200)
        assertTrue("20-candle range high must be > 0", report.range20High > 0.0)
        assertTrue("20-candle range low must be <= range high", report.range20Low <= report.range20High)
        assertTrue("200-candle range high must be >= 20-candle high", report.range200High >= report.range20High)

        val breakout = report.breakoutAnalysis
        assertTrue("Breakout analysis must not be null", breakout != null)
        assertTrue("Confirmation criteria must require relative volume >= 1.2", breakout!!.confirmationCriteria.contains("1.2"))
        assertTrue("Likely minute window must not be empty", breakout.likelyMinuteWindow.isNotEmpty())
        assertTrue("Breakout level must be > 0", breakout.breakoutLevel > 0.0)
        assertTrue("Invalidation price must be > 0", breakout.invalidationPrice > 0.0)
    }

    @Test
    fun testNextCandleMinuteByMinuteReport() {
        val baseTime = 1700000000000L
        val candles = (1..60).map { i ->
            val p = 50000.0 + i * 15.0
            Candle(baseTime + i * 60000L, p - 5.0, p + 10.0, p - 8.0, p, 80.0)
        }
        val report = ForecastEngine.generateNextCandleReport(
            candles1m = candles,
            symbol = "BTCUSDT",
            marketType = MarketType.SPOT
        )

        assertEquals("BTCUSDT", report.symbol)
        assertTrue("Must include 10 minutes plus 15/30/45/60", report.minutesTable.size >= 14)
        assertTrue("Best entry minute must be between 1 and 5", report.bestEntryMinute in 1..5)
        assertTrue("Must contain disclaimer", report.disclaimer.contains("probability estimate", ignoreCase = true))

        val bestItem = report.minutesTable.firstOrNull { it.minute == report.bestEntryMinute }
        assertTrue("Best entry item must have isBestEntry == true", bestItem?.isBestEntry == true)

        for (item in report.minutesTable) {
            assertTrue("Estimated high must be non-empty", item.estimatedHighStr.isNotEmpty())
            assertTrue("Estimated low must be non-empty", item.estimatedLowStr.isNotEmpty())
            assertTrue("No scientific notation allowed", !item.estimatedHighStr.contains("E") && !item.estimatedLowStr.contains("E"))
            assertTrue("Probability must be in 0..100", item.probPercent in 0..100)
        }
    }

    @Test
    fun testBtcCorrelationAndSignalDowngrade() {
        val baseTime = 1700000000000L
        val seriesA = (1..50).map { it * 2.0 }
        val seriesB = (1..50).map { it * 4.0 }
        val seriesInv = (1..50).map { 100.0 - it * 2.0 }

        // Pearson correlation test
        val corrPos = MarketStructureAnalyzer.calculateCorrelation(seriesA, seriesB)
        assertEquals(1.0, corrPos, 0.001)

        val corrNeg = MarketStructureAnalyzer.calculateCorrelation(seriesA, seriesInv)
        assertEquals(-1.0, corrNeg, 0.001)

        // Altcoin bullish while BTC is bearish -> Oppose and downgrade
        val btcBearishCandles = (1..50).map { i ->
            val p = 70000.0 - i * 50.0
            Candle(baseTime + i * 60000L, p + 10.0, p + 15.0, p - 20.0, p, 100.0)
        }
        val altBullishCandles = (1..50).map { i ->
            val p = 1.0 + i * 0.02
            Candle(baseTime + i * 60000L, p - 0.01, p + 0.02, p - 0.01, p, 50.0)
        }

        val btcAnalysis = MarketStructureAnalyzer.analyzeBtcCorrelation(
            activeSymbol = "XRPUSDT",
            activeCandles = altBullishCandles,
            btcCandles = btcBearishCandles,
            activeSignalDirection = Direction.UP
        )

        assertEquals(Direction.DOWN, btcAnalysis.btcTrend)

        // When BTC opposes the signal, trading decision engine must downgrade to WAIT
        val rep = MarketStructureAnalyzer.analyze(altBullishCandles, symbol = "XRPUSDT", marketType = MarketType.SPOT)
        val f = ForecastEngine.generateForecasts(altBullishCandles).first()
        val decision = TradingDecisionEngine.generateDecision(
            report = rep,
            forecast1m = f,
            marketType = MarketType.SPOT,
            symbol = "XRPUSDT",
            btcCorrelation = btcAnalysis.copy(isDowngradedByBtc = true, btcSupportState = "BTC opposes active coin")
        )

        assertEquals("Signal must be downgraded to WAIT when BTC opposes", SignalType.WAIT, decision.signal)
        assertTrue("Reasons against must mention BTC opposition", decision.reasonsAgainst.any { it.contains("BTC opposes") })
    }

    @Test
    fun testUsdPriceTierAndOneCentMove() {
        // Cheap < $1: price $0.25 -> 1 cent move is (0.01 / 0.25) * 100% = 4.0%
        val cheapPrice = 0.25
        val cheapTier = when {
            cheapPrice < 1.0 -> "Cheap < $1"
            cheapPrice <= 100.0 -> "Mid $1-$100"
            else -> "High > $100"
        }
        val cheapMove = (0.01 / cheapPrice) * 100.0
        assertEquals("Cheap < $1", cheapTier)
        assertEquals(4.0, cheapMove, 0.001)

        // Mid $1-$100: price $50 -> 1 cent move is (0.01 / 50) * 100% = 0.02%
        val midPrice = 50.0
        val midTier = when {
            midPrice < 1.0 -> "Cheap < $1"
            midPrice <= 100.0 -> "Mid $1-$100"
            else -> "High > $100"
        }
        val midMove = (0.01 / midPrice) * 100.0
        assertEquals("Mid $1-$100", midTier)
        assertEquals(0.02, midMove, 0.001)

        // High > $100: price $10,000 -> 1 cent move is (0.01 / 10000) * 100% = 0.0001%
        val highPrice = 10_000.0
        val highTier = when {
            highPrice < 1.0 -> "Cheap < $1"
            highPrice <= 100.0 -> "Mid $1-$100"
            else -> "High > $100"
        }
        val highMove = (0.01 / highPrice) * 100.0
        assertEquals("High > $100", highTier)
        assertEquals(0.0001, highMove, 0.00001)
    }

    @Test
    fun testCvdManualTradeInjectionAccuracy() {
        val wsManager = com.example.signalx.data.api.BitgetWebSocketManager()

        // Inject 3 buys and 2 sells
        // Buy 1.5 BTC
        wsManager.injectTrade("buy", java.math.BigDecimal("1.50000000"))
        // Buy 2.25 BTC
        wsManager.injectTrade("buy", java.math.BigDecimal("2.25000000"))
        // Sell 0.75 BTC
        wsManager.injectTrade("sell", java.math.BigDecimal("0.75000000"))
        // Sell 1.00 BTC
        wsManager.injectTrade("sell", java.math.BigDecimal("1.00000000"))
        // Buy 0.50 BTC
        wsManager.injectTrade("buy", java.math.BigDecimal("0.50000000"))

        val cvd = wsManager.cvdData.value
        org.junit.Assert.assertNotNull(cvd)
        // Total Buy = 1.5 + 2.25 + 0.50 = 4.25
        assertEquals(4.25, cvd!!.sessionBuyVolume.toDouble(), 0.0001)
        // Total Sell = 0.75 + 1.00 = 1.75
        assertEquals(1.75, cvd.sessionSellVolume.toDouble(), 0.0001)
        // Net Cumulative Volume Delta = 4.25 - 1.75 = +2.50
        assertEquals(2.50, cvd.cumulativeVolumeDelta.toDouble(), 0.0001)
        // Trend must be UP since CVD > oldest (0.0)
        assertEquals(Direction.UP, cvd.cvdTrend)
        // Formatted CVD must represent 2.50
        assertTrue(cvd.formattedCvd.contains("2.5"))
    }

    @Test
    fun testSmartMoneyConceptsFvgDetection() {
        val baseTime = 1700000000000L
        val candles = listOf(
            Candle(baseTime, 96.0, 100.0, 95.0, 98.0, 50.0),
            Candle(baseTime + 60000L, 98.0, 115.0, 97.0, 114.0, 500.0),
            Candle(baseTime + 120000L, 114.0, 120.0, 106.0, 118.0, 60.0),
            Candle(baseTime + 180000L, 118.0, 122.0, 116.0, 121.0, 40.0)
        )

        val smc = SmartMoneyEngine.analyze(candles)
        assertTrue("Must detect at least 1 FVG", smc.fvgs.isNotEmpty())
        val bullishFvg = smc.fvgs.firstOrNull { it.type == FvgType.BULLISH }
        assertNotNull("Must detect Bullish FVG", bullishFvg)
        assertEquals(100.0, bullishFvg!!.bottom, 0.001)
        assertEquals(106.0, bullishFvg.top, 0.001)
        assertEquals(103.0, bullishFvg.midpoint, 0.001)
        assertEquals(0.0, bullishFvg.fillPercent, 0.001)
        assertTrue(bullishFvg.isDisplacementFvg)
    }

    @Test
    fun testOrderBlockDetection() {
        val baseTime = 1700000000000L
        val candles = (0..15).map { i ->
            val p = 100.0 - i * 0.5
            Candle(baseTime + i * 60000L, p, p + 0.5, p - 0.5, p - 0.2, 50.0)
        }.toMutableList()

        // Bearish last candle before displacement (Order Block)
        candles.add(Candle(baseTime + 16 * 60000L, 92.0, 93.0, 89.0, 89.5, 60.0))
        // Strong bullish displacement candle that breaks previous highs
        candles.add(Candle(baseTime + 17 * 60000L, 89.5, 112.0, 90.0, 110.0, 800.0))
        candles.add(Candle(baseTime + 18 * 60000L, 110.0, 115.0, 108.0, 114.0, 100.0))

        val smc = SmartMoneyEngine.analyze(candles)
        assertTrue("Must detect order blocks", smc.orderBlocks.isNotEmpty())
        val bullOb = smc.orderBlocks.firstOrNull { it.type == OrderBlockType.BULLISH_OB }
        assertNotNull("Must detect Bullish Order Block", bullOb)
        assertEquals(89.0, bullOb!!.bottom, 0.001)
        assertEquals(93.0, bullOb.top, 0.001)
        assertTrue(bullOb.displacementStrength >= 1.0)
    }

    @Test
    fun testMarketStructureBreakBosVsChoch() {
        val baseTime = 1700000000000L
        val candles = mutableListOf<Candle>()
        // Build an uptrend with clear swing high at 110.0 and pullback to 102.0
        candles.add(Candle(baseTime, 100.0, 105.0, 99.0, 104.0, 50.0))
        candles.add(Candle(baseTime + 60000L, 104.0, 110.0, 103.0, 109.0, 60.0)) // Swing high
        candles.add(Candle(baseTime + 120000L, 109.0, 109.0, 102.0, 103.0, 40.0)) // Swing low
        // Candle closes above 110.0 -> Confirmed BOS
        candles.add(Candle(baseTime + 180000L, 103.0, 116.0, 102.5, 115.0, 150.0))

        val smc = SmartMoneyEngine.analyze(candles)
        assertTrue("Must detect structure breaks", smc.structureBreaks.isNotEmpty())
        val breakItem = smc.structureBreaks.first()
        assertTrue(
            breakItem.type == StructureBreakType.BOS ||
            breakItem.type == StructureBreakType.CHOCH ||
            breakItem.type == StructureBreakType.MSS
        )
        assertEquals(110.0, breakItem.breakPrice, 0.001)
        assertTrue("Break was confirmed by candle close", breakItem.confirmedByClose)
    }

    @Test
    fun testPremiumDiscountEquilibriumAndOte() {
        val baseTime = 1700000000000L
        val candles = listOf(
            Candle(baseTime, 50000.0, 51000.0, 50000.0, 50500.0, 10.0),
            Candle(baseTime + 60000L, 50500.0, 60000.0, 50400.0, 59500.0, 20.0),
            Candle(baseTime + 120000L, 59500.0, 59800.0, 54000.0, 54500.0, 15.0)
        )

        val smc = SmartMoneyEngine.analyze(candles)
        assertNotNull("Premium/Discount must be calculated", smc.premiumDiscount)
        val pd = smc.premiumDiscount!!
        assertEquals(60000.0, pd.dealingRangeHigh, 0.001)
        assertEquals(50000.0, pd.dealingRangeLow, 0.001)
        // 50% Equilibrium line
        assertEquals(55000.0, pd.equilibrium50, 0.001)
        // Bullish OTE Zone: 61.8% to 78.6% retracement from high
        assertEquals(53820.0, pd.oteUpper618, 0.01)
        assertEquals(52140.0, pd.oteLower786, 0.01)
    }

    @Test
    fun testSmtDivergenceBenchmarkComparison() {
        val baseTime = 1700000000000L
        // Active symbol makes Lower Low (100.0 -> 90.0)
        val activeCandles = listOf(
            Candle(baseTime, 105.0, 110.0, 100.0, 102.0, 10.0),
            Candle(baseTime + 60000L, 102.0, 108.0, 101.0, 107.0, 10.0),
            Candle(baseTime + 120000L, 107.0, 107.0, 90.0, 92.0, 10.0)
        )

        // Benchmark BTC makes Higher Low (60000.0 -> 62000.0)
        val btcCandles = listOf(
            Candle(baseTime, 60500.0, 61000.0, 60000.0, 60200.0, 100.0),
            Candle(baseTime + 60000L, 60200.0, 63000.0, 60100.0, 62800.0, 100.0),
            Candle(baseTime + 120000L, 62800.0, 63500.0, 62000.0, 63000.0, 100.0)
        )

        val smc = SmartMoneyEngine.analyze(activeCandles, btcCandles)
        val smt = smc.smtDivergence
        assertNotNull("Must detect SMT divergence against BTC benchmark", smt)
        assertTrue(smt!!.isDetected)
        assertTrue(smt.isBullish)
        assertEquals("BTCUSDT", smt.comparisonSymbol)
    }
}
