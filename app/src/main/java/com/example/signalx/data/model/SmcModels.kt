package com.example.signalx.data.model

enum class FvgType {
    BULLISH,
    BEARISH,
    INVERSE_FVG, // IFVG: broken through and flipped from support to resistance or vice versa
    BALANCED_PRICE_RANGE // BPR: overlapping bullish and bearish FVGs
}

enum class FvgStatus {
    UNTESTED,
    TESTED,
    PARTIALLY_FILLED,
    FULLY_FILLED,
    MITIGATED,
    INVALIDATED
}

data class EnhancedFvg(
    val id: String,
    val type: FvgType,
    val top: Double,
    val bottom: Double,
    val midpoint: Double = (top + bottom) / 2.0,
    val candleIndex: Int,
    val timestamp: Long,
    val status: FvgStatus,
    val fillPercent: Double, // 0.0 to 100.0%
    val ageCandles: Int,
    val isDisplacementFvg: Boolean = false,
    val timeframe: String = "15m"
)

enum class OrderBlockType {
    BULLISH_OB,
    BEARISH_OB,
    BREAKER_BLOCK,
    MITIGATION_BLOCK,
    REJECTION_BLOCK
}

data class EnhancedOrderBlock(
    val id: String,
    val type: OrderBlockType,
    val top: Double,
    val bottom: Double,
    val midpoint: Double = (top + bottom) / 2.0,
    val originCandleIndex: Int,
    val timestamp: Long,
    val volume: Double,
    val displacementStrength: Double, // % price displacement away
    val isRetested: Boolean,
    val isInvalidated: Boolean,
    val isStrong: Boolean,
    val timeframe: String = "15m"
)

enum class StructureBreakType {
    BOS,   // Break of Structure in trend continuation
    CHOCH, // Change of Character (first sign of trend change)
    MSS    // Market Structure Shift (displacement + break)
}

data class EnhancedStructureBreak(
    val type: StructureBreakType,
    val isBullish: Boolean,
    val breakPrice: Double,
    val confirmedByClose: Boolean, // true if closed beyond, false if wick only
    val candleIndex: Int,
    val timestamp: Long,
    val isInternal: Boolean = false // Internal vs External swing
)

enum class LiquidityType {
    EQUAL_HIGHS,
    EQUAL_LOWS,
    BUY_SIDE_LIQUIDITY,  // BSL
    SELL_SIDE_LIQUIDITY, // SSL
    LIQUIDITY_SWEEP,     // Wick penetration & close back inside
    STOP_HUNT,
    INDUCEMENT
}

data class EnhancedLiquidity(
    val type: LiquidityType,
    val price: Double,
    val sweepPrice: Double = 0.0,
    val isHigh: Boolean,
    val isConfirmedReclaim: Boolean = false,
    val isSweep: Boolean = false,
    val timestamp: Long = 0L,
    val label: String
)

data class PremiumDiscountRange(
    val dealingRangeHigh: Double,
    val dealingRangeLow: Double,
    val equilibrium50: Double, // 50% level
    val oteUpper618: Double,   // 61.8% fib level
    val oteLower786: Double,   // 78.6% fib level
    val currentZone: String    // "PREMIUM", "DISCOUNT", or "EQUILIBRIUM"
)

data class SmtDivergence(
    val isDetected: Boolean,
    val isBullish: Boolean,
    val primarySymbol: String,
    val comparisonSymbol: String,
    val description: String,
    val timestamp: Long
)

data class SmcAnalysis(
    val fvgs: List<EnhancedFvg> = emptyList(),
    val orderBlocks: List<EnhancedOrderBlock> = emptyList(),
    val structureBreaks: List<EnhancedStructureBreak> = emptyList(),
    val liquidities: List<EnhancedLiquidity> = emptyList(),
    val premiumDiscount: PremiumDiscountRange? = null,
    val smtDivergence: SmtDivergence? = null,
    val institutionalMomentum: String = "NEUTRAL",
    val consolidationZoneHigh: Double = 0.0,
    val consolidationZoneLow: Double = 0.0,
    val isConsolidation: Boolean = false
)
