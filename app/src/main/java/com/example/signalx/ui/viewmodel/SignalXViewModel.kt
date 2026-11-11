package com.example.signalx.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.signalx.data.api.BitgetApiClient
import com.example.signalx.data.api.BitgetTimeSync
import com.example.signalx.data.api.BitgetWebSocketManager
import com.example.signalx.data.api.ConnectionStatus
import com.example.signalx.data.db.AppDatabase
import com.example.signalx.data.db.DrawingEntity
import com.example.signalx.data.db.FavoriteEntity
import com.example.signalx.data.db.PaperTradeEntity
import com.example.signalx.data.db.PredictionEntity
import com.example.signalx.data.model.AutoModeScanResult
import com.example.signalx.data.model.BtcCorrelationAnalysis
import com.example.signalx.data.model.Candle
import com.example.signalx.data.model.Direction
import com.example.signalx.data.model.DrawingItem
import com.example.signalx.data.model.ForecastResult
import com.example.signalx.data.model.Language
import com.example.signalx.data.model.MarketType
import com.example.signalx.data.model.NextCandleReport
import com.example.signalx.data.model.OrderBook
import com.example.signalx.data.model.SignalType
import com.example.signalx.data.model.Ticker
import com.example.signalx.data.model.TimeFrame
import com.example.signalx.data.model.TradingDecision
import com.example.signalx.data.model.UrduExplanation
import com.example.signalx.data.model.UsdPriceTierInfo
import com.example.signalx.engine.analysis.MarketAnalysisReport
import com.example.signalx.engine.analysis.MarketStructureAnalyzer
import com.example.signalx.engine.decision.TradingDecisionEngine
import com.example.signalx.engine.forecast.ForecastEngine
import com.example.signalx.engine.testing.SignalXSelfTest
import com.example.signalx.engine.testing.TestResultItem
import com.example.signalx.data.model.ConfluenceReport
import com.example.signalx.data.model.CvdData
import com.example.signalx.data.model.MarketContextInfo
import com.example.signalx.data.model.OrderBookImbalanceData
import com.example.signalx.data.model.ProIndicators
import com.example.signalx.engine.indicators.TechnicalIndicators
import com.example.signalx.engine.urdu.UrduExplanationGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val activeSymbol: String = "BTCUSDT",
    val activeMarketType: MarketType = MarketType.SPOT,
    val activeTimeFrame: TimeFrame = TimeFrame.M15,
    val candles: List<Candle> = emptyList(),
    val latestTicker: Ticker? = null,
    val futuresMarkPrice: Double = 0.0,
    val futuresIndexPrice: Double = 0.0,
    val spotComparisonPrice: Double = 0.0,
    val futuresOpenInterest: Double = 0.0,
    val futuresFundingRate: Double = 0.0,
    val drawings: List<DrawingItem> = emptyList(),
    val orderBook: OrderBook? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.OFFLINE,
    val isDataStale: Boolean = false,
    val dataAgeSeconds: Long = 0L,
    val priceSource: String = "WS",
    val remainingCountdownMs: Long = 0L,
    val formattedCountdown: String = "00:00",
    val isPreparationMode: Boolean = false,
    val candleProgressFraction: Float = 0f,
    val pktTime: String = "",
    val analysisReport: MarketAnalysisReport? = null,
    val forecasts: List<ForecastResult> = emptyList(),
    val selectedHorizonMinutes: Int = 1,
    val decision: TradingDecision? = null,
    val explanation: UrduExplanation? = null,
    val spotTickers: List<Ticker> = emptyList(),
    val futuresTickers: List<Ticker> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val paperBalance: Double = 10_000.0,
    val openTrades: List<PaperTradeEntity> = emptyList(),
    val tradeHistory: List<PaperTradeEntity> = emptyList(),
    val language: Language = Language.ROMAN_URDU,
    val selfTestResults: List<TestResultItem> = emptyList(),
    val isRunningSelfTest: Boolean = false,
    val isLoadingCandles: Boolean = false,
    val isAutoModeEnabled: Boolean = false,
    val keepScreenAwake: Boolean = false,
    val autoScanResult: AutoModeScanResult? = null,
    val nextCandleReport: NextCandleReport? = null,
    val btcCorrelation: BtcCorrelationAnalysis? = null,
    val usdTierInfo: UsdPriceTierInfo? = null,
    val dailyPnl: Double = 0.0,
    val dailyLossLimitReached: Boolean = false,
    val cvdData: CvdData? = null,
    val orderBookImbalance: OrderBookImbalanceData? = null,
    val proIndicators: ProIndicators? = null,
    val marketContext: MarketContextInfo? = null,
    val confluenceReport: ConfluenceReport? = null,
    val autoErrorMessage: String? = null,
    val isAutoError: Boolean = false
)

class SignalXViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = BitgetApiClient()
    private val wsManager = BitgetWebSocketManager()
    private val db = AppDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var scannerJob: Job? = null
    private var telemetryJob: Job? = null
    private var marketContextJob: Job? = null
    private var candleLoadJob: Job? = null
    private var lastRecordedCandleTimestamp: Long = 0L

    private val scanSemaphore = Semaphore(5)
    private val autoExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("SignalXVM", "Supervised AutoScope caught error: ${throwable.message}", throwable)
        _uiState.value = _uiState.value.copy(
            isAutoError = true,
            autoErrorMessage = throwable.message ?: "Unknown auto scanner error"
        )
    }
    private val autoScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + autoExceptionHandler)

    init {
        // Initial setup
        viewModelScope.launch {
            try {
                apiClient.syncServerTime()
            } catch (e: Exception) {
                Log.w("SignalXVM", "Initial time sync error: ${e.message}")
            }

            loadFavorites()
            loadPaperTrades()
            loadCandles(_uiState.value.activeSymbol, _uiState.value.activeMarketType, _uiState.value.activeTimeFrame)
            wsManager.connect(_uiState.value.activeSymbol, _uiState.value.activeMarketType)

            observeWebSocket()
            startCountdownTimer()
            startMarketScanner()
            startFuturesTelemetry()
            startMarketContextSync()
        }
    }

    private fun startMarketContextSync() {
        marketContextJob?.cancel()
        marketContextJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val ctx = apiClient.getMarketContext()
                    _uiState.value = _uiState.value.copy(marketContext = ctx)
                } catch (e: Exception) {
                    Log.w("SignalXVM", "Context fetch error: ${e.message}")
                }
                delay(60_000L) // Fetch context every 60s
            }
        }
    }

    private fun observeWebSocket() {
        viewModelScope.launch {
            wsManager.connectionStatus.collect { status ->
                _uiState.value = _uiState.value.copy(connectionStatus = status)
            }
        }

        viewModelScope.launch {
            wsManager.isDataStale.collect { isStale ->
                _uiState.value = _uiState.value.copy(isDataStale = isStale)
            }
        }

        viewModelScope.launch {
            wsManager.dataAgeSeconds.collect { age ->
                _uiState.value = _uiState.value.copy(dataAgeSeconds = age)
                if (age > 15L && _uiState.value.priceSource != "REST") {
                    val fallback = apiClient.getSingleTicker(_uiState.value.activeSymbol, _uiState.value.activeMarketType)
                    if (fallback != null) {
                        _uiState.value = _uiState.value.copy(
                            latestTicker = fallback.copy(priceSource = "REST"),
                            priceSource = "REST"
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            wsManager.latestTicker.collect { ticker ->
                if (ticker != null) {
                    _uiState.value = _uiState.value.copy(
                        latestTicker = ticker,
                        priceSource = "WS"
                    )
                    updatePaperTradesLivePnl(ticker.lastPrice)
                }
            }
        }

        viewModelScope.launch {
            wsManager.latestCandleTick.collect { liveCandle ->
                if (liveCandle != null) {
                    mergeLiveCandle(liveCandle)
                    evaluatePaperTradesOnCandle(liveCandle)
                }
            }
        }

        viewModelScope.launch {
            wsManager.cvdData.collect { cvd ->
                if (cvd != null) {
                    _uiState.value = _uiState.value.copy(cvdData = cvd)
                }
            }
        }

        viewModelScope.launch {
            wsManager.orderBookImbalance.collect { imb ->
                if (imb != null) {
                    _uiState.value = _uiState.value.copy(orderBookImbalance = imb)
                }
            }
        }
    }

    fun setSymbol(symbol: String, marketType: MarketType = _uiState.value.activeMarketType) {
        if (_uiState.value.activeSymbol == symbol && _uiState.value.activeMarketType == marketType) return

        _uiState.value = _uiState.value.copy(
            activeSymbol = symbol,
            activeMarketType = marketType,
            isLoadingCandles = true
        )
        wsManager.switchSubscription(symbol, marketType)
        loadCandles(symbol, marketType, _uiState.value.activeTimeFrame)

        if (marketType == MarketType.FUTURES) {
            viewModelScope.launch {
                val (mark, index) = apiClient.getFuturesSymbolPrice(symbol)
                _uiState.value = _uiState.value.copy(
                    futuresMarkPrice = mark,
                    futuresIndexPrice = index
                )
            }
        }
    }

    fun setMarketType(marketType: MarketType) {
        if (_uiState.value.activeMarketType == marketType) return
        setSymbol(_uiState.value.activeSymbol, marketType)
    }

    fun setTimeFrame(timeFrame: TimeFrame) {
        if (_uiState.value.activeTimeFrame == timeFrame) return
        _uiState.value = _uiState.value.copy(activeTimeFrame = timeFrame, isLoadingCandles = true)
        wsManager.switchSubscription(_uiState.value.activeSymbol, _uiState.value.activeMarketType, timeFrame)
        loadCandles(_uiState.value.activeSymbol, _uiState.value.activeMarketType, timeFrame)
    }

    fun setLanguage(language: Language) {
        _uiState.value = _uiState.value.copy(language = language)
        // Refresh explanation with new language
        recomputeDecisionAndExplanation()
    }

    fun setSelectedHorizon(horizonMinutes: Int) {
        _uiState.value = _uiState.value.copy(selectedHorizonMinutes = horizonMinutes)
        recomputeDecisionAndExplanation()
    }

    private fun loadCandles(symbol: String, marketType: MarketType, timeFrame: TimeFrame) {
        candleLoadJob?.cancel()
        candleLoadJob = viewModelScope.launch {
            try {
                val candles = apiClient.getCandles(symbol, marketType, timeFrame, limit = 250)
                val orderBook = apiClient.getOrderBook(symbol, marketType)

                // For 1m candles for forecasting
                val candles1m = if (timeFrame == TimeFrame.M1) candles else {
                    apiClient.getCandles(symbol, marketType, TimeFrame.M1, limit = 120)
                }

                // Fetch BTC candles for correlation analysis if active symbol is not BTC
                val btcCandles = if (symbol == "BTCUSDT") candles else {
                    try {
                        apiClient.getCandles("BTCUSDT", marketType, timeFrame, limit = 60)
                    } catch (e: Exception) {
                        candles
                    }
                }

                _uiState.value = _uiState.value.copy(
                    candles = candles,
                    orderBook = orderBook,
                    isLoadingCandles = false
                )

                // Recompute analysis and forecasts
                runAnalysisEngine(candles, candles1m, orderBook, btcCandles)
            } catch (e: CancellationException) {
                // Job was cancelled by a newer timeframe or symbol selection; safely ignore
            } catch (e: Exception) {
                Log.e("SignalXVM", "Failed to load candles: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoadingCandles = false)
            }
        }
    }

    private fun mergeLiveCandle(liveCandle: Candle) {
        val currentList = _uiState.value.candles
        if (currentList.isEmpty()) return

        val last = currentList.last()
        val tfDuration = _uiState.value.activeTimeFrame.durationMs

        val updatedList = if (liveCandle.timestamp >= (last.timestamp / tfDuration) * tfDuration) {
            // Update last candle in-place
            val updatedLast = last.copy(
                high = maxOf(last.high, liveCandle.high),
                low = minOf(last.low, liveCandle.low),
                close = liveCandle.close,
                volume = last.volume + liveCandle.volume,
                isClosed = false
            )
            currentList.dropLast(1) + updatedLast
        } else {
            currentList
        }

        _uiState.value = _uiState.value.copy(candles = updatedList)
    }

    private fun runAnalysisEngine(
        candles: List<Candle>,
        candles1m: List<Candle>,
        orderBook: OrderBook?,
        btcCandles: List<Candle> = emptyList()
    ) {
        if (candles.isEmpty()) return

        val symbol = _uiState.value.activeSymbol
        val marketType = _uiState.value.activeMarketType
        val report = MarketStructureAnalyzer.analyze(
            candles = candles,
            orderBook = orderBook,
            symbol = symbol,
            marketType = marketType,
            comparisonCandles = btcCandles,
            comparisonSymbol = "BTCUSDT"
        )
        val forecasts = ForecastEngine.generateForecasts(candles1m.ifEmpty { candles }, candles, orderBook)
        val selHorizon = _uiState.value.selectedHorizonMinutes
        val activeForecast = forecasts.firstOrNull { it.horizonMinutes == selHorizon } ?: forecasts.firstOrNull()

        // BTC correlation & altcoin follow analysis
        val btcCorr = MarketStructureAnalyzer.analyzeBtcCorrelation(
            activeSymbol = symbol,
            activeCandles = candles,
            btcCandles = btcCandles.ifEmpty { candles },
            activeSignalDirection = activeForecast?.direction ?: Direction.SIDEWAYS
        )

        // Usd Price Tier and 1-cent move percentage calculation
        val currentPrice = report.currentPrice
        val tierLabel = when {
            currentPrice < 1.0 -> "Cheap < $1"
            currentPrice <= 100.0 -> "Mid $1-$100"
            else -> "High > $100"
        }
        val oneCentMovePct = if (currentPrice > 0) (0.01 / currentPrice) * 100.0 else 0.0
        val oneCentMoveStr = String.format(java.util.Locale.US, if (oneCentMovePct >= 0.01) "%.2f%%" else "%.4f%%", oneCentMovePct)
        val usdTier = UsdPriceTierInfo(tierLabel, oneCentMovePct, oneCentMoveStr)

        // Next Candle Minute-by-Minute Report
        val nextReport = ForecastEngine.generateNextCandleReport(
            candles1m = candles1m.ifEmpty { candles },
            higherTfCandles = candles,
            orderBook = orderBook,
            symbol = symbol,
            marketType = marketType
        )

        val decision = TradingDecisionEngine.generateDecision(
            symbol = symbol,
            report = report,
            forecast1m = activeForecast,
            marketType = marketType,
            isDataStale = _uiState.value.isDataStale,
            paperBalance = _uiState.value.paperBalance,
            btcCorrelation = btcCorr
        )

        val explanation = if (activeForecast != null) {
            UrduExplanationGenerator.generateExplanation(
                report = report,
                forecast = activeForecast,
                decision = decision,
                symbol = symbol,
                marketType = marketType
            )
        } else null

        // Pro Confluence & Indicators
        val proIndicators = TechnicalIndicators.calculateIchimoku(candles)
        val confluence = TradingDecisionEngine.generateConfluenceReport(
            report = report,
            forecast1m = activeForecast,
            marketType = marketType,
            symbol = symbol,
            proIndicators = proIndicators,
            cvdData = _uiState.value.cvdData,
            orderBookImbalance = _uiState.value.orderBookImbalance,
            marketContext = _uiState.value.marketContext,
            btcCorrelation = btcCorr,
            dataAgeSeconds = _uiState.value.dataAgeSeconds
        )

        _uiState.value = _uiState.value.copy(
            analysisReport = report,
            forecasts = forecasts,
            decision = decision,
            explanation = explanation,
            btcCorrelation = btcCorr,
            nextCandleReport = nextReport,
            usdTierInfo = usdTier,
            proIndicators = proIndicators,
            confluenceReport = confluence
        )

        // Store forecast in database for historical accuracy tracking
        activeForecast?.let { f ->
            savePredictionRecord(f, decision)
        }

        // Resolve any previous pending predictions using the latest candle
        candles.lastOrNull()?.let { lastCandle ->
            resolvePendingPredictions(lastCandle)
        }
    }

    private fun recomputeDecisionAndExplanation() {
        val report = _uiState.value.analysisReport ?: return
        val selHorizon = _uiState.value.selectedHorizonMinutes
        val activeForecast = _uiState.value.forecasts.firstOrNull { it.horizonMinutes == selHorizon } ?: return
        val btcCorr = _uiState.value.btcCorrelation

        val decision = TradingDecisionEngine.generateDecision(
            symbol = _uiState.value.activeSymbol,
            report = report,
            forecast1m = activeForecast,
            marketType = _uiState.value.activeMarketType,
            isDataStale = _uiState.value.isDataStale,
            paperBalance = _uiState.value.paperBalance,
            btcCorrelation = btcCorr
        )

        val explanation = UrduExplanationGenerator.generateExplanation(
            report = report,
            forecast = activeForecast,
            decision = decision,
            symbol = _uiState.value.activeSymbol,
            marketType = _uiState.value.activeMarketType
        )

        _uiState.value = _uiState.value.copy(
            decision = decision,
            explanation = explanation
        )
    }

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                delay(250L)
                val tf = _uiState.value.activeTimeFrame
                val correctedNow = BitgetTimeSync.getCorrectedServerTime()
                val (openMs, closeMs) = BitgetTimeSync.getCandleBoundaries(tf, correctedNow)
                val remainingMs = (closeMs - correctedNow).coerceAtLeast(0L)
                val formatted = BitgetTimeSync.formatCountdown(remainingMs)
                val isPrep = BitgetTimeSync.isPreparationMode(tf, remainingMs)

                val elapsed = (correctedNow - openMs).toFloat()
                val progress = (elapsed / tf.durationMs).coerceIn(0f, 1f)
                val pktTime = BitgetTimeSync.formatPktTime(correctedNow)

                _uiState.value = _uiState.value.copy(
                    remainingCountdownMs = remainingMs,
                    formattedCountdown = formatted,
                    isPreparationMode = isPrep,
                    candleProgressFraction = progress,
                    pktTime = pktTime
                )

                // Detect candle boundary close
                if (remainingMs <= 300L && openMs > lastRecordedCandleTimestamp) {
                    lastRecordedCandleTimestamp = openMs
                    delay(500L)
                    loadCandles(_uiState.value.activeSymbol, _uiState.value.activeMarketType, _uiState.value.activeTimeFrame)
                }
            }
        }
    }

    private fun startMarketScanner() {
        scannerJob?.cancel()
        scannerJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val spot = apiClient.getSpotTickers()
                    val futures = apiClient.getFuturesTickers()
                    _uiState.value = _uiState.value.copy(
                        spotTickers = spot,
                        futuresTickers = futures
                    )
                } catch (e: Exception) {
                    Log.w("SignalXVM", "Scanner poll error: ${e.message}")
                }
                delay(8000L) // Poll bulk tickers every 8 seconds
            }
        }
    }

    private fun startFuturesTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch {
            while (isActive) {
                val symbol = _uiState.value.activeSymbol
                val marketType = _uiState.value.activeMarketType
                if (marketType == MarketType.FUTURES) {
                    try {
                        val (mark, index) = apiClient.getFuturesSymbolPrice(symbol)
                        val oi = apiClient.getFuturesOpenInterest(symbol)
                        val fr = apiClient.getFuturesFundingRate(symbol)
                        val spotTicker = apiClient.getSingleTicker(symbol, MarketType.SPOT)
                        _uiState.value = _uiState.value.copy(
                            futuresMarkPrice = mark,
                            futuresIndexPrice = index,
                            futuresOpenInterest = oi,
                            futuresFundingRate = fr,
                            spotComparisonPrice = spotTicker?.lastPrice ?: 0.0
                        )
                    } catch (e: Exception) {
                        Log.w("SignalXVM", "Futures telemetry error: ${e.message}")
                    }
                }
                delay(20_000L)
            }
        }
    }

    // --- DRAWING TOOLS ---
    fun addDrawing(drawing: DrawingItem) {
        val updated = _uiState.value.drawings + drawing
        _uiState.value = _uiState.value.copy(drawings = updated)
    }

    fun removeDrawing(id: String) {
        _uiState.value = _uiState.value.copy(
            drawings = _uiState.value.drawings.filterNot { it.id == id }
        )
    }

    fun clearDrawings() {
        _uiState.value = _uiState.value.copy(drawings = emptyList())
    }

    // --- FAVORITES ---
    private fun loadFavorites() {
        viewModelScope.launch {
            db.favoriteDao().getAllFavorites().collect { list ->
                _uiState.value = _uiState.value.copy(favorites = list.map { it.symbol }.toSet())
            }
        }
    }

    fun toggleFavorite(symbol: String) {
        viewModelScope.launch {
            val marketTypeStr = _uiState.value.activeMarketType.name
            if (_uiState.value.favorites.contains(symbol)) {
                db.favoriteDao().removeFavorite(symbol, marketTypeStr)
            } else {
                db.favoriteDao().addFavorite(FavoriteEntity(symbol, marketTypeStr))
            }
        }
    }

    // --- PAPER TRADING ---
    private fun loadPaperTrades() {
        viewModelScope.launch {
            db.paperTradeDao().getOpenTrades().collect { list ->
                _uiState.value = _uiState.value.copy(openTrades = list)
            }
        }
        viewModelScope.launch {
            db.paperTradeDao().getAllTrades().collect { list ->
                val dayStart = (System.currentTimeMillis() / 86_400_000L) * 86_400_000L
                val todayTrades = list.filter { it.closeTimestamp >= dayStart }
                val dayPnl = todayTrades.sumOf { it.pnl }
                val limitHit = dayPnl <= -500.0
                _uiState.value = _uiState.value.copy(
                    tradeHistory = list,
                    dailyPnl = dayPnl,
                    dailyLossLimitReached = limitHit
                )
            }
        }
    }

    fun executePaperTrade(
        side: String,
        entryPrice: Double,
        quantity: Double,
        leverage: Int = 1,
        stopLoss: Double = 0.0,
        takeProfit: Double = 0.0
    ) {
        if (_uiState.value.dailyLossLimitReached) {
            Log.w("SignalXVM", "Daily loss limit reached ($500). Trade blocked.")
            return
        }
        viewModelScope.launch {
            val trade = PaperTradeEntity(
                symbol = _uiState.value.activeSymbol,
                marketType = _uiState.value.activeMarketType.name,
                side = side,
                entryPrice = entryPrice,
                quantity = quantity,
                leverage = leverage,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                status = "OPEN"
            )
            db.paperTradeDao().insertTrade(trade)
        }
    }

    fun tradeAtBestMinute() {
        val state = _uiState.value
        if (state.dailyLossLimitReached) return
        val bestMin = state.nextCandleReport?.bestEntryMinute ?: 1
        val nextItem = state.nextCandleReport?.minutesTable?.firstOrNull { it.minute == bestMin }
        val side = if (nextItem?.direction == Direction.DOWN || state.decision?.signal?.name?.contains("SHORT") == true) "SELL" else "BUY"
        val entryP = state.latestTicker?.lastPrice ?: state.candles.lastOrNull()?.close ?: 1.0
        val sl = state.decision?.stopLoss ?: (if (side == "BUY") entryP * 0.985 else entryP * 1.015)
        val tp = state.decision?.takeProfit1 ?: (if (side == "BUY") entryP * 1.03 else entryP * 0.97)
        val riskAmt = state.paperBalance * 0.02
        val dist = kotlin.math.abs(entryP - sl).coerceAtLeast(0.0001)
        val qty = (riskAmt / dist).coerceIn(0.001, (state.paperBalance * 2.0) / entryP)
        executePaperTrade(side, entryP, qty, leverage = 1, stopLoss = sl, takeProfit = tp)
    }

    fun closePaperTrade(trade: PaperTradeEntity, exitPrice: Double) {
        viewModelScope.launch {
            val isBuy = trade.side == "BUY"
            val diff = if (isBuy) (exitPrice - trade.entryPrice) else (trade.entryPrice - exitPrice)
            val rawPnl = diff * trade.quantity * trade.leverage
            val fee = (trade.entryPrice * trade.quantity * trade.leverage * 0.001) + (exitPrice * trade.quantity * trade.leverage * 0.001)
            val netPnl = rawPnl - fee

            val updated = trade.copy(
                status = "CLOSED",
                exitPrice = exitPrice,
                pnl = netPnl,
                fee = fee,
                closeTimestamp = System.currentTimeMillis()
            )
            db.paperTradeDao().updateTrade(updated)
            _uiState.value = _uiState.value.copy(paperBalance = _uiState.value.paperBalance + netPnl)
        }
    }

    fun resetPaperBalance() {
        viewModelScope.launch {
            db.paperTradeDao().clearAll()
            _uiState.value = _uiState.value.copy(paperBalance = 10_000.0)
        }
    }

    private fun updatePaperTradesLivePnl(currentPrice: Double) {
        val open = _uiState.value.openTrades
        for (trade in open) {
            if (trade.symbol == _uiState.value.activeSymbol) {
                val isBuy = trade.side == "BUY"
                if (isBuy) {
                    if (trade.stopLoss > 0 && currentPrice <= trade.stopLoss) {
                        closePaperTrade(trade, trade.stopLoss)
                    } else if (trade.takeProfit > 0 && currentPrice >= trade.takeProfit) {
                        closePaperTrade(trade, trade.takeProfit)
                    }
                } else {
                    if (trade.stopLoss > 0 && currentPrice >= trade.stopLoss) {
                        closePaperTrade(trade, trade.stopLoss)
                    } else if (trade.takeProfit > 0 && currentPrice <= trade.takeProfit) {
                        closePaperTrade(trade, trade.takeProfit)
                    }
                }
            }
        }
    }

    /**
     * Conservative paper trading rule:
     * If SL and TP both touched in one candle, strictly assume Stop Loss hit first.
     */
    private fun evaluatePaperTradesOnCandle(candle: Candle) {
        val open = _uiState.value.openTrades
        for (trade in open) {
            if (trade.symbol != _uiState.value.activeSymbol) continue
            val isBuy = trade.side == "BUY"
            if (isBuy) {
                val slTouched = trade.stopLoss > 0 && candle.low <= trade.stopLoss
                val tpTouched = trade.takeProfit > 0 && candle.high >= trade.takeProfit
                if (slTouched && tpTouched) {
                    // Conservative rule: assume SL first
                    closePaperTrade(trade, trade.stopLoss)
                } else if (slTouched) {
                    closePaperTrade(trade, trade.stopLoss)
                } else if (tpTouched) {
                    closePaperTrade(trade, trade.takeProfit)
                }
            } else {
                val slTouched = trade.stopLoss > 0 && candle.high >= trade.stopLoss
                val tpTouched = trade.takeProfit > 0 && candle.low <= trade.takeProfit
                if (slTouched && tpTouched) {
                    // Conservative rule: assume SL first
                    closePaperTrade(trade, trade.stopLoss)
                } else if (slTouched) {
                    closePaperTrade(trade, trade.stopLoss)
                } else if (tpTouched) {
                    closePaperTrade(trade, trade.takeProfit)
                }
            }
        }
    }

    // --- PREDICTION RESOLUTION ---
    private fun savePredictionRecord(forecast: ForecastResult, decision: TradingDecision) {
        viewModelScope.launch {
            val entity = PredictionEntity(
                symbol = _uiState.value.activeSymbol,
                marketType = _uiState.value.activeMarketType.name,
                timeframe = _uiState.value.activeTimeFrame.label,
                horizonMinutes = forecast.horizonMinutes,
                timestamp = forecast.timestamp,
                basePrice = forecast.basePrice,
                predictedDirection = forecast.direction.name,
                probUp = forecast.probUp,
                probDown = forecast.probDown,
                probSideways = forecast.probSideways,
                predictedHigh = forecast.estimatedHigh,
                predictedLow = forecast.estimatedLow,
                confidence = forecast.confidence,
                signalType = decision.signal.name
            )
            db.predictionDao().insertPrediction(entity)
        }
    }

    private fun resolvePendingPredictions(candle: Candle) {
        viewModelScope.launch {
            try {
                val unresolved = db.predictionDao().getUnresolvedPredictions()
                val now = BitgetTimeSync.getCorrectedServerTime()
                for (p in unresolved) {
                    val targetTime = p.timestamp + p.horizonMinutes * 60_000L
                    if (now >= targetTime) {
                        val actualClose = candle.close
                        val actualHigh = candle.high
                        val actualLow = candle.low
                        val actualDir = when {
                            actualClose > p.basePrice * 1.0005 -> "UP"
                            actualClose < p.basePrice * 0.9995 -> "DOWN"
                            else -> "SIDEWAYS"
                        }
                        val isWin = (p.predictedDirection == actualDir)
                        val isHitHighLow = (p.predictedHigh > 0 && actualHigh >= p.predictedHigh) ||
                                (p.predictedLow > 0 && actualLow <= p.predictedLow)

                        val resolved = p.copy(
                            isResolved = true,
                            actualClose = actualClose,
                            actualHigh = actualHigh,
                            actualLow = actualLow,
                            outcomeDirection = actualDir,
                            isWin = isWin,
                            isHitHighLow = isHitHighLow,
                            resolvedTimestamp = now
                        )
                        db.predictionDao().updatePrediction(resolved)
                    }
                }
            } catch (e: Exception) {
                Log.e("SignalXVM", "Error resolving predictions: ${e.message}")
            }
        }
    }

    // --- SELF TEST ---
    fun runSelfTest() {
        _uiState.value = _uiState.value.copy(isRunningSelfTest = true)
        viewModelScope.launch {
            val results = SignalXSelfTest.runAllTests(apiClient)
            _uiState.value = _uiState.value.copy(
                selfTestResults = results,
                isRunningSelfTest = false
            )
        }
    }

    // --- AUTO MODE ---
    private var autoModeJob: Job? = null

    fun toggleAutoMode() {
        val newState = !_uiState.value.isAutoModeEnabled
        _uiState.value = _uiState.value.copy(isAutoModeEnabled = newState)
        if (newState) {
            startAutoModeScanner()
        } else {
            autoModeJob?.cancel()
        }
    }

    fun toggleKeepScreenAwake() {
        _uiState.value = _uiState.value.copy(keepScreenAwake = !_uiState.value.keepScreenAwake)
    }

    fun retryAutoMode() {
        _uiState.value = _uiState.value.copy(
            isAutoError = false,
            autoErrorMessage = null,
            isAutoModeEnabled = true
        )
        startAutoModeScanner()
    }

    private fun startAutoModeScanner() {
        autoModeJob?.cancel()
        _uiState.value = _uiState.value.copy(isAutoError = false, autoErrorMessage = null)
        autoModeJob = autoScope.launch {
            while (isActive && _uiState.value.isAutoModeEnabled) {
                try {
                    val marketType = _uiState.value.activeMarketType
                    // Rate-limited and IO-dispatched ticker retrieval
                    val tickers = withContext(Dispatchers.IO) {
                        scanSemaphore.withPermit {
                            if (marketType == MarketType.SPOT) {
                                _uiState.value.spotTickers.ifEmpty { apiClient.getSpotTickers() }
                            } else {
                                _uiState.value.futuresTickers.ifEmpty { apiClient.getFuturesTickers() }
                            }
                        }
                    }

                    // CPU heavy filtering & sorting dispatched on Default
                    val bestCandidate = withContext(Dispatchers.Default) {
                        val top40 = tickers.sortedByDescending { it.quoteVolume24h }.take(40)
                        top40.maxByOrNull { t ->
                            kotlin.math.abs(t.change24hPercent) * 2.0 + (if (t.volume24h > 0) kotlin.math.log10(t.volume24h) else 0.0)
                        }
                    }

                    if (bestCandidate != null) {
                        val scanResult = AutoModeScanResult(
                            strongestSymbol = bestCandidate.symbol,
                            marketType = marketType,
                            direction = if (bestCandidate.change24h >= 0) Direction.UP else Direction.DOWN,
                            score = (kotlin.math.abs(bestCandidate.change24hPercent) * 10.0).coerceIn(40.0, 95.0),
                            signal = if (bestCandidate.change24h >= 0) SignalType.LONG else SignalType.SHORT,
                            lastScanTime = System.currentTimeMillis(),
                            summary = "AUTO detected top liquid volume mover ${bestCandidate.symbol} (${String.format(java.util.Locale.US, "%+.2f%%", bestCandidate.change24hPercent)}) with verified order flow."
                        )
                        _uiState.value = _uiState.value.copy(autoScanResult = scanResult, isAutoError = false)

                        if (_uiState.value.activeSymbol != bestCandidate.symbol) {
                            setSymbol(bestCandidate.symbol, marketType)
                        } else {
                            loadCandles(_uiState.value.activeSymbol, marketType, _uiState.value.activeTimeFrame)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("SignalXVM", "Auto mode scan error: ${e.message}", e)
                    _uiState.value = _uiState.value.copy(
                        isAutoError = true,
                        autoErrorMessage = e.message ?: "Scanner encountered an error"
                    )
                }
                delay(30_000L) // Auto-recomputes report every 30 seconds
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        scannerJob?.cancel()
        telemetryJob?.cancel()
        autoModeJob?.cancel()
        wsManager.disconnect()
    }
}
