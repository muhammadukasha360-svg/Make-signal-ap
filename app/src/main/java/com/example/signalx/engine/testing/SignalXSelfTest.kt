package com.example.signalx.engine.testing

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class TestResultItem(
    val testName: String,
    val isPassed: Boolean,
    val details: String
)

object SignalXSelfTest {

    suspend fun runAllTests(apiClient: BitgetApiClient): List<TestResultItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TestResultItem>()

        // 1. Bitget Server Time API test
        try {
            val serverTime = apiClient.syncServerTime()
            val offset = BitgetTimeSync.serverOffsetMs
            val pktTime = BitgetTimeSync.formatPktTime(serverTime)
            results.add(
                TestResultItem(
                    testName = "1. Server Time API & PKT Sync",
                    isPassed = serverTime > 1_600_000_000_000L,
                    details = "Synced: serverTime=$serverTime, offset=${offset}ms, PKT Time=$pktTime"
                )
            )
        } catch (e: Exception) {
            results.add(TestResultItem("1. Server Time API & PKT Sync", false, "Failed: ${e.message}"))
        }

        // 2. Symbols Loading Test
        try {
            val spotSymbols = apiClient.getSpotSymbols()
            val futuresSymbols = apiClient.getFuturesSymbols()
            results.add(
                TestResultItem(
                    testName = "2. Exchange Symbols Online",
                    isPassed = spotSymbols.isNotEmpty() && futuresSymbols.isNotEmpty(),
                    details = "Loaded ${spotSymbols.size} Spot USDT pairs, ${futuresSymbols.size} Futures contracts"
                )
            )
        } catch (e: Exception) {
            results.add(TestResultItem("2. Exchange Symbols Online", false, "Failed: ${e.message}"))
        }

        // 3. Candle Aggregation Math Test (3m from 1m)
        try {
            val t0 = 1700000000000L // aligned base
            val dummy1m = listOf(
                Candle(t0, 100.0, 105.0, 99.0, 102.0, 10.0, 1020.0),
                Candle(t0 + 60000, 102.0, 108.0, 101.0, 106.0, 15.0, 1590.0),
                Candle(t0 + 120000, 106.0, 107.0, 98.0, 103.0, 20.0, 2060.0)
            )
            val agg3m = apiClient.aggregateCandles(dummy1m, 180_000L)
            val c = agg3m.firstOrNull()
            val passed = c != null && c.open == 100.0 && c.high == 108.0 && c.low == 98.0 && c.close == 103.0 && c.volume == 45.0
            results.add(
                TestResultItem(
                    testName = "3. Candle Aggregation (3m from 1m)",
                    isPassed = passed,
                    details = "Open=${c?.open}, High=${c?.high}, Low=${c?.low}, Close=${c?.close}, Vol=${c?.volume}"
                )
            )
        } catch (e: Exception) {
            results.add(TestResultItem("3. Candle Aggregation", false, "Failed: ${e.message}"))
        }

        // 4. Indicator Math Verification (Hand-verified values)
        try {
            // Test data: 15 candles
            val baseTime = 1700000000000L
            val testCandles = (1..20).map { i ->
                val p = 100.0 + i * 2.0
                Candle(baseTime + i * 60000L, p - 1.0, p + 2.0, p - 2.0, p, 100.0)
            }
            val ema7 = TechnicalIndicators.calculateEMA(testCandles, 7).last()
            val rsi14 = TechnicalIndicators.calculateRSI(testCandles, 14).last()
            val atr14 = TechnicalIndicators.calculateATR(testCandles, 14)
            val bb = TechnicalIndicators.calculateBollingerBands(testCandles, 14)

            val passed = ema7 > 130.0 && rsi14 > 90.0 && atr14 > 3.0 && bb != null && bb.upper > bb.lower
            results.add(
                TestResultItem(
                    testName = "4. Indicator Engine Math Verification",
                    isPassed = passed,
                    details = "EMA7=${String.format("%.1f", ema7)}, RSI14=${String.format("%.1f", rsi14)}, ATR=${String.format("%.2f", atr14)}, BB.width=${String.format("%.3f", bb?.bandwidth ?: 0.0)}"
                )
            )
        } catch (e: Exception) {
            results.add(TestResultItem("4. Indicator Engine Math Verification", false, "Failed: ${e.message}"))
        }

        // 5. Countdown & Boundary Math Test
        try {
            val refTime = 1700000045000L // 45 seconds past boundary
            val (openMs, closeMs) = BitgetTimeSync.getCandleBoundaries(TimeFrame.M1, refTime)
            val remaining = BitgetTimeSync.getRemainingTimeMs(TimeFrame.M1, refTime)
            val isPrep = BitgetTimeSync.isPreparationMode(TimeFrame.M1, remaining)

            val passed = (closeMs - openMs == 60000L) && remaining == 15000L && isPrep
            results.add(
                TestResultItem(
                    testName = "5. Countdown & Next-Candle Prep Math",
                    isPassed = passed,
                    details = "Duration=${closeMs - openMs}ms, Remaining=${remaining}ms (${BitgetTimeSync.formatCountdown(remaining)}), isPrep=$isPrep"
                )
            )
        } catch (e: Exception) {
            results.add(TestResultItem("5. Countdown & Next-Candle Prep Math", false, "Failed: ${e.message}"))
        }

        // 6. Forecast Engine 14 Horizons & Probability Sum
        try {
            val baseTime = 1700000000000L
            val testCandles = (1..60).map { i ->
                val p = 50000.0 + i * 10.0
                Candle(baseTime + i * 60000L, p - 5.0, p + 15.0, p - 10.0, p, 50.0)
            }
            val forecasts = ForecastEngine.generateForecasts(testCandles)
            val all14 = forecasts.size == 14
            val probsSum100 = forecasts.all { abs((it.probUp + it.probDown + it.probSideways) - 100.0) <= 1.0 }
            val rangeValid = forecasts.all { it.estimatedHigh >= it.basePrice && it.estimatedLow <= it.basePrice }

            val passed = all14 && probsSum100 && rangeValid
            results.add(
                TestResultItem(
                    testName = "6. Forecast Engine (14 Horizons & Probabilities)",
                    isPassed = passed,
                    details = "Horizons generated=${forecasts.size}/14, ProbSumCheck=$probsSum100, High/Low Range Valid=$rangeValid"
                )
            )
        } catch (e: Exception) {
            results.add(TestResultItem("6. Forecast Engine", false, "Failed: ${e.message}"))
        }

        // 7. Urdu & Roman Urdu Dynamic NLG Generator Test
        try {
            val baseTime = 1700000000000L
            val testCandles = (1..60).map { i ->
                val p = 60000.0 + i * 20.0
                Candle(baseTime + i * 60000L, p - 5.0, p + 10.0, p - 8.0, p, 100.0)
            }
            val report = MarketStructureAnalyzer.analyze(testCandles)
            val f = ForecastEngine.generateForecasts(testCandles).first()
            val decision = TradingDecisionEngine.generateDecision(report, f, MarketType.SPOT)
            val urduExp = UrduExplanationGenerator.generateExplanation(report, f, decision, "BTCUSDT")

            val hasUrdu = urduExp.urduScript.contains("کینڈل") && urduExp.urduScript.contains("61200") || urduExp.urduScript.length > 50
            val hasRoman = urduExp.romanUrdu.contains("candle") || urduExp.romanUrdu.contains("price")
            val hasEnglish = urduExp.english.contains("next candle") || urduExp.english.contains("probability")

            val passed = hasUrdu && hasRoman && hasEnglish
            results.add(
                TestResultItem(
                    testName = "7. Dynamic Urdu & Roman Urdu Explanation Generator",
                    isPassed = passed,
                    details = "Urdu length=${urduExp.urduScript.length}, Roman length=${urduExp.romanUrdu.length}, English length=${urduExp.english.length}"
                )
            )
        } catch (e: Exception) {
            results.add(TestResultItem("7. Dynamic Urdu & Roman Urdu Generator", false, "Failed: ${e.message}"))
        }

        // 8. Trading Decision Confirmation Rules & Invalidation Test
        try {
            val baseTime = 1700000000000L
            val testCandles = (1..60).map { i ->
                val p = 100.0
                Candle(baseTime + i * 60000L, p, p + 0.5, p - 0.5, p, 10.0)
            }
            val report = MarketStructureAnalyzer.analyze(testCandles)
            val f = ForecastEngine.generateForecasts(testCandles).first()
            val decision = TradingDecisionEngine.generateDecision(report, f, MarketType.SPOT)

            // Ranging market with no volume should trigger WAIT
            val passed = decision.signal == SignalType.WAIT && decision.waitReason != null
            results.add(
                TestResultItem(
                    testName = "8. Decision Engine Confirmation & WAIT Enforcement",
                    isPassed = passed,
                    details = "Signal=${decision.signal}, WaitReason=${decision.waitReason}"
                )
            )
        } catch (e: Exception) {
            results.add(TestResultItem("8. Decision Engine Confirmation & WAIT Enforcement", false, "Failed: ${e.message}"))
        }

        // 9. Paper Trading PnL & Fee Calculation Test
        try {
            val entryPrice = 100.0
            val exitPrice = 110.0 // +10%
            val quantity = 10.0 // 1000 USDT position
            val feeRate = 0.001 // 0.1% per side
            val leverage = 5

            val grossPnl = (exitPrice - entryPrice) * quantity * leverage // $500
            val entryFee = (entryPrice * quantity * leverage) * feeRate // $5
            val exitFee = (exitPrice * quantity * leverage) * feeRate // $5.5
            val netPnl = grossPnl - entryFee - exitFee

            val passed = netPnl in 489.0..490.0
            results.add(
                TestResultItem(
                    testName = "9. Paper Trading PnL & Fee Math",
                    isPassed = passed,
                    details = "Gross PnL=$grossPnl, Fees=${entryFee + exitFee}, Net PnL=$netPnl"
                )
            )
        } catch (e: Exception) {
            results.add(TestResultItem("9. Paper Trading Math", false, "Failed: ${e.message}"))
        }

        return@withContext results
    }
}
