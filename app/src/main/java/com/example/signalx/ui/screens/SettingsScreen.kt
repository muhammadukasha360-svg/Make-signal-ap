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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signalx.data.api.PriceFormatter
import com.example.signalx.data.model.Language
import com.example.signalx.data.model.MarketType
import com.example.signalx.ui.viewmodel.UiState
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentYellow
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.math.BigDecimal
import java.util.Locale

@Composable
fun SettingsScreen(
    state: UiState,
    onSelectLanguage: (Language) -> Unit,
    onRunSelfTest: () -> Unit,
    onResetData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val livePrice = state.latestTicker?.lastPrice ?: 0.0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E11))
            .verticalScroll(scrollState)
            .padding(12.dp)
    ) {
        Text("Settings & Limitations", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("Configuration, Price Verification & Engine Transparency", fontSize = 11.sp, color = TextSecondary)

        Spacer(modifier = Modifier.height(14.dp))

        // Language Selection
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("App & Explanation Language", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LanguageOption("اردو (Urdu Script)", state.language == Language.URDU_SCRIPT, Modifier.weight(1f)) {
                        onSelectLanguage(Language.URDU_SCRIPT)
                    }
                    LanguageOption("Roman Urdu", state.language == Language.ROMAN_URDU, Modifier.weight(1f)) {
                        onSelectLanguage(Language.ROMAN_URDU)
                    }
                    LanguageOption("English", state.language == Language.ENGLISH, Modifier.weight(1f)) {
                        onSelectLanguage(Language.ENGLISH)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Spacer(modifier = Modifier.height(14.dp))

        // Price Check Debug Panel: 10 symbols for Spot & Futures
        PriceCheckVerificationSection(state = state)

        Spacer(modifier = Modifier.height(14.dp))

        // In-App Automated Self-Test Runner
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("System Self-Test Suite", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Tests APIs, candles, math, countdowns & Urdu generator", fontSize = 11.sp, color = TextSecondary)
                    }

                    Button(
                        onClick = onRunSelfTest,
                        enabled = !state.isRunningSelfTest,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentYellow),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        if (state.isRunningSelfTest) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFF0B0E11), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF0B0E11), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Tests", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0B0E11))
                        }
                    }
                }

                if (state.selfTestResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.selfTestResults.forEach { test ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkCardElevated)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (test.isPassed) BullishGreen else BearishRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (test.isPassed) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = Color(0xFF0B0E11),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(test.testName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(test.details, fontSize = 10.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Transparent System Limitations
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Platform Transparency & Limitations", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = """
                        1. Quantitative Rule-Based Engine:
                        The forecasts in SignalX AI are produced by an auditable rule-based quantitative evidence engine (incorporating technical indicators, smart money price action, order flow, and multi-timeframe structure) combined with an on-device statistical calibration layer. It does NOT claim to use a black-box opaque neural network.
                        
                        2. Probabilistic Forecasts:
                        All forecast outcomes (UP / DOWN / SIDEWAYS) are mathematical probabilities based on historical edge. No indicator or quantitative model can guarantee 100% accuracy in financial markets.
                        
                        3. Real Bitget Public Data:
                        Live prices, candles, and order books are fetched directly from Bitget public endpoints without modification or fabrication.
                        
                        4. Educational Disclaimer:
                        SignalX AI is an analytical tool for educational and decision-support purposes. It is NOT financial advice. Always practice strict risk management.
                    """.trimIndent(),
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Reset Data
        OutlinedButton(
            onClick = onResetData,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BearishRed),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BearishRed)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(42.dp)
        ) {
            Text("Clear Cached Predictions & Data", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun LanguageOption(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) AccentYellow else DarkCardElevated)
            .border(1.dp, if (isSelected) AccentYellow else DarkBorder, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF0B0E11) else TextPrimary
        )
    }
}

@Composable
fun PriceCheckVerificationSection(state: UiState) {
    val checkSymbols = listOf(
        "BTCUSDT", "ETHUSDT", "XRPUSDT", "SOLUSDT", "DOGEUSDT",
        "ADAUSDT", "PEPEUSDT", "SHIBUSDT", "DOGSUSDT", "NOTUSDT"
    )

    var activeTab by remember { mutableIntStateOf(if (state.activeMarketType == MarketType.SPOT) 0 else 1) }
    val currentMarket = if (activeTab == 0) MarketType.SPOT else MarketType.FUTURES
    val tickers = if (activeTab == 0) state.spotTickers else state.futuresTickers

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = AccentYellow, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Price Check Verification (10 Symbols)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Text("Compares Raw Bitget WS vs Raw REST vs Displayed digits with precision", fontSize = 10.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(10.dp))

            TabRow(
                selectedTabIndex = activeTab,
                containerColor = DarkCardElevated,
                contentColor = AccentYellow,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = AccentYellow
                    )
                }
            ) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Spot (10)") })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Futures (10)") })
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                checkSymbols.forEach { sym ->
                    val ticker = tickers.firstOrNull { it.symbol == sym }
                    val rawWs = if (state.activeSymbol == sym && state.activeMarketType == currentMarket && state.latestTicker != null) {
                        state.latestTicker.rawLastPrice.ifBlank { state.latestTicker.lastPrice.toString() }
                    } else {
                        ticker?.rawLastPrice ?: ""
                    }
                    val rawRest = ticker?.rawLastPrice ?: ""
                    val rawToUse = rawWs.ifBlank { rawRest }
                    val displayed = PriceFormatter.formatPrice(sym, currentMarket, rawToUse)
                    val decimals = PriceFormatter.getPrecision(sym, currentMarket) ?: if (displayed.contains(".")) displayed.substringAfter(".").length else 0

                    val isMatch = if (rawToUse.isNotBlank() && displayed != "--") {
                        try {
                            BigDecimal(rawToUse).compareTo(BigDecimal(displayed)) == 0
                        } catch (e: Exception) {
                            rawToUse == displayed
                        }
                    } else {
                        true // No data yet
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkCardElevated)
                            .padding(8.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(sym, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Decimals: $decimals", fontSize = 10.sp, color = AccentCyan)

                                Spacer(modifier = Modifier.weight(1f))

                                val badgeText = if (rawToUse.isBlank()) "PENDING" else if (isMatch) "MATCH" else "MISMATCH"
                                val badgeColor = if (rawToUse.isBlank()) AccentYellow else if (isMatch) BullishGreen else BearishRed
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(badgeColor.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(badgeText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = badgeColor)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Raw WS: ${rawWs.ifBlank { "--" }}", fontSize = 10.sp, color = TextSecondary)
                                Text("Raw REST: ${rawRest.ifBlank { "--" }}", fontSize = 10.sp, color = TextSecondary)
                            }
                            Text("Displayed UI: $displayed", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AccentYellow)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PriceCheckRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
