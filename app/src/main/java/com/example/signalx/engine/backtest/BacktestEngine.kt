package com.example.signalx.engine.backtest

import com.example.signalx.data.model.Candle
import com.example.signalx.data.model.Direction
import com.example.signalx.data.model.MarketType
import com.example.signalx.data.model.SignalType
import com.example.signalx.engine.analysis.MarketStructureAnalyzer
import com.example.signalx.engine.decision.TradingDecisionEngine
import com.example.signalx.engine.forecast.ForecastEngine
import kotlin.math.max

data class BacktestTrade(
    val entryIndex: Int,
    val exitIndex: Int,
    val isLong: Boolean,
    val entryPrice: Double,
    val exitPrice: Double,
    val pnlPct: Double,
    val isWin: Boolean,
    val reason: String
)

data class BacktestResult(
    val totalTrades: Int,
    val winTrades: Int,
    val lossTrades: Int,
    val winRate: Double,
    val profitFactor: Double,
    val maxDrawdownPct: Double,
    val totalReturnPct: Double,
    val equityCurve: List<Double>,
    val trades: List<BacktestTrade>
)

object BacktestEngine {

    fun runBacktest(
        candles: List<Candle>,
        marketType: MarketType,
        feeRate: Double = 0.001, // 0.1%
        slippageRate: Double = 0.0002 // 0.02%
    ): BacktestResult {
        if (candles.size < 60) {
            return BacktestResult(0, 0, 0, 0.0, 0.0, 0.0, 0.0, listOf(100.0), emptyList())
        }

        val trades = mutableListOf<BacktestTrade>()
        var activePositionIsLong: Boolean? = null
        var entryPrice = 0.0
        var stopLoss = 0.0
        var takeProfit = 0.0
        var entryIdx = 0

        var equity = 100.0 // Starting at 100%
        val equityCurve = mutableListOf(equity)
        var peakEquity = equity
        var maxDrawdown = 0.0

        var grossProfit = 0.0
        var grossLoss = 0.0

        // Step candle-by-candle (warm-up of 50 candles for indicators)
        for (i in 50 until candles.size) {
            val historySlice = candles.subList(0, i + 1)
            val currCandle = candles[i]

            // 1. Manage open position
            if (activePositionIsLong != null) {
                val isLong = activePositionIsLong!!
                var exited = false
                var exitPrice = 0.0
                var reason = ""

                if (isLong) {
                    if (currCandle.low <= stopLoss) {
                        exitPrice = stopLoss * (1.0 - slippageRate)
                        exited = true
                        reason = "Stop Loss"
                    } else if (currCandle.high >= takeProfit) {
                        exitPrice = takeProfit * (1.0 - slippageRate)
                        exited = true
                        reason = "Take Profit"
                    }
                } else {
                    if (currCandle.high >= stopLoss) {
                        exitPrice = stopLoss * (1.0 + slippageRate)
                        exited = true
                        reason = "Stop Loss"
                    } else if (currCandle.low <= takeProfit) {
                        exitPrice = takeProfit * (1.0 + slippageRate)
                        exited = true
                        reason = "Take Profit"
                    }
                }

                if (exited) {
                    val rawPnlPct = if (isLong) {
                        ((exitPrice - entryPrice) / entryPrice) * 100.0
                    } else {
                        ((entryPrice - exitPrice) / entryPrice) * 100.0
                    }
                    val netPnlPct = rawPnlPct - (feeRate * 2.0 * 100.0) // entry + exit fees
                    val isWin = netPnlPct > 0

                    if (netPnlPct > 0) grossProfit += netPnlPct else grossLoss += -netPnlPct
                    equity *= (1.0 + netPnlPct / 100.0)
                    equityCurve.add(equity)

                    if (equity > peakEquity) peakEquity = equity
                    val dd = if (peakEquity > 0) ((peakEquity - equity) / peakEquity) * 100.0 else 0.0
                    if (dd > maxDrawdown) maxDrawdown = dd

                    trades.add(BacktestTrade(entryIdx, i, isLong, entryPrice, exitPrice, netPnlPct, isWin, reason))
                    activePositionIsLong = null
                }
            }

            // 2. Look for new entries if no active position
            if (activePositionIsLong == null && i < candles.size - 1) {
                val report = MarketStructureAnalyzer.analyze(historySlice)
                val forecasts = ForecastEngine.generateForecasts(historySlice)
                val f1 = forecasts.firstOrNull()
                val decision = TradingDecisionEngine.generateDecision(report, f1, marketType)

                if (decision.signal == SignalType.STRONG_LONG || decision.signal == SignalType.LONG) {
                    activePositionIsLong = true
                    entryPrice = currCandle.close * (1.0 + slippageRate)
                    stopLoss = decision.stopLoss
                    takeProfit = decision.takeProfit1
                    entryIdx = i
                } else if ((decision.signal == SignalType.STRONG_SHORT || decision.signal == SignalType.SHORT) && marketType == MarketType.FUTURES) {
                    activePositionIsLong = false
                    entryPrice = currCandle.close * (1.0 - slippageRate)
                    stopLoss = decision.stopLoss
                    takeProfit = decision.takeProfit1
                    entryIdx = i
                }
            }
        }

        val winTrades = trades.count { it.isWin }
        val lossTrades = trades.size - winTrades
        val winRate = if (trades.isNotEmpty()) (winTrades.toDouble() / trades.size) * 100.0 else 0.0
        val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) 99.9 else 1.0
        val totalReturnPct = equity - 100.0

        return BacktestResult(
            totalTrades = trades.size,
            winTrades = winTrades,
            lossTrades = lossTrades,
            winRate = winRate,
            profitFactor = profitFactor,
            maxDrawdownPct = maxDrawdown,
            totalReturnPct = totalReturnPct,
            equityCurve = equityCurve,
            trades = trades
        )
    }
}
