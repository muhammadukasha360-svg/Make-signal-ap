package com.example.signalx.ui.chart

data class SmcLayerVisibility(
    val showFvg: Boolean = true,
    val showOrderBlocks: Boolean = true,
    val showSupplyDemand: Boolean = true,
    val showStructureBreaks: Boolean = true, // BOS, CHoCH, MSS
    val showLiquidity: Boolean = true,       // Sweeps, EQH, EQL, BSL, SSL
    val showSupportResistance: Boolean = true,
    val showPremiumDiscount: Boolean = true, // 50% Equilibrium & OTE
    val showVolumeProfile: Boolean = true,   // POC, VAH, VAL
    val showSmt: Boolean = true,
    val showEntrySlTp: Boolean = true
)

data class SelectedSmcElement(
    val title: String,
    val direction: String, // "BULLISH", "BEARISH", "NEUTRAL"
    val timestamp: Long,
    val priceRangeStr: String,
    val status: String,
    val confirmationCondition: String,
    val invalidationCondition: String,
    val romanUrduExplanation: String,
    val supportingEvidence: String
)
