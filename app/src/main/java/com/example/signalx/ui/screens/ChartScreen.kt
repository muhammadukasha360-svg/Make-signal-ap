package com.example.signalx.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signalx.data.api.ConnectionStatus
import com.example.signalx.data.api.PriceFormatter
import com.example.signalx.data.model.Direction
import com.example.signalx.data.model.MarketType
import com.example.signalx.data.model.SignalType
import com.example.signalx.data.model.TimeFrame
import com.example.signalx.data.model.TradingDecision
import com.example.signalx.ui.chart.CandlestickChart
import com.example.signalx.ui.viewmodel.UiState
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentYellow
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.StatusLive
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusReconnecting
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun ChartScreen(
    state: UiState,
    onSelectTimeFrame: (TimeFrame) -> Unit,
    onSelectMarketType: (MarketType) -> Unit,
    onOpenForecast: () -> Unit,
    onTakeSignal: () -> Unit,
    onToggleAutoMode: () -> Unit = {},
    onToggleKeepScreenAwake: () -> Unit = {},
    onTradeAtBestMinute: () -> Unit = {},
    onRetryAutoMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showEma by remember { mutableStateOf(true) }
    var showVwap by remember { mutableStateOf(false) }
    var showOverlays by remember { mutableStateOf(true) }
    var smcLayers by remember { mutableStateOf(com.example.signalx.ui.chart.SmcLayerVisibility()) }
    var selectedSmcElement by remember { mutableStateOf<com.example.signalx.ui.chart.SelectedSmcElement?>(null) }
    var showSmcLayerMenu by remember { mutableStateOf(false) }

    val lastPrice = state.latestTicker?.lastPrice ?: state.candles.lastOrNull()?.close ?: 0.0
    val rawLastPrice = state.latestTicker?.rawLastPrice?.ifBlank { null }
    val formattedLastPrice = if (rawLastPrice != null) {
        PriceFormatter.formatPrice(state.activeSymbol, state.activeMarketType, rawLastPrice)
    } else {
        PriceFormatter.formatPrice(state.activeSymbol, state.activeMarketType, lastPrice)
    }
    val change24hPct = state.latestTicker?.change24hPercent ?: 0.0
    val isBull = change24hPct >= 0.0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E11))
    ) {
        // 1. Top Header: Symbol, Spot/Futures, Last Price, 24h Change
        Surface(
            color = Color(0xFF161A1E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Symbol
                    Text(
                        text = state.activeSymbol,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Market Type Badge (Toggleable)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkCard)
                            .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                    ) {
                        Text(
                            text = "SPOT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.activeMarketType == MarketType.SPOT) Color(0xFF0B0E11) else TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (state.activeMarketType == MarketType.SPOT) AccentYellow else Color.Transparent)
                                .clickable { onSelectMarketType(MarketType.SPOT) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Text(
                            text = "FUTURES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.activeMarketType == MarketType.FUTURES) Color(0xFF0B0E11) else TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (state.activeMarketType == MarketType.FUTURES) AccentYellow else Color.Transparent)
                                .clickable { onSelectMarketType(MarketType.FUTURES) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Live Last Price & Change with explicit Spot / Futures label
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (state.activeMarketType == MarketType.SPOT) "Spot Last Price" else "Futures Last Price",
                            fontSize = 9.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = formattedLastPrice,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBull) BullishGreen else BearishRed
                        )
                        Text(
                            text = "${if (isBull) "+" else ""}${String.format(Locale.US, "%.2f", change24hPct)}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isBull) BullishGreen else BearishRed
                        )
                    }
                }

                // If Futures: show separate Futures Last, Mark Price, Index Price, and Spot Last with Basis
                if (state.activeMarketType == MarketType.FUTURES) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val basisPct = if (state.spotComparisonPrice > 0.0) {
                        ((lastPrice - state.spotComparisonPrice) / state.spotComparisonPrice) * 100.0
                    } else 0.0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Mark: ${PriceFormatter.formatPrice(state.activeSymbol, MarketType.FUTURES, state.futuresMarkPrice.takeIf { it > 0 } ?: lastPrice)}",
                            fontSize = 10.sp,
                            color = AccentCyan
                        )
                        Text(
                            text = "Index: ${PriceFormatter.formatPrice(state.activeSymbol, MarketType.FUTURES, state.futuresIndexPrice.takeIf { it > 0 } ?: lastPrice)}",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "Spot: ${PriceFormatter.formatPrice(state.activeSymbol, MarketType.SPOT, state.spotComparisonPrice)}",
                            fontSize = 10.sp,
                            color = AccentYellow
                        )
                        Text(
                            text = "Basis: ${String.format(Locale.US, "%+.2f", basisPct)}%",
                            fontSize = 10.sp,
                            color = if (basisPct >= 0) BullishGreen else BearishRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Status Bar: Connection status, WS / REST tag, data age / STALE feed, PKT time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusColor = when (state.connectionStatus) {
                        ConnectionStatus.LIVE -> StatusLive
                        ConnectionStatus.RECONNECTING -> StatusReconnecting
                        ConnectionStatus.OFFLINE -> StatusOffline
                    }

                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = state.connectionStatus.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Price Source Badge (WS or REST)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (state.priceSource == "REST") AccentOrange.copy(alpha = 0.2f) else DarkCard)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = state.priceSource,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.priceSource == "REST") AccentOrange else TextSecondary
                        )
                    }

                    if (state.isDataStale || state.dataAgeSeconds > 15L) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Stale",
                            tint = BearishRed,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "STALE (${state.dataAgeSeconds}s)",
                            fontSize = 9.sp,
                            color = BearishRed,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "PKT: ${state.pktTime}",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // AUTO Mode, Keep-Awake, USD Price Tier, and BTC Correlation Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // AUTO Mode Toggle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (state.isAutoModeEnabled) AccentYellow else DarkCard)
                            .clickable { onToggleAutoMode() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FlashOn,
                                contentDescription = "Auto",
                                tint = if (state.isAutoModeEnabled) Color(0xFF0B0E11) else TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (state.isAutoModeEnabled) "AUTO ON" else "AUTO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isAutoModeEnabled) Color(0xFF0B0E11) else TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Keep Screen Awake Toggle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (state.keepScreenAwake) AccentCyan.copy(alpha = 0.2f) else DarkCard)
                            .clickable { onToggleKeepScreenAwake() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (state.keepScreenAwake) "AWAKE" else "SLEEP OK",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.keepScreenAwake) AccentCyan else TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // USD Tier & 1-cent move chip
                    state.usdTierInfo?.let { tier ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DarkCardElevated)
                                .padding(horizontal = 5.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${tier.tierLabel} • 1¢=${tier.oneCentMovePctFormatted}",
                                fontSize = 9.sp,
                                color = AccentYellow
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // BTC Correlation State
                    state.btcCorrelation?.let { btc ->
                        val btcColor = when (btc.btcTrend) {
                            Direction.UP -> BullishGreen
                            Direction.DOWN -> BearishRed
                            Direction.SIDEWAYS -> TextSecondary
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (btc.isDowngradedByBtc) BearishRed.copy(alpha = 0.2f) else DarkCard)
                                .padding(horizontal = 5.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "BTC ${btc.btcTrend.name} (r=${String.format(Locale.US, "%.2f", btc.correlationWithBtc)})",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (btc.isDowngradedByBtc) BearishRed else btcColor
                            )
                        }
                    }
                }

                // If AUTO Mode encountered an error, show graceful error banner + Retry
                if (state.isAutoModeEnabled && state.isAutoError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = BearishRed.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = BearishRed, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Scanner issue: ${state.autoErrorMessage ?: "Network/Rate limit"}",
                                fontSize = 9.sp,
                                color = BearishRed,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(BearishRed)
                                    .clickable { onRetryAutoMode() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("RETRY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                // If AUTO Mode has an active scan result, show summary banner
                if (state.isAutoModeEnabled && state.autoScanResult != null && !state.isAutoError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0x33F0B90B),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, tint = AccentYellow, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "AUTO SCAN: Selected ${state.autoScanResult.strongestSymbol} [${state.autoScanResult.signal.name}] • Auto-recomputes every 30s",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentYellow
                            )
                        }
                    }
                }
            }
        }

        // 2. Timeframe Strip
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161A1E))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(TimeFrame.entries) { tf ->
                val isSelected = state.activeTimeFrame == tf
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) AccentYellow else DarkCard)
                        .clickable { onSelectTimeFrame(tf) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tf.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF0B0E11) else TextSecondary
                        )
                        if (tf.isAggregated) {
                            Text(
                                text = "*",
                                fontSize = 10.sp,
                                color = if (isSelected) Color(0xFF0B0E11) else AccentOrange
                            )
                        }
                    }
                }
            }
        }

        // 3. Indicator Toggles Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B0E11))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IndicatorPill("EMA (7/25/99)", showEma, AccentCyan) { showEma = !showEma }
            IndicatorPill("VWAP", showVwap, AccentOrange) { showVwap = !showVwap }
            IndicatorPill("SMC & S/R", showOverlays, AccentYellow) { showOverlays = !showOverlays }
            if (showOverlays) {
                IndicatorPill(
                    if (showSmcLayerMenu) "Hide Layers ▲" else "SMC Layers ▼",
                    showSmcLayerMenu,
                    AccentPurple
                ) { showSmcLayerMenu = !showSmcLayerMenu }
            }
        }

        // 3b. SMC Individual Layer Controls Strip
        if (showOverlays && showSmcLayerMenu) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF13171B))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    IndicatorPill("FVG", smcLayers.showFvg, BullishGreen) {
                        smcLayers = smcLayers.copy(showFvg = !smcLayers.showFvg)
                    }
                }
                item {
                    IndicatorPill("OB", smcLayers.showOrderBlocks, AccentCyan) {
                        smcLayers = smcLayers.copy(showOrderBlocks = !smcLayers.showOrderBlocks)
                    }
                }
                item {
                    IndicatorPill("BOS/CHoCH", smcLayers.showStructureBreaks, AccentYellow) {
                        smcLayers = smcLayers.copy(showStructureBreaks = !smcLayers.showStructureBreaks)
                    }
                }
                item {
                    IndicatorPill("Liquidity", smcLayers.showLiquidity, AccentOrange) {
                        smcLayers = smcLayers.copy(showLiquidity = !smcLayers.showLiquidity)
                    }
                }
                item {
                    IndicatorPill("Eq/OTE", smcLayers.showPremiumDiscount, AccentPurple) {
                        smcLayers = smcLayers.copy(showPremiumDiscount = !smcLayers.showPremiumDiscount)
                    }
                }
                item {
                    IndicatorPill("POC/VP", smcLayers.showVolumeProfile, AccentYellow) {
                        smcLayers = smcLayers.copy(showVolumeProfile = !smcLayers.showVolumeProfile)
                    }
                }
                item {
                    IndicatorPill("SMT", smcLayers.showSmt, AccentCyan) {
                        smcLayers = smcLayers.copy(showSmt = !smcLayers.showSmt)
                    }
                }
                item {
                    IndicatorPill("SL/TP", smcLayers.showEntrySlTp, BullishGreen) {
                        smcLayers = smcLayers.copy(showEntrySlTp = !smcLayers.showEntrySlTp)
                    }
                }
                item {
                    IndicatorPill("Reset All", true, TextMuted) {
                        smcLayers = com.example.signalx.ui.chart.SmcLayerVisibility()
                    }
                }
            }
        }

        // 4. Candlestick Chart Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CandlestickChart(
                candles = state.candles,
                report = state.analysisReport,
                decision = state.decision,
                livePrice = lastPrice,
                drawings = state.drawings,
                symbol = state.activeSymbol,
                marketType = state.activeMarketType,
                showEma = showEma,
                showVwap = showVwap,
                showOverlays = showOverlays,
                smcLayers = smcLayers,
                onElementClick = { selectedSmcElement = it },
                modifier = Modifier.fillMaxSize()
            )

            // Interactive SMC Detail Modal overlay
            selectedSmcElement?.let { element ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x88000000))
                        .clickable { selectedSmcElement = null }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.clickable(enabled = false) {}) {
                        com.example.signalx.ui.components.SmcElementDetailModal(
                            element = element,
                            onDismiss = { selectedSmcElement = null }
                        )
                    }
                }
            }
        }

        // 5. Order Flow & Confluence Indicator Panel
        com.example.signalx.ui.components.OrderFlowPanel(
            cvdData = state.cvdData,
            orderBookImbalance = state.orderBookImbalance,
            proIndicators = state.proIndicators,
            marketContext = state.marketContext,
            confluenceReport = state.confluenceReport,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )

        // 6. Mandatory Live Candle Countdown Bar
        LiveCountdownBar(
            timeFrame = state.activeTimeFrame,
            formattedCountdown = state.formattedCountdown,
            remainingCountdownMs = state.remainingCountdownMs,
            isPreparationMode = state.isPreparationMode,
            progressFraction = state.candleProgressFraction,
            forecast = state.forecasts.firstOrNull(),
            symbol = state.activeSymbol,
            marketType = state.activeMarketType,
            breakout = state.analysisReport?.breakoutAnalysis,
            nextCandleReport = state.nextCandleReport,
            onTradeAtBestMinute = onTradeAtBestMinute
        )

        // 6. Quick Decision & Action Banner
        state.decision?.let { dec ->
            DecisionBanner(
                decision = dec,
                symbol = state.activeSymbol,
                marketType = state.activeMarketType,
                onOpenForecast = onOpenForecast,
                onTakeSignal = onTakeSignal
            )
        }
    }
}

@Composable
fun LiveCountdownBar(
    timeFrame: TimeFrame,
    formattedCountdown: String,
    remainingCountdownMs: Long,
    isPreparationMode: Boolean,
    progressFraction: Float,
    forecast: com.example.signalx.data.model.ForecastResult?,
    symbol: String = "BTCUSDT",
    marketType: MarketType = MarketType.SPOT,
    breakout: com.example.signalx.data.model.BreakoutAnalysis? = null,
    nextCandleReport: com.example.signalx.data.model.NextCandleReport? = null,
    onTradeAtBestMinute: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isPreparationMode) Color(0x33F0B90B) else Color(0xFF161A1E))
            .border(
                width = if (isPreparationMode) 1.dp else 0.dp,
                color = if (isPreparationMode) AccentYellow else Color.Transparent
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = "Timer",
                tint = if (isPreparationMode) AccentYellow else TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${timeFrame.label}: ",
                fontSize = 11.sp,
                color = TextSecondary
            )
            Text(
                text = formattedCountdown,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPreparationMode) AccentYellow else TextPrimary
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Beside Countdown: Breakout expected: UP / DOWN / NONE
            if (breakout != null) {
                val dirLabel = when (breakout.expectedDirection) {
                    Direction.UP -> "UP"
                    Direction.DOWN -> "DOWN"
                    Direction.SIDEWAYS -> "NONE"
                }
                val dirColor = when (breakout.expectedDirection) {
                    Direction.UP -> BullishGreen
                    Direction.DOWN -> BearishRed
                    Direction.SIDEWAYS -> TextSecondary
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text("Breakout: ", fontSize = 10.sp, color = TextMuted)
                    Text(dirLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = dirColor)
                    if (breakout.expectedDirection != Direction.SIDEWAYS) {
                        Text(" @ ${breakout.breakoutLevelStr}", fontSize = 10.sp, color = TextPrimary)
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (isPreparationMode) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AGLI CANDLE PREPARATION",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentYellow
                )
            }
        }

        // Breakout detail sub-line if active
        if (breakout != null && breakout.expectedDirection != Direction.SIDEWAYS) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Breakout Conf: ${breakout.confirmationCriteria}  Inv: ${breakout.invalidationPriceStr} (${breakout.likelyMinuteWindow})",
                fontSize = 9.sp,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Progress indicator of candle
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (isPreparationMode) AccentYellow else BullishGreen,
            trackColor = DarkCardElevated
        )

        // Show Next Candle Report Card at 60 seconds remaining or preparation mode
        val showNextCandleReport = (remainingCountdownMs in 1L..60_000L) || isPreparationMode
        if (showNextCandleReport && nextCandleReport != null) {
            Spacer(modifier = Modifier.height(6.dp))
            NextCandleReportCard(
                report = nextCandleReport,
                onTradeAtBestMinute = onTradeAtBestMinute
            )
        }
    }
}

@Composable
fun NextCandleReportCard(
    report: com.example.signalx.data.model.NextCandleReport,
    onTradeAtBestMinute: () -> Unit
) {
    Surface(
        color = Color(0xFF1E2328),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentYellow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = null, tint = AccentYellow, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "NEXT CANDLE REPORT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentYellow
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onTradeAtBestMinute,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentYellow),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(
                        text = "Trade Best Min (${report.bestEntryMinute}m)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B0E11)
                    )
                }
            }

            Text(
                text = report.disclaimer,
                fontSize = 9.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Minute-by-Minute Table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF13171B), RoundedCornerShape(4.dp))
                    .padding(6.dp)
            ) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Min", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(0.7f))
                    Text("Direction", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(1.3f))
                    Text("Est Range", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(2.0f))
                    Text("Edge Tag", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(1.6f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }

                // Table Rows
                report.minutesTable.take(10).forEach { item ->
                    val (dirText, dirColor) = when (item.direction) {
                        Direction.UP -> Pair("▲ UP ${item.probPercent}%", BullishGreen)
                        Direction.DOWN -> Pair("▼ DN ${item.probPercent}%", BearishRed)
                        Direction.SIDEWAYS -> Pair("↔ SIDE ${item.probPercent}%", TextSecondary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${item.minute}m", fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.7f))
                        Text(dirText, fontSize = 10.sp, color = dirColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                        Text("${item.estimatedLowStr} - ${item.estimatedHighStr}", fontSize = 9.sp, color = TextSecondary, modifier = Modifier.weight(2.0f))

                        Box(modifier = Modifier.weight(1.6f), contentAlignment = Alignment.CenterEnd) {
                            if (item.isBestEntry) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0x330ECB81))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("BEST ENTRY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = BullishGreen)
                                }
                            } else if (item.isAvoid) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0x33F6465D))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("AVOID", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = BearishRed)
                                }
                            } else {
                                Text("-", fontSize = 9.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DecisionBanner(
    decision: TradingDecision,
    symbol: String = "BTCUSDT",
    marketType: MarketType = MarketType.SPOT,
    onOpenForecast: () -> Unit,
    onTakeSignal: () -> Unit
) {
    val (signalColor, signalBg) = when (decision.signal) {
        SignalType.STRONG_LONG, SignalType.LONG -> Pair(BullishGreen, Color(0x220ECB81))
        SignalType.STRONG_SHORT, SignalType.SHORT -> Pair(BearishRed, Color(0x22F6465D))
        SignalType.WAIT -> Pair(AccentYellow, Color(0x22F0B90B))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Signal Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(signalBg)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = decision.signal.name.replace("_", " "),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = signalColor
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Signal quick numbers formatted with PriceFormatter
        Column(modifier = Modifier.weight(1f)) {
            if (decision.signal != SignalType.WAIT) {
                val slStr = PriceFormatter.formatPrice(symbol, marketType, decision.stopLoss)
                val tp1Str = PriceFormatter.formatPrice(symbol, marketType, decision.takeProfit1)
                Text(
                    text = "SL: $slStr  TP1: $tp1Str",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "R:R: 1:${String.format(Locale.US, "%.1f", decision.riskReward1)}  Conf: ${decision.confidence.toInt()}%",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            } else {
                Text(
                    text = decision.waitReason ?: "Waiting for confirmation",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }

        // Action Buttons
        if (decision.signal != SignalType.WAIT) {
            Button(
                onClick = onTakeSignal,
                colors = ButtonDefaults.buttonColors(containerColor = AccentYellow),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = "Trade", tint = Color(0xFF0B0E11), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Trade", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0B0E11))
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Button(
            onClick = onOpenForecast,
            colors = ButtonDefaults.buttonColors(containerColor = DarkCardElevated),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(Icons.Default.AutoGraph, contentDescription = "Forecast", tint = AccentCyan, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("AI Plan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
        }
    }
}

@Composable
fun IndicatorPill(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) activeColor.copy(alpha = 0.2f) else DarkCard)
            .border(1.dp, if (isActive) activeColor else DarkBorder, RoundedCornerShape(4.dp))
            .clickable { onToggle() }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) activeColor else TextSecondary
        )
    }
}
