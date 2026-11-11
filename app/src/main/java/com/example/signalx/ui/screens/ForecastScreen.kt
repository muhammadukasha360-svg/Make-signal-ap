package com.example.signalx.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signalx.data.api.PriceFormatter
import com.example.signalx.data.model.Direction
import com.example.signalx.data.model.Language
import com.example.signalx.data.model.MarketType
import com.example.signalx.data.model.SignalType
import com.example.signalx.engine.forecast.ForecastEngine
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun ForecastScreen(
    state: UiState,
    onSelectHorizon: (Int) -> Unit,
    onSelectLanguage: (Language) -> Unit,
    onTakeSignal: () -> Unit,
    onTradeAtBestMinute: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val activeHorizon = state.selectedHorizonMinutes
    val currentForecast = state.forecasts.firstOrNull { it.horizonMinutes == activeHorizon }
        ?: state.forecasts.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E11))
            .verticalScroll(scrollState)
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${state.activeSymbol} AI Prediction",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Quant Evidence Engine + Statistical Calibration",
                    fontSize = 11.sp,
                    color = AccentCyan
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkCard)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = state.activeMarketType.name,
                    color = AccentYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // USD Price Tier and 1-cent move chip
        state.usdTierInfo?.let { tier ->
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkCardElevated)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${tier.tierLabel} • Price of 1¢ Move: ${tier.oneCentMovePctFormatted}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentYellow
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 14 Horizons Selector
        Text("Forecast Horizon (Minutes)", fontSize = 12.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(ForecastEngine.HORIZONS) { horizon ->
                val isSelected = horizon == activeHorizon
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) AccentYellow else DarkCard)
                        .border(1.dp, if (isSelected) AccentYellow else DarkBorder, RoundedCornerShape(6.dp))
                        .clickable { onSelectHorizon(horizon) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${horizon}m",
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFF0B0E11) else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Selected Forecast Overview Card
        if (currentForecast != null) {
            ForecastDetailsCard(
                forecast = currentForecast,
                symbol = state.activeSymbol,
                marketType = state.activeMarketType
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkCard)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Analyzing candle history...", color = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Language Switcher & Urdu Explanation Box
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Language",
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI Market Breakdown",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    // Language toggles
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkCardElevated)
                    ) {
                        LanguageChip("اردو", state.language == Language.URDU_SCRIPT) { onSelectLanguage(Language.URDU_SCRIPT) }
                        LanguageChip("Roman", state.language == Language.ROMAN_URDU) { onSelectLanguage(Language.ROMAN_URDU) }
                        LanguageChip("English", state.language == Language.ENGLISH) { onSelectLanguage(Language.ENGLISH) }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                state.explanation?.let { exp ->
                    val textToShow = when (state.language) {
                        Language.URDU_SCRIPT -> exp.urduScript
                        Language.ROMAN_URDU -> exp.romanUrdu
                        Language.ENGLISH -> exp.english
                    }

                    Text(
                        text = textToShow,
                        fontSize = if (state.language == Language.URDU_SCRIPT) 15.sp else 13.sp,
                        lineHeight = if (state.language == Language.URDU_SCRIPT) 24.sp else 19.sp,
                        color = TextPrimary,
                        textAlign = if (state.language == Language.URDU_SCRIPT) TextAlign.Right else TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Trading Decision Box with Entry / SL / TP
        state.decision?.let { dec ->
            TradingDecisionDetailsCard(
                decision = dec,
                symbol = state.activeSymbol,
                marketType = state.activeMarketType,
                onTakeSignal = onTakeSignal
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Evidence & Supporting Indicators List
        currentForecast?.let { f ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Quantitative Evidence Breakdown (${f.evidence.size} Factors)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    f.evidence.forEach { item ->
                        val (dirColor, dirIcon) = when (item.direction) {
                            Direction.UP -> Pair(BullishGreen, Icons.Default.ArrowUpward)
                            Direction.DOWN -> Pair(BearishRed, Icons.Default.ArrowDownward)
                            Direction.SIDEWAYS -> Pair(AccentYellow, Icons.Default.ArrowForward)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(dirIcon, contentDescription = null, tint = dirColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(item.description, fontSize = 10.sp, color = TextSecondary)
                            }
                            Text(
                                text = "${item.direction.name} (+${item.weight.toInt()})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = dirColor
                            )
                        }
                    }
                }
            }
        }
        // Next Candle Minute-by-Minute Forecast Card
        state.nextCandleReport?.let { nextReport ->
            Spacer(modifier = Modifier.height(14.dp))
            ForecastNextCandleCard(
                report = nextReport,
                onTradeAtBestMinute = onTradeAtBestMinute
            )
        }

        // Breakout and 200-Candle Reading Card
        state.analysisReport?.breakoutAnalysis?.let { breakout ->
            Spacer(modifier = Modifier.height(14.dp))
            ForecastBreakoutCard(breakout = breakout)
        }

        // BTC Link and Altcoin Follow Card
        state.btcCorrelation?.let { btc ->
            Spacer(modifier = Modifier.height(14.dp))
            BtcLinkCard(btc = btc, activeSymbol = state.activeSymbol)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun ForecastDetailsCard(
    forecast: com.example.signalx.data.model.ForecastResult,
    symbol: String = "BTCUSDT",
    marketType: MarketType = MarketType.SPOT
) {
    val (dirColor, dirBg) = when (forecast.direction) {
        Direction.UP -> Pair(BullishGreen, Color(0x220ECB81))
        Direction.DOWN -> Pair(BearishRed, Color(0x22F6465D))
        Direction.SIDEWAYS -> Pair(AccentYellow, Color(0x22F0B90B))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Direction & Horizon Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(dirBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "FORECAST: ${forecast.direction.name}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = dirColor
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Calibration Status Tag
                val isCalib = forecast.calibrationSampleCount >= 100
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isCalib) BullishGreen.copy(alpha = 0.2f) else AccentOrange.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isCalib) "Calibrated (n=${forecast.calibrationSampleCount})" else "uncalibrated (n=${forecast.calibrationSampleCount})",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCalib) BullishGreen else AccentOrange
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "${forecast.horizonMinutes}m",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentCyan
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Probabilities Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("UP: ${forecast.probUp.toInt()}%", fontSize = 11.sp, color = BullishGreen, fontWeight = FontWeight.Bold)
                Text("DOWN: ${forecast.probDown.toInt()}%", fontSize = 11.sp, color = BearishRed, fontWeight = FontWeight.Bold)
                Text("SIDEWAYS: ${forecast.probSideways.toInt()}%", fontSize = 11.sp, color = AccentYellow, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                Box(modifier = Modifier.weight((forecast.probUp).coerceAtLeast(1.0).toFloat()).fillMaxSize().background(BullishGreen))
                Box(modifier = Modifier.weight((forecast.probDown).coerceAtLeast(1.0).toFloat()).fillMaxSize().background(BearishRed))
                Box(modifier = Modifier.weight((forecast.probSideways).coerceAtLeast(1.0).toFloat()).fillMaxSize().background(AccentYellow))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Price Range (HIGH / LOW) formatted via PriceFormatter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ESTIMATED LOW", fontSize = 10.sp, color = TextMuted)
                    Text(PriceFormatter.formatPrice(symbol, marketType, forecast.estimatedLow), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BearishRed)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("EXP MOVE", fontSize = 10.sp, color = TextMuted)
                    Text("±${String.format(Locale.US, "%.2f", forecast.expectedPctMove)}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentYellow)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("ESTIMATED HIGH", fontSize = 10.sp, color = TextMuted)
                    Text(PriceFormatter.formatPrice(symbol, marketType, forecast.estimatedHigh), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BullishGreen)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Confidence & Momentum
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Confidence: ${forecast.confidence.toInt()}%",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Momentum: ${if (forecast.momentumScore > 0) "+" else ""}${forecast.momentumScore.toInt()}",
                    fontSize = 11.sp,
                    color = if (forecast.momentumScore >= 0) BullishGreen else BearishRed,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TradingDecisionDetailsCard(
    decision: com.example.signalx.data.model.TradingDecision,
    symbol: String = "BTCUSDT",
    marketType: MarketType = MarketType.SPOT,
    onTakeSignal: () -> Unit
) {
    val (signalColor, signalBg) = when (decision.signal) {
        SignalType.STRONG_LONG, SignalType.LONG -> Pair(BullishGreen, Color(0x220ECB81))
        SignalType.STRONG_SHORT, SignalType.SHORT -> Pair(BearishRed, Color(0x22F6465D))
        SignalType.WAIT -> Pair(AccentYellow, Color(0x22F0B90B))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(signalBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "DECISION: ${decision.signal.name.replace("_", " ")}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = signalColor
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (decision.signal != SignalType.WAIT) {
                    Button(
                        onClick = onTakeSignal,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentYellow),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFF0B0E11), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Take Signal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0B0E11))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (decision.signal != SignalType.WAIT) {
                val entryMinStr = PriceFormatter.formatPrice(symbol, marketType, decision.entryMin)
                val entryMaxStr = PriceFormatter.formatPrice(symbol, marketType, decision.entryMax)
                val slStr = PriceFormatter.formatPrice(symbol, marketType, decision.stopLoss)
                val tp1Str = PriceFormatter.formatPrice(symbol, marketType, decision.takeProfit1)
                val tp2Str = PriceFormatter.formatPrice(symbol, marketType, decision.takeProfit2)
                val tp3Str = PriceFormatter.formatPrice(symbol, marketType, decision.takeProfit3)
                val invStr = PriceFormatter.formatPrice(symbol, marketType, decision.invalidationPrice)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("ENTRY ZONE", fontSize = 10.sp, color = TextMuted)
                        Text("$entryMinStr - $entryMaxStr", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("STOP LOSS", fontSize = 10.sp, color = TextMuted)
                        Text(slStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BearishRed)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("TP1 (1:1.5)", fontSize = 10.sp, color = TextMuted)
                        Text(tp1Str, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BullishGreen)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TP2 (1:2.5)", fontSize = 10.sp, color = TextMuted)
                        Text(tp2Str, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BullishGreen)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TP3 (1:4.0)", fontSize = 10.sp, color = TextMuted)
                        Text(tp3Str, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BullishGreen)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Thesis Invalidation Price: $invStr",
                    fontSize = 11.sp,
                    color = AccentCyan
                )

                if (decision.historicalSampleCount > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val calibStatus = if (decision.historicalSampleCount >= 100) "Calibrated" else "uncalibrated (n < 100)"
                    val winPct = (decision.historicalWinRate ?: 0.0) * 100.0
                    val lowerPct = decision.wilsonIntervalLower * 100.0
                    val upperPct = decision.wilsonIntervalUpper * 100.0
                    Text(
                        text = "Historical Win Rate: ${String.format(Locale.US, "%.1f", winPct)}% | Wilson 95% CI: [${String.format(Locale.US, "%.1f", lowerPct)}% - ${String.format(Locale.US, "%.1f", upperPct)}%] (n=${decision.historicalSampleCount}, $calibStatus)",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AccentYellow, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = decision.waitReason ?: "Waiting for >= 4 confirmation groups",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageChip(label: String, isSelected: Boolean, onSelect: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) AccentCyan else Color.Transparent)
            .clickable { onSelect() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF0B0E11) else TextSecondary
        )
    }
}

@Composable
fun ForecastNextCandleCard(
    report: com.example.signalx.data.model.NextCandleReport,
    onTradeAtBestMinute: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentYellow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = null, tint = AccentYellow, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "NEXT CANDLE REPORT (Minute-by-Minute)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentYellow
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onTradeAtBestMinute,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentYellow),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
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

            Spacer(modifier = Modifier.height(8.dp))

            // Minute-by-Minute Table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF13171B), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Minute", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(0.7f))
                    Text("Direction", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(1.3f))
                    Text("Estimated High - Low", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(2.0f))
                    Text("Edge Tag", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End)
                }

                report.minutesTable.forEach { item ->
                    val (dirText, dirColor) = when (item.direction) {
                        Direction.UP -> Pair("▲ UP ${item.probPercent}%", BullishGreen)
                        Direction.DOWN -> Pair("▼ DN ${item.probPercent}%", BearishRed)
                        Direction.SIDEWAYS -> Pair("↔ SIDE ${item.probPercent}%", TextSecondary)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${item.minute}m", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.7f))
                        Text(dirText, fontSize = 11.sp, color = dirColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f))
                        Text("${item.estimatedLowStr} - ${item.estimatedHighStr}", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.weight(2.0f))

                        Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.CenterEnd) {
                            if (item.isBestEntry) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0x330ECB81))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("BEST ENTRY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = BullishGreen)
                                }
                            } else if (item.isAvoid) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0x33F6465D))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
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
fun ForecastBreakoutCard(breakout: com.example.signalx.data.model.BreakoutAnalysis) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val (dirText, dirColor) = when (breakout.expectedDirection) {
                Direction.UP -> Pair("UP BREAKOUT EXPECTED", BullishGreen)
                Direction.DOWN -> Pair("DOWN BREAKOUT EXPECTED", BearishRed)
                Direction.SIDEWAYS -> Pair("NO BREAKOUT (CONSOLIDATING)", TextSecondary)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Breakout & Structure Reading (200 Candles)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (breakout.isAtrSqueeze) AccentOrange.copy(alpha = 0.2f) else DarkCardElevated)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (breakout.isAtrSqueeze) "ATR SQUEEZE" else "NORMAL VOL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (breakout.isAtrSqueeze) AccentOrange else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = dirText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = dirColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Breakout Level, Timing, Invalidation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("BREAKOUT LEVEL", fontSize = 9.sp, color = TextMuted)
                    Text(breakout.breakoutLevelStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TIMING WINDOW", fontSize = 9.sp, color = TextMuted)
                    Text(breakout.likelyMinuteWindow, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("INVALIDATION PRICE", fontSize = 9.sp, color = TextMuted)
                    Text(breakout.invalidationPriceStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BearishRed)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Confirmation: ${breakout.confirmationCriteria}",
                fontSize = 10.sp,
                color = AccentYellow
            )

            Text(
                text = "20-Candle Range: ${breakout.rangeLowStr} - ${breakout.rangeHighStr}",
                fontSize = 10.sp,
                color = TextSecondary
            )

            if (breakout.reasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                breakout.reasons.forEach { r ->
                    Text("• $r", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun BtcLinkCard(btc: com.example.signalx.data.model.BtcCorrelationAnalysis, activeSymbol: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BTC Link & Altcoin Follow (50 Candles)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (btc.isDowngradedByBtc) BearishRed.copy(alpha = 0.2f) else BullishGreen.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (btc.isDowngradedByBtc) "BTC OPPOSES (DOWNGRADED)" else "BTC ALIGNED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (btc.isDowngradedByBtc) BearishRed else BullishGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // BTC Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("BTC TREND", fontSize = 9.sp, color = TextMuted)
                    Text(btc.btcTrend.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (btc.btcTrend == Direction.UP) BullishGreen else BearishRed)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("BTC NEXT CANDLE", fontSize = 9.sp, color = TextMuted)
                    Text(btc.btcNextCandleDirection.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (btc.btcNextCandleDirection == Direction.UP) BullishGreen else BearishRed)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("CORRELATION / LAG", fontSize = 9.sp, color = TextMuted)
                    Text("r=${String.format(Locale.US, "%.2f", btc.correlationWithBtc)} (lag ${btc.lagCandles})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = btc.btcSupportState,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (btc.isDowngradedByBtc) BearishRed else BullishGreen
            )

            // Correlated Altcoins Table (corr > 0.6)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Key Altcoins Following BTC (XRP, ETH, SOL, DOGE, ADA, BNB, LINK):",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                btc.correlatedAltcoins.take(4).forEach { alt ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF161A1E))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${alt.symbol.replace("USDT", "")}: r=${String.format(Locale.US, "%.2f", alt.correlation)}",
                            fontSize = 9.sp,
                            color = if (alt.followsBtc) AccentCyan else TextMuted
                        )
                    }
                }
            }
        }
    }
}
