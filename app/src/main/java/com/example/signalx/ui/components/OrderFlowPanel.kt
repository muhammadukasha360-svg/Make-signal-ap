package com.example.signalx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signalx.data.model.ConfluenceReport
import com.example.signalx.data.model.CvdData
import com.example.signalx.data.model.Direction
import com.example.signalx.data.model.MarketContextInfo
import com.example.signalx.data.model.OrderBookImbalanceData
import com.example.signalx.data.model.ProIndicators
import com.example.signalx.data.model.SignalType
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AccentYellow
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun OrderFlowPanel(
    cvdData: CvdData?,
    orderBookImbalance: OrderBookImbalanceData?,
    proIndicators: ProIndicators?,
    marketContext: MarketContextInfo?,
    confluenceReport: ConfluenceReport?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF12161A),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header with toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ORDER FLOW & PRO CONFLUENCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                confluenceReport?.let { cr ->
                    Spacer(modifier = Modifier.width(6.dp))
                    val badgeBg = when (cr.finalSignal) {
                        SignalType.STRONG_LONG, SignalType.LONG -> BullishGreen.copy(alpha = 0.2f)
                        SignalType.STRONG_SHORT, SignalType.SHORT -> BearishRed.copy(alpha = 0.2f)
                        else -> DarkCard
                    }
                    val badgeColor = when (cr.finalSignal) {
                        SignalType.STRONG_LONG, SignalType.LONG -> BullishGreen
                        SignalType.STRONG_SHORT, SignalType.SHORT -> BearishRed
                        else -> AccentYellow
                    }
                    val count = if (cr.finalSignal.name.contains("LONG")) cr.agreeingLongCount else if (cr.finalSignal.name.contains("SHORT")) cr.agreeingShortCount else 0
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${cr.finalSignal.name} ($count/6)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (expanded) "HIDE ▲" else "DETAILS ▼",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Stats Row: CVD, Book Imbalance %, Absorption
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // CVD Metric
                val cvdVal = cvdData?.cumulativeVolumeDelta?.toDouble() ?: 0.0
                val cvdColor = if (cvdVal >= 0.0) BullishGreen else BearishRed
                val cvdTrend = cvdData?.cvdTrend ?: Direction.SIDEWAYS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkCard)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("CVD Delta", fontSize = 9.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.weight(1f))
                            if (cvdTrend == Direction.UP) {
                                Icon(Icons.Default.TrendingUp, null, tint = BullishGreen, modifier = Modifier.size(10.dp))
                            } else if (cvdTrend == Direction.DOWN) {
                                Icon(Icons.Default.TrendingDown, null, tint = BearishRed, modifier = Modifier.size(10.dp))
                            }
                        }
                        Text(
                            text = cvdData?.formattedCvd ?: String.format(Locale.US, "%+.2f", cvdVal),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = cvdColor
                        )
                    }
                }

                // Order Book Imbalance Metric
                val imbPct = orderBookImbalance?.imbalancePct ?: 0.0
                val imbSide = if (imbPct > 0) "BIDS" else if (imbPct < 0) "ASKS" else "NEUTRAL"
                val imbColor = if (imbSide == "BIDS") BullishGreen else if (imbSide == "ASKS") BearishRed else TextSecondary
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkCard)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Column {
                        Text("Book Imbalance", fontSize = 9.sp, color = TextSecondary)
                        Text(
                            text = String.format(Locale.US, "%s %+.1f%%", imbSide, imbPct),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = imbColor
                        )
                    }
                }

                // Absorption / Wall Alert Metric
                val isAbsorbing = orderBookImbalance?.isAbsorptionDetected == true
                val absDesc = orderBookImbalance?.absorptionDescription ?: "Normal flow"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isAbsorbing) AccentOrange.copy(alpha = 0.2f) else DarkCard)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Column {
                        Text("Absorption", fontSize = 9.sp, color = TextSecondary)
                        Text(
                            text = if (isAbsorbing) "DETECTED" else "Normal Flow",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAbsorbing) AccentOrange else TextSecondary
                        )
                    }
                }
            }

            // Expanded Section: Confluence Breakdown, Technical Indicators, Liquidity Walls, and Disclaimer
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    // 6 Groups Breakdown
                    confluenceReport?.let { cr ->
                        Text(
                            text = "Confluence 6-Group Breakdown:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        cr.groups.chunked(2).forEach { rowGroups ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowGroups.forEach { grp ->
                                    val gColor = when (grp.isBullish) {
                                        true -> BullishGreen
                                        false -> BearishRed
                                        null -> TextSecondary
                                    }
                                    val gText = when (grp.isBullish) {
                                        true -> "BULLISH"
                                        false -> "BEARISH"
                                        null -> "NEUTRAL"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(DarkCard)
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(grp.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                                Spacer(modifier = Modifier.weight(1f))
                                                Text(gText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = gColor)
                                            }
                                            Text(grp.summary, fontSize = 8.sp, color = TextMuted, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        if (cr.missingDataWarnings.isNotEmpty()) {
                            Text(
                                text = "Warnings: " + cr.missingDataWarnings.joinToString("; "),
                                fontSize = 8.sp,
                                color = AccentOrange
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // Indicators Sub-row: Ichimoku, Supertrend, OBV, MFI, CCI
                    proIndicators?.let { pi ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Technical Indicators:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IndicatorBadge("Ichimoku", if (pi.isPriceAboveCloud) "BULL CLOUD" else if (pi.isPriceBelowCloud) "BEAR CLOUD" else "IN CLOUD", if (pi.isPriceAboveCloud) BullishGreen else if (pi.isPriceBelowCloud) BearishRed else TextSecondary)
                            IndicatorBadge("Supertrend", if (pi.isSupertrendBullish) "BULLISH" else "BEARISH", if (pi.isSupertrendBullish) BullishGreen else BearishRed)
                            IndicatorBadge("MFI(14)", String.format(Locale.US, "%.1f", pi.mfi14), if (pi.isMfiOverbought) BearishRed else if (pi.isMfiOversold) BullishGreen else TextSecondary)
                            IndicatorBadge("CCI(20)", String.format(Locale.US, "%.0f", pi.cci20), if (pi.isCciBullish) BullishGreen else if (pi.isCciBearish) BearishRed else TextSecondary)
                        }
                    }

                    // Context Sub-row: Fear & Greed, BTC Dominance
                    marketContext?.let { mc ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Market Context: Fear & Greed: ${mc.fearAndGreedValue} (${mc.fearAndGreedClassification}) • BTC Dom: ${String.format(Locale.US, "%.1f%%", mc.btcDominancePct)} • MCap: ${mc.totalMarketCapUsd}",
                                fontSize = 8.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Non-free API transparency note
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Whale / On-chain / Liquidation heatmaps: NOT AVAILABLE (no free API).",
                        fontSize = 8.sp,
                        color = TextMuted
                    )

                    // Liquidity Walls & Mandatory Disclaimer
                    val bidWalls = orderBookImbalance?.bidWalls ?: emptyList()
                    val askWalls = orderBookImbalance?.askWalls ?: emptyList()
                    val allWalls = bidWalls + askWalls
                    if (allWalls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Resting Liquidity Walls (Order Book Depth):",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        allWalls.take(4).forEach { wall ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${if (wall.isBid) "BID" else "ASK"} Wall @ ${wall.formattedPrice}",
                                    fontSize = 8.sp,
                                    color = if (wall.isBid) BullishGreen else BearishRed
                                )
                                Text(
                                    text = "${wall.volume.toPlainString()} coins (${String.format(Locale.US, "%.1f%% dist", wall.distancePct)})",
                                    fontSize = 8.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // Mandatory Disclaimer
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Disclaimer: Liquidity walls are not guaranteed support/resistance (may be spoofed or pulled).",
                        fontSize = 8.sp,
                        fontStyle = FontStyle.Italic,
                        color = AccentOrange
                    )
                }
            }
        }
    }
}

@Composable
private fun IndicatorBadge(name: String, value: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(DarkCard)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 7.sp, color = TextMuted)
            Text(value, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
