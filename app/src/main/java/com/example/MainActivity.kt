package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signalx.ui.screens.ChartScreen
import com.example.signalx.ui.screens.ForecastScreen
import com.example.signalx.ui.screens.MarketScreen
import com.example.signalx.ui.screens.PaperTradingScreen
import com.example.signalx.ui.screens.SettingsScreen
import com.example.signalx.ui.screens.StatsScreen
import com.example.signalx.ui.viewmodel.SignalXViewModel
import com.example.ui.theme.AccentYellow
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private val viewModel: SignalXViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                SignalXMainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SignalXMainApp(viewModel: SignalXViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(1) } // Default to Chart (tab index 1)

    // Handle system back navigation: return to Chart tab if on another tab
    BackHandler(enabled = selectedTab != 1) {
        selectedTab = 1
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    androidx.compose.runtime.LaunchedEffect(uiState.keepScreenAwake) {
        if (uiState.keepScreenAwake) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E11)),
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar")
            ) {
                // Tab 0: Market Scanner
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Storefront, contentDescription = "Market") },
                    label = { Text("Market", fontSize = 11.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0B0E11),
                        selectedTextColor = AccentYellow,
                        indicatorColor = AccentYellow,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("tab_market")
                )

                // Tab 1: Chart
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.CandlestickChart, contentDescription = "Chart") },
                    label = { Text("Chart", fontSize = 11.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0B0E11),
                        selectedTextColor = AccentYellow,
                        indicatorColor = AccentYellow,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("tab_chart")
                )

                // Tab 2: Forecast
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.AutoGraph, contentDescription = "Forecast") },
                    label = { Text("Forecast", fontSize = 11.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0B0E11),
                        selectedTextColor = AccentYellow,
                        indicatorColor = AccentYellow,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("tab_forecast")
                )

                // Tab 3: Paper Trading
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Wallet, contentDescription = "Paper") },
                    label = { Text("Paper", fontSize = 11.sp, fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0B0E11),
                        selectedTextColor = AccentYellow,
                        indicatorColor = AccentYellow,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("tab_paper")
                )

                // Tab 4: Stats & Backtest
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.QueryStats, contentDescription = "Stats") },
                    label = { Text("Stats", fontSize = 11.sp, fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0B0E11),
                        selectedTextColor = AccentYellow,
                        indicatorColor = AccentYellow,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("tab_stats")
                )

                // Tab 5: Settings & Limitations
                NavigationBarItem(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp, fontWeight = if (selectedTab == 5) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0B0E11),
                        selectedTextColor = AccentYellow,
                        indicatorColor = AccentYellow,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> MarketScreen(
                    state = uiState,
                    onSelectSymbol = { symbol, marketType ->
                        viewModel.setSymbol(symbol, marketType)
                        selectedTab = 1 // Switch to chart
                    },
                    onToggleFavorite = { symbol ->
                        viewModel.toggleFavorite(symbol)
                    }
                )

                1 -> ChartScreen(
                    state = uiState,
                    onSelectTimeFrame = { tf -> viewModel.setTimeFrame(tf) },
                    onSelectMarketType = { mt -> viewModel.setMarketType(mt) },
                    onOpenForecast = { selectedTab = 2 },
                    onTakeSignal = {
                        val dec = uiState.decision
                        val lastP = uiState.latestTicker?.lastPrice ?: uiState.candles.lastOrNull()?.close ?: 1.0
                        if (dec != null) {
                            val side = if (dec.signal.name.contains("LONG")) "BUY" else "SELL"
                            val qty = dec.positionSizeRecommended / lastP
                            viewModel.executePaperTrade(side, lastP, qty, 1, dec.stopLoss, dec.takeProfit1)
                            selectedTab = 3 // Switch to paper trading
                        }
                    },
                    onToggleAutoMode = { viewModel.toggleAutoMode() },
                    onToggleKeepScreenAwake = { viewModel.toggleKeepScreenAwake() },
                    onTradeAtBestMinute = {
                        viewModel.tradeAtBestMinute()
                        selectedTab = 3
                    },
                    onRetryAutoMode = { viewModel.retryAutoMode() }
                )

                2 -> ForecastScreen(
                    state = uiState,
                    onSelectHorizon = { horizon -> viewModel.setSelectedHorizon(horizon) },
                    onSelectLanguage = { lang -> viewModel.setLanguage(lang) },
                    onTakeSignal = {
                        val dec = uiState.decision
                        val lastP = uiState.latestTicker?.lastPrice ?: uiState.candles.lastOrNull()?.close ?: 1.0
                        if (dec != null) {
                            val side = if (dec.signal.name.contains("LONG")) "BUY" else "SELL"
                            val qty = dec.positionSizeRecommended / lastP
                            viewModel.executePaperTrade(side, lastP, qty, 1, dec.stopLoss, dec.takeProfit1)
                            selectedTab = 3
                        }
                    },
                    onTradeAtBestMinute = {
                        viewModel.tradeAtBestMinute()
                        selectedTab = 3
                    }
                )

                3 -> PaperTradingScreen(
                    state = uiState,
                    onExecuteTrade = { side, entry, qty, lev, sl, tp ->
                        viewModel.executePaperTrade(side, entry, qty, lev, sl, tp)
                    },
                    onCloseTrade = { trade, exitPrice ->
                        viewModel.closePaperTrade(trade, exitPrice)
                    },
                    onResetBalance = {
                        viewModel.resetPaperBalance()
                    }
                )

                4 -> StatsScreen(
                    state = uiState
                )

                5 -> SettingsScreen(
                    state = uiState,
                    onSelectLanguage = { lang -> viewModel.setLanguage(lang) },
                    onRunSelfTest = { viewModel.runSelfTest() },
                    onResetData = { viewModel.resetPaperBalance() }
                )
            }
        }
    }
}
