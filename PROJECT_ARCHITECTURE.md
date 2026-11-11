# PROJECT ARCHITECTURE - SignalX AI

## 1. Overview
SignalX AI is a professional native Android application built with Kotlin and Jetpack Compose (Material 3). It provides institutional cryptocurrency quantitative analysis, real-time exchange streaming via Bitget public REST APIs and WebSockets, technical indicator calculations, automated multi-horizon candle forecasts, order flow analysis (CVD and Order Book Imbalance), 6-group confluence decision generation, and a complete Smart Money Concepts (SMC) quantitative engine with 34 interactive chart markers across 13 timeframes.

## 2. Directory & Package Structure
```
app/src/main/java/com/example/
├── MainActivity.kt                      // App entry point, edge-to-edge, window flags, tab routing
├── signalx/
│   ├── data/
│   │   ├── api/
│   │   │   ├── BitgetApiClient.kt       // Bitget REST API & public market context client
│   │   │   ├── BitgetTimeSync.kt        // Exchange server time synchronization & countdown
│   │   │   ├── BitgetWebSocketManager.kt// Real-time WebSocket: tickers, candles, trades, orderbook depth
│   │   │   └── PriceFormatter.kt        // Exact-precision BigDecimal price formatter
│   │   ├── db/
│   │   │   ├── AppDatabase.kt           // Room database for immutable predictions & paper trades
│   │   │   └── PredictionEntity.kt      // Prediction & paper trade persistence schemas
│   │   └── model/
│   │       ├── Models.kt                // Core data classes (Candle, Ticker, OrderBook, CvdData, etc.)
│   │       └── SmcModels.kt             // SMC data classes (EnhancedFvg, EnhancedOrderBlock, BOS/CHoCH, Liquidity, OTE, SMT)
│   ├── engine/
│   │   ├── analysis/
│   │   │   ├── MarketStructureAnalyzer.kt // 20/200 candle structure, breakout, swing points, liquidity
│   │   │   └── SmartMoneyEngine.kt        // Mathematical SMC engine: FVG, Order Blocks, BOS/CHoCH, OTE, SMT
│   │   ├── decision/
│   │   │   └── TradingDecisionEngine.kt // 6-group confluence scoring, levels, position sizing
│   │   ├── forecast/
│   │   │   └── ForecastEngine.kt        // 14-horizon forecasting & next-candle minute report
│   │   ├── indicators/
│   │   │   └── TechnicalIndicators.kt   // Math: EMA, RSI, MACD, ATR, ADX, Ichimoku, Supertrend, OBV, etc.
│   │   └── urdu/
│   │       └── UrduExplanationGenerator.kt // Dynamic Urdu, Roman Urdu, and English market commentary
│   └── ui/
│       ├── chart/
│       │   ├── CandlestickChart.kt      // Custom Compose Canvas TradingView-style candlestick chart with 34 SMC overlays
│       │   └── SmcChartModels.kt        // SmcLayerVisibility and SelectedSmcElement models
│       ├── components/
│       │   ├── OrderFlowPanel.kt        // Pure Compose panel: live CVD, orderbook imbalance, liquidity walls
│       │   └── SmcElementDetailModal.kt // Interactive modal: pattern specs, confirmation criteria, Roman Urdu explanation
│       ├── screens/
│       │   ├── ChartScreen.kt           // Main chart screen, timeframes, SMC layer toggles, countdown bar
│       │   ├── ForecastScreen.kt        // 10-minute forecast table, breakout, confluence card
│       │   ├── MarketScreen.kt          // Watchlist, top volume movers, symbol selection
│       │   ├── PaperTradingScreen.kt    // Simulated trading, PnL tracking, risk management
│       │   ├── StatsScreen.kt           // Prediction performance & Wilson 95% confidence intervals
│       │   └── SettingsScreen.kt        // Preferences, language switcher, auto-mode toggles
│       └── viewmodel/
│           └── SignalXViewModel.kt      // Central ViewModel managing state, timeframe transitions, and data flows
└── ui/theme/
    ├── Color.kt                         // Dark theme Binance/TradingView color palette
    ├── Theme.kt                         // Material 3 theme configuration
    └── Type.kt                          // Typography settings
```

## 3. Data Flow Architecture
1. **Exchange Ingestion:**
   - `BitgetWebSocketManager` maintains persistent connection to `wss://ws.bitget.com/v2/ws/public`.
   - Subscribes to `ticker`, `trade`, `books15`, `candle1m`, and the active timeframe candle channel.
   - Pushes live updates to ViewModel via Kotlin `StateFlow`.
2. **Order Flow & Precision:**
   - Trades are processed in `BigDecimal` to calculate Cumulative Volume Delta (CVD) without floating-point precision loss.
   - Depth updates compute Order Book Imbalance ratio, detect liquidity walls, and track absorption.
3. **Smart Money Concepts (SMC) Quantitative Engine:**
   - Evaluates Fair Value Gaps (Bullish, Bearish, IFVG, BPR) with exact fill percentage and mitigation tracking.
   - Identifies institutional Order Blocks (Bullish, Bearish, Breakers, Mitigations) and calculates displacement strength.
   - Determines structural breaks (BOS vs CHoCH vs MSS) with confirmed candle-close vs wick-sweep distinction.
   - Maps Buy-Side and Sell-Side Liquidity pools, Equal Highs/Lows, and stop hunts.
   - Computes dealing range 50% Equilibrium and 61.8%/78.6% Optimal Trade Entry (OTE) zones.
   - Analyzes cross-asset SMT divergence between active asset and BTC benchmark.
4. **Timeframe Transition Safety:**
   - When switching timeframes (e.g. 15m to 1m), `candleLoadJob?.cancel()` immediately cancels previous in-flight requests.
   - Canvas coordinate transformations clamp dimensions with `.coerceAtLeast(0f)` to prevent empty range crashes.
5. **Interactive Charting Experience:**
   - 34 distinct SMC markers drawn directly onto Canvas, scaling dynamically on pinch zoom and drag panning.
   - Tapping any SMC element launches `SmcElementDetailModal` presenting full metrics and Roman Urdu institutional explanation.
   - Independent layer visibility controls allow toggling individual SMC overlays on demand.

## 4. CI/CD & Build Pipeline
- **GitHub Actions Workflow:** `.github/workflows/android-build.yml` runs automated builds and JVM unit tests on `ubuntu-latest`.
- **Target APK:** `app/build/outputs/apk/debug/app-debug.apk` (23MB size, fully verified).
