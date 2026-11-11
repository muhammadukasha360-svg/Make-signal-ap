package com.example.signalx.data.model

import com.example.signalx.data.api.PriceFormatter
import java.math.BigDecimal

enum class MarketType {
    SPOT,
    FUTURES
}

enum class TimeFrame(
    val label: String,
    val spotGranularity: String,
    val futuresGranularity: String,
    val durationMs: Long,
    val isAggregated: Boolean,
    val sourceTimeFrame: TimeFrame? = null
) {
    M1("1m", "1min", "1m", 60_000L, false),
    M3("3m", "1min", "1m", 180_000L, true, M1), // Aggregated from 1m
    M5("5m", "5min", "5m", 300_000L, false),
    M15("15m", "15min", "15m", 900_000L, false),
    M30("30m", "30min", "30m", 1_800_000L, false),
    H1("1h", "1h", "1H", 3_600_000L, false),
    H2("2h", "1h", "1H", 7_200_000L, true, H1), // Aggregated from 1h
    H4("4h", "4h", "4H", 14_400_000L, false),
    H6("6h", "6h", "6H", 21_600_000L, false),
    H12("12h", "12h", "12H", 43_200_000L, false),
    D1("1D", "1day", "1D", 86_400_000L, false),
    W1("1W", "1week", "1W", 604_800_000L, false),
    MONTH1("1M", "1month", "1M", 2_592_000_000L, false);

    companion object {
        fun fromLabel(label: String): TimeFrame = entries.firstOrNull { it.label == label } ?: M15
    }
}

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val quoteVolume: Double = 0.0,
    val isClosed: Boolean = true,
    val rawOpen: String = "",
    val rawHigh: String = "",
    val rawLow: String = "",
    val rawClose: String = ""
) {
    val isBullish: Boolean get() = close >= open
    val bodySize: Double get() = kotlin.math.abs(close - open)
    val upperWick: Double get() = high - kotlin.math.max(open, close)
    val lowerWick: Double get() = kotlin.math.min(open, close) - low
    val totalRange: Double get() = high - low
}

data class Ticker(
    val symbol: String,
    val lastPrice: Double,
    val high24h: Double,
    val low24h: Double,
    val change24h: Double, // decimal ratio, e.g. 0.025 for +2.5%
    val volume24h: Double,
    val quoteVolume24h: Double,
    val timestamp: Long,
    val bid: Double = 0.0,
    val ask: Double = 0.0,
    val markPrice: Double = 0.0,
    val indexPrice: Double = 0.0,
    val fundingRate: Double = 0.0,
    val openInterest: Double = 0.0,
    val marketType: MarketType = MarketType.SPOT,
    val pricePrecision: Int = 2,
    val rawLastPrice: String = "",
    val rawMarkPrice: String = "",
    val rawIndexPrice: String = "",
    val rawBidPrice: String = "",
    val rawAskPrice: String = "",
    val priceSource: String = "WS" // "WS" or "REST"
) {
    val change24hPercent: Double get() = change24h * 100.0
    val isBullish: Boolean get() = change24h >= 0.0

    // Formatted price string with exact exchange precision padding
    val formattedLastPrice: String
        get() = PriceFormatter.formatPrice(
            symbol,
            marketType,
            if (rawLastPrice.isNotEmpty()) rawLastPrice else lastPrice.toString()
        )
}

data class OrderBookLevel(
    val price: Double,
    val quantity: Double,
    val rawPrice: String = ""
)

data class OrderBook(
    val asks: List<OrderBookLevel>,
    val bids: List<OrderBookLevel>,
    val timestamp: Long
) {
    val totalBidVolume: Double = bids.take(15).sumOf { it.quantity }
    val totalAskVolume: Double = asks.take(15).sumOf { it.quantity }
    val imbalance: Double = if (totalBidVolume + totalAskVolume > 0) {
        (totalBidVolume - totalAskVolume) / (totalBidVolume + totalAskVolume)
    } else 0.0
    val spread: Double = if (asks.isNotEmpty() && bids.isNotEmpty()) {
        (asks.firstOrNull()?.price ?: 0.0) - (bids.firstOrNull()?.price ?: 0.0)
    } else 0.0
}

enum class Direction {
    UP,
    DOWN,
    SIDEWAYS
}

enum class SignalType {
    STRONG_LONG,
    LONG,
    WAIT,
    SHORT,
    STRONG_SHORT
}

enum class Language {
    URDU_SCRIPT,
    ROMAN_URDU,
    ENGLISH
}

data class EvidenceItem(
    val name: String,
    val direction: Direction,
    val weight: Double,
    val description: String
)

data class ForecastResult(
    val horizonMinutes: Int,
    val direction: Direction,
    val probUp: Double,
    val probDown: Double,
    val probSideways: Double,
    val estimatedHigh: Double,
    val estimatedLow: Double,
    val expectedPctMove: Double,
    val momentumScore: Double, // -100 to +100
    val confidence: Double,    // 0 to 100
    val evidence: List<EvidenceItem>,
    val timestamp: Long,
    val basePrice: Double,
    val estimatedHighStr: String = "",
    val estimatedLowStr: String = "",
    val isCalibrated: Boolean = false,
    val calibrationSampleCount: Int = 0
)

data class TradingDecision(
    val signal: SignalType,
    val entryMin: Double,
    val entryMax: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val takeProfit3: Double,
    val riskReward1: Double,
    val riskReward2: Double,
    val riskReward3: Double,
    val invalidationPrice: Double,
    val confidence: Double,
    val reasonsFor: List<String>,
    val reasonsAgainst: List<String>,
    val waitReason: String? = null,
    val positionSizeRecommended: Double = 0.0,
    val entryMinStr: String = "",
    val entryMaxStr: String = "",
    val stopLossStr: String = "",
    val takeProfit1Str: String = "",
    val takeProfit2Str: String = "",
    val takeProfit3Str: String = "",
    val invalidationPriceStr: String = "",
    val historicalWinRate: Double? = null,
    val historicalSampleCount: Int = 0,
    val wilsonIntervalLower: Double = 0.0,
    val wilsonIntervalUpper: Double = 0.0
)

data class UrduExplanation(
    val urduScript: String,
    val romanUrdu: String,
    val english: String,
    val bulletPoints: List<String>
)

data class BreakoutAnalysis(
    val expectedDirection: Direction, // UP, DOWN, or SIDEWAYS (representing NONE)
    val breakoutLevel: Double,
    val breakoutLevelStr: String = "",
    val confirmationCriteria: String = "Close beyond level + Relative Volume >= 1.2",
    val invalidationPrice: Double,
    val invalidationPriceStr: String = "",
    val reasons: List<String>,
    val likelyMinuteWindow: String, // e.g. "Min 2 - 4"
    val isAtrSqueeze: Boolean = false,
    val rangeHigh: Double = 0.0,
    val rangeLow: Double = 0.0,
    val rangeHighStr: String = "",
    val rangeLowStr: String = ""
)

data class MinuteForecastItem(
    val minute: Int,
    val direction: Direction,
    val probPercent: Int,
    val estimatedHigh: Double,
    val estimatedLow: Double,
    val estimatedHighStr: String = "",
    val estimatedLowStr: String = "",
    val isBestEntry: Boolean = false,
    val isAvoid: Boolean = false,
    val note: String = ""
)

data class NextCandleReport(
    val timestamp: Long,
    val symbol: String,
    val marketType: MarketType,
    val minutesTable: List<MinuteForecastItem>,
    val bestEntryMinute: Int,
    val breakout: BreakoutAnalysis,
    val disclaimer: String = "Probability estimate, not a guarantee."
)

data class AltcoinFollowItem(
    val symbol: String,
    val correlation: Double,
    val trend: String, // "FOLLOWS_UP", "FOLLOWS_DOWN", "INDEPENDENT"
    val followsBtc: Boolean
)

data class BtcCorrelationAnalysis(
    val btcTrend: Direction,
    val btcNextCandleDirection: Direction,
    val correlationWithBtc: Double,
    val lagCandles: Int,
    val btcSupportState: String, // "BTC supports active coin" or "BTC opposes active coin"
    val isDowngradedByBtc: Boolean,
    val correlatedAltcoins: List<AltcoinFollowItem>
)

data class UsdPriceTierInfo(
    val tierLabel: String, // "Cheap < $1", "Mid $1-$100", "High > $100"
    val oneCentMovePct: Double,
    val oneCentMovePctFormatted: String
)

data class AutoModeScanResult(
    val strongestSymbol: String,
    val marketType: MarketType,
    val direction: Direction,
    val score: Double,
    val signal: SignalType,
    val lastScanTime: Long,
    val summary: String
)

sealed class DrawingItem(open val id: String, open val symbol: String, open val marketType: MarketType) {
    data class Trendline(
        override val id: String,
        override val symbol: String,
        override val marketType: MarketType,
        val startTimestamp: Long,
        val startPrice: Double,
        val endTimestamp: Long,
        val endPrice: Double
    ) : DrawingItem(id, symbol, marketType)

    data class HorizontalLine(
        override val id: String,
        override val symbol: String,
        override val marketType: MarketType,
        val price: Double,
        val label: String
    ) : DrawingItem(id, symbol, marketType)

    data class Ray(
        override val id: String,
        override val symbol: String,
        override val marketType: MarketType,
        val startTimestamp: Long,
        val startPrice: Double,
        val endTimestamp: Long,
        val endPrice: Double
    ) : DrawingItem(id, symbol, marketType)

    data class RectangleZone(
        override val id: String,
        override val symbol: String,
        override val marketType: MarketType,
        val startTimestamp: Long,
        val startPrice: Double,
        val endTimestamp: Long,
        val endPrice: Double
    ) : DrawingItem(id, symbol, marketType)

    data class SupportResistanceLine(
        override val id: String,
        override val symbol: String,
        override val marketType: MarketType,
        val price: Double,
        val isSupport: Boolean
    ) : DrawingItem(id, symbol, marketType)

    data class Fibonacci(
        override val id: String,
        override val symbol: String,
        override val marketType: MarketType,
        val highPrice: Double,
        val lowPrice: Double
    ) : DrawingItem(id, symbol, marketType) {
        // Levels: 0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0, and extensions 1.272, 1.618
        val diff: Double = highPrice - lowPrice
        val level0: Double = highPrice
        val level236: Double = highPrice - 0.236 * diff
        val level382: Double = highPrice - 0.382 * diff
        val level500: Double = highPrice - 0.500 * diff
        val level618: Double = highPrice - 0.618 * diff
        val level786: Double = highPrice - 0.786 * diff
        val level1000: Double = lowPrice
        val level1272: Double = highPrice - 1.272 * diff
        val level1618: Double = highPrice - 1.618 * diff
    }

    data class LongTool(
        override val id: String,
        override val symbol: String,
        override val marketType: MarketType,
        val entryPrice: Double,
        val stopLoss: Double,
        val takeProfit: Double
    ) : DrawingItem(id, symbol, marketType) {
        val risk: Double = kotlin.math.abs(entryPrice - stopLoss)
        val reward: Double = kotlin.math.abs(takeProfit - entryPrice)
        val riskRewardRatio: Double = if (risk > 0) reward / risk else 0.0
    }

    data class ShortTool(
        override val id: String,
        override val symbol: String,
        override val marketType: MarketType,
        val entryPrice: Double,
        val stopLoss: Double,
        val takeProfit: Double
    ) : DrawingItem(id, symbol, marketType) {
        val risk: Double = kotlin.math.abs(entryPrice - stopLoss)
        val reward: Double = kotlin.math.abs(entryPrice - takeProfit)
        val riskRewardRatio: Double = if (risk > 0) reward / risk else 0.0
    }
}

data class CvdData(
    val cumulativeVolumeDelta: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    val sessionBuyVolume: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    val sessionSellVolume: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    val recentTradeDelta: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    val cvdTrend: Direction = Direction.SIDEWAYS,
    val formattedCvd: String = "0.00"
)

data class LiquidityWall(
    val price: java.math.BigDecimal,
    val formattedPrice: String,
    val volume: java.math.BigDecimal,
    val isBid: Boolean,
    val distancePct: Double,
    val note: String = "liquidity walls are not guaranteed support/resistance (may be spoofed or pulled)"
)

data class OrderBookImbalanceData(
    val imbalancePct: Double = 0.0, // -100.0 to +100.0
    val totalBidVolume: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    val totalAskVolume: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    val bidWalls: List<LiquidityWall> = emptyList(),
    val askWalls: List<LiquidityWall> = emptyList(),
    val isAbsorptionDetected: Boolean = false,
    val absorptionDescription: String = "Normal order flow"
)

data class ProIndicators(
    // Ichimoku Cloud (9, 26, 52)
    val tenkanSen: Double = 0.0,
    val kijunSen: Double = 0.0,
    val senkouSpanA: Double = 0.0,
    val senkouSpanB: Double = 0.0,
    val chikouSpan: Double = 0.0,
    val isPriceAboveCloud: Boolean = false,
    val isPriceBelowCloud: Boolean = false,
    val isBullishTkCross: Boolean = false,

    // Supertrend (10, 3.0)
    val supertrendValue: Double = 0.0,
    val isSupertrendBullish: Boolean = true,

    // OBV
    val obv: Double = 0.0,
    val obvSlope: Double = 0.0,
    val isObvRising: Boolean = true,

    // MFI (14)
    val mfi14: Double = 50.0,
    val isMfiOverbought: Boolean = false,
    val isMfiOversold: Boolean = false,

    // CCI (20)
    val cci20: Double = 0.0,
    val isCciBullish: Boolean = false,
    val isCciBearish: Boolean = false
)

data class MarketContextInfo(
    val fearAndGreedValue: Int = 50,
    val fearAndGreedClassification: String = "Neutral",
    val btcDominancePct: Double = 58.0,
    val ethDominancePct: Double = 14.0,
    val totalMarketCapUsd: String = "$2.65T",
    val unavailableNotice: String = "Whale/on-chain, ETF, news, liquidation heatmaps, Bookmap/ATAS/CoinGlass/CryptoQuant = NOT AVAILABLE (no free API)"
)

data class ConfluenceGroup(
    val name: String,
    val isBullish: Boolean?, // true = Bullish/Long, false = Bearish/Short, null = Neutral
    val score: Int, // 0 to 10
    val summary: String
)

data class ConfluenceReport(
    val groups: List<ConfluenceGroup>,
    val agreeingLongCount: Int,
    val agreeingShortCount: Int,
    val finalSignal: SignalType,
    val bullishPct: Int,
    val bearishPct: Int,
    val confidence: Int,
    val confidenceLabel: String,
    val missingConfluenceReason: String,
    val entryZone: String,
    val stopLoss: String,
    val takeProfit1: String,
    val takeProfit2: String,
    val takeProfit3: String,
    val riskRewardRatio: Double,
    val keySupport: String,
    val keyResistance: String,
    val liquidityTargets: List<String>,
    val invalidationPrice: String,
    val volatilityState: String,
    val reasonsFor: List<String>,
    val reasonsAgainst: List<String>,
    val dataAgeSeconds: Long,
    val missingDataWarnings: List<String>
)
