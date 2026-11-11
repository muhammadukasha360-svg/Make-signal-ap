package com.example.signalx.ui.chart

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signalx.data.api.PriceFormatter
import com.example.signalx.data.model.Candle
import com.example.signalx.data.model.DrawingItem
import com.example.signalx.data.model.MarketType
import com.example.signalx.data.model.TradingDecision
import com.example.signalx.engine.analysis.MarketAnalysisReport
import com.example.signalx.engine.indicators.BollingerBands
import com.example.signalx.engine.indicators.TechnicalIndicators
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentYellow
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun CandlestickChart(
    candles: List<Candle>,
    report: MarketAnalysisReport?,
    decision: TradingDecision?,
    livePrice: Double,
    drawings: List<DrawingItem> = emptyList(),
    symbol: String = "BTCUSDT",
    marketType: MarketType = MarketType.SPOT,
    showEma: Boolean = true,
    showBollinger: Boolean = false,
    showVwap: Boolean = false,
    showOverlays: Boolean = true,
    smcLayers: SmcLayerVisibility = SmcLayerVisibility(),
    onElementClick: (SelectedSmcElement) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (candles.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0B0E11)),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading exchange candlestick data...", color = TextSecondary, fontSize = 14.sp)
        }
        return
    }

    // Viewport control: visible candles count and scroll offset
    var visibleCount by remember { mutableIntStateOf(60) }
    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var crosshairOffset by remember { mutableStateOf<Offset?>(null) }
    var selectedCandle by remember { mutableStateOf<Candle?>(null) }
    var activeMinPrice by remember { mutableDoubleStateOf(0.0) }
    var activeMaxPrice by remember { mutableDoubleStateOf(0.0) }
    var activeChartHeight by remember { mutableFloatStateOf(0f) }

    val dateFormat = remember {
        SimpleDateFormat("dd MMM, HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Karachi")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E11))
    ) {
        // Main Chart Canvas with Gesture Detection
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(candles.size) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // Zoom in/out: adjust visible candles count
                        if (zoom != 1f) {
                            val newCount = (visibleCount / zoom).toInt().coerceIn(20, 180)
                            visibleCount = newCount
                        }
                        // Pan left/right: adjust scroll offset
                        if (pan.x != 0f) {
                            scrollOffset = (scrollOffset + pan.x).coerceAtMost(0f)
                        }
                    }
                }
                .pointerInput(candles.size) {
                    detectTapGestures { tapOffset ->
                        val smc = report?.smcAnalysis ?: return@detectTapGestures
                        if (activeChartHeight <= 0f || activeMaxPrice <= activeMinPrice) return@detectTapGestures
                        val currentRange = activeMaxPrice - activeMinPrice
                        val tappedPrice = activeMaxPrice - (tapOffset.y / activeChartHeight) * currentRange

                        // 1. Check FVG
                        val tappedFvg = smc.fvgs.firstOrNull { tappedPrice >= min(it.top, it.bottom) && tappedPrice <= max(it.top, it.bottom) }
                        if (tappedFvg != null) {
                            onElementClick(
                                SelectedSmcElement(
                                    title = "Fair Value Gap (${tappedFvg.type.name})",
                                    direction = if (tappedFvg.type == com.example.signalx.data.model.FvgType.BULLISH) "BULLISH" else if (tappedFvg.type == com.example.signalx.data.model.FvgType.BEARISH) "BEARISH" else "NEUTRAL",
                                    timestamp = tappedFvg.timestamp,
                                    priceRangeStr = "${PriceFormatter.formatPrice(symbol, marketType, tappedFvg.bottom)} - ${PriceFormatter.formatPrice(symbol, marketType, tappedFvg.top)} (Mid: ${PriceFormatter.formatPrice(symbol, marketType, tappedFvg.midpoint)})",
                                    status = "${tappedFvg.status.name} (${tappedFvg.fillPercent.toInt()}% fill)",
                                    confirmationCondition = "Clean candle close rejecting midpoint (${PriceFormatter.formatPrice(symbol, marketType, tappedFvg.midpoint)}) with volume",
                                    invalidationCondition = "Full candle body close through ${PriceFormatter.formatPrice(symbol, marketType, if (tappedFvg.type == com.example.signalx.data.model.FvgType.BULLISH) tappedFvg.bottom else tappedFvg.top)}",
                                    romanUrduExplanation = "Ye 3-candle imbalance zone hai jahan price tezi se nikal gayi aur liquidity fill nahi hui. Price is imbalance ko retest karke reaction deti hai.",
                                    supportingEvidence = "Age: ${tappedFvg.ageCandles} candles | Displacement: ${if (tappedFvg.isDisplacementFvg) "Strong" else "Standard"}"
                                )
                            )
                            return@detectTapGestures
                        }

                        // 2. Check Order Block
                        val tappedOb = smc.orderBlocks.firstOrNull { tappedPrice >= min(it.top, it.bottom) && tappedPrice <= max(it.top, it.bottom) }
                        if (tappedOb != null) {
                            onElementClick(
                                SelectedSmcElement(
                                    title = "Order Block (${tappedOb.type.name})",
                                    direction = if (tappedOb.type == com.example.signalx.data.model.OrderBlockType.BULLISH_OB) "BULLISH" else "BEARISH",
                                    timestamp = tappedOb.timestamp,
                                    priceRangeStr = "${PriceFormatter.formatPrice(symbol, marketType, tappedOb.bottom)} - ${PriceFormatter.formatPrice(symbol, marketType, tappedOb.top)}",
                                    status = if (tappedOb.isInvalidated) "INVALIDATED" else if (tappedOb.isRetested) "RETESTED" else "ACTIVE",
                                    confirmationCondition = "Lower timeframe MSS or rejection wick bouncing from order block boundary",
                                    invalidationCondition = "Candle close beyond opposite boundary (${PriceFormatter.formatPrice(symbol, marketType, if (tappedOb.type == com.example.signalx.data.model.OrderBlockType.BULLISH_OB) tappedOb.bottom else tappedOb.top)})",
                                    romanUrduExplanation = "Institutional buying ya selling zone jahan se market ne strong displacement move banaya tha.",
                                    supportingEvidence = "Volume: ${formatVolumeShort(tappedOb.volume)} | Displacement: ${String.format(Locale.US, "%.2f%%", tappedOb.displacementStrength)}"
                                )
                            )
                            return@detectTapGestures
                        }

                        // 3. Check Structure Break
                        val tappedBreak = smc.structureBreaks.firstOrNull { abs(it.breakPrice - tappedPrice) <= (currentRange * 0.03) }
                        if (tappedBreak != null) {
                            onElementClick(
                                SelectedSmcElement(
                                    title = "${tappedBreak.type.name} (Structure Break)",
                                    direction = if (tappedBreak.isBullish) "BULLISH" else "BEARISH",
                                    timestamp = tappedBreak.timestamp,
                                    priceRangeStr = PriceFormatter.formatPrice(symbol, marketType, tappedBreak.breakPrice),
                                    status = if (tappedBreak.confirmedByClose) "CONFIRMED BY CLOSE" else "WICK TEST ONLY",
                                    confirmationCondition = "Subsequent candle continues expansion in direction of break",
                                    invalidationCondition = "Price reclaims and closes back inside previous swing structure",
                                    romanUrduExplanation = "${if (tappedBreak.isBullish) "Bullish" else "Bearish"} trend shift ya continuation structure confirm hua.",
                                    supportingEvidence = "Type: ${tappedBreak.type.name} | Close Confirmed: ${tappedBreak.confirmedByClose}"
                                )
                            )
                            return@detectTapGestures
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val rightAxisWidth = 60.dp.toPx()
            val bottomAxisHeight = 24.dp.toPx()
            val volumeHeight = (canvasHeight * 0.18f).coerceAtLeast(0f)

            val chartWidth = (canvasWidth - rightAxisWidth).coerceAtLeast(0f)
            val chartHeight = (canvasHeight - bottomAxisHeight - volumeHeight).coerceAtLeast(0f)

            if (candles.isEmpty() || chartWidth <= 10f || chartHeight <= 10f) return@Canvas

            val totalCandles = candles.size
            val effectiveVisibleCount = visibleCount.coerceAtMost(totalCandles).coerceAtLeast(1)
            val candleWidth = chartWidth / effectiveVisibleCount.toFloat()
            val candleBodyWidth = (candleWidth * 0.7f).coerceAtLeast(2f)

            val maxScrollCandles = (totalCandles - effectiveVisibleCount).coerceAtLeast(0)
            val scrollCandleOffset = if (candleWidth > 0f) {
                (-scrollOffset / candleWidth).toInt().coerceIn(0, maxScrollCandles)
            } else 0

            val startIndex = (totalCandles - effectiveVisibleCount - scrollCandleOffset).coerceAtLeast(0)
            val endIndex = (startIndex + effectiveVisibleCount).coerceAtMost(totalCandles)

            if (startIndex >= endIndex || startIndex >= candles.size) return@Canvas
            val visibleCandles = candles.subList(startIndex, endIndex)
            if (visibleCandles.isEmpty()) return@Canvas

            var minPrice = visibleCandles.minOf { it.low }
            var maxPrice = visibleCandles.maxOf { it.high }
            val maxVolume = visibleCandles.maxOf { it.volume }.coerceAtLeast(0.0001)

            // Add margin to price scale
            val priceRange = (maxPrice - minPrice).coerceAtLeast(if (maxPrice > 0.0) maxPrice * 0.002 else 1.0)
            minPrice -= priceRange * 0.05
            maxPrice += priceRange * 0.05
            val currentPriceRange = (maxPrice - minPrice).coerceAtLeast(0.00000001)
            activeMinPrice = minPrice
            activeMaxPrice = maxPrice
            activeChartHeight = chartHeight

            fun priceToY(price: Double): Float {
                if (chartHeight <= 0f) return 0f
                val rawY = chartHeight * (1f - ((price - minPrice) / currentPriceRange).toFloat())
                return rawY.coerceIn(0f, chartHeight)
            }

            fun yToPrice(y: Float): Double {
                if (chartHeight <= 0f) return minPrice
                val ratio = 1.0 - (y.toDouble() / chartHeight.toDouble()).coerceIn(0.0, 1.0)
                return minPrice + ratio * currentPriceRange
            }

            fun volumeToY(vol: Double): Float {
                val ratio = (vol / maxVolume).toFloat().coerceIn(0f, 1f)
                return (canvasHeight - bottomAxisHeight - (volumeHeight * ratio)).coerceAtLeast(0f)
            }

            // Draw Background Grid
            drawGrid(chartWidth, chartHeight, minPrice, maxPrice, rightAxisWidth, bottomAxisHeight)

            // 1. Draw Volume Bars
            visibleCandles.forEachIndexed { i, c ->
                val x = i * candleWidth + candleWidth / 2f
                val volY = volumeToY(c.volume)
                val baseVolY = canvasHeight - bottomAxisHeight
                val color = if (c.isBullish) BullishGreen.copy(alpha = 0.35f) else BearishRed.copy(alpha = 0.35f)

                drawRect(
                    color = color,
                    topLeft = Offset(x - candleBodyWidth / 2f, volY),
                    size = Size(candleBodyWidth, baseVolY - volY)
                )
            }

            // 2. Draw Candlesticks
            visibleCandles.forEachIndexed { i, c ->
                val x = i * candleWidth + candleWidth / 2f
                val openY = priceToY(c.open)
                val closeY = priceToY(c.close)
                val highY = priceToY(c.high)
                val lowY = priceToY(c.low)

                val isGreen = c.isBullish
                val color = if (isGreen) BullishGreen else BearishRed

                // Wick
                drawLine(
                    color = color,
                    start = Offset(x, highY),
                    end = Offset(x, lowY),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Body
                val topY = min(openY, closeY)
                val bodyHeight = max(abs(closeY - openY), 2f)
                drawRect(
                    color = color,
                    topLeft = Offset(x - candleBodyWidth / 2f, topY),
                    size = Size(candleBodyWidth, bodyHeight)
                )
            }

            // 3. Draw Indicators
            if (showEma && candles.size >= 7) {
                drawEmaLine(candles, startIndex, endIndex, 7, candleWidth, AccentCyan, ::priceToY)
                if (candles.size >= 25) {
                    drawEmaLine(candles, startIndex, endIndex, 25, candleWidth, AccentYellow, ::priceToY)
                }
                if (candles.size >= 99) {
                    drawEmaLine(candles, startIndex, endIndex, 99, candleWidth, AccentPurple, ::priceToY)
                }
            }

            if (showVwap) {
                drawVwapLine(candles, startIndex, endIndex, candleWidth, AccentOrange, ::priceToY)
            }

            // 4. Draw Smart Money Concepts & Overlays
            if (showOverlays && report != null) {
                val smc = report.smcAnalysis

                // 4.1 FVG / IFVG / BPR System
                if (smcLayers.showFvg) {
                    smc.fvgs.forEach { fvg ->
                        if (fvg.top > minPrice && fvg.bottom < maxPrice) {
                            val topY = priceToY(fvg.top)
                            val botY = priceToY(fvg.bottom)
                            val fvgColor = when (fvg.type) {
                                com.example.signalx.data.model.FvgType.BULLISH -> BullishGreen.copy(alpha = 0.18f)
                                com.example.signalx.data.model.FvgType.BEARISH -> BearishRed.copy(alpha = 0.18f)
                                com.example.signalx.data.model.FvgType.INVERSE_FVG -> AccentPurple.copy(alpha = 0.22f)
                                com.example.signalx.data.model.FvgType.BALANCED_PRICE_RANGE -> AccentOrange.copy(alpha = 0.25f)
                            }
                            drawRect(
                                color = fvgColor,
                                topLeft = Offset(0f, topY),
                                size = Size(chartWidth, abs(botY - topY).coerceAtLeast(1f))
                            )
                            // Midpoint dotted line
                            val midY = priceToY(fvg.midpoint)
                            drawLine(
                                color = fvgColor.copy(alpha = 0.5f),
                                start = Offset(0f, midY),
                                end = Offset(chartWidth, midY),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                            )
                            // Label
                            drawContext.canvas.nativeCanvas.drawText(
                                "${fvg.type.name} (${fvg.status.name} ${fvg.fillPercent.toInt()}%)",
                                8.dp.toPx(),
                                midY - 2.dp.toPx(),
                                Paint().apply {
                                    color = TextSecondary.toArgb()
                                    textSize = 8.sp.toPx()
                                }
                            )
                        }
                    }
                }

                // 4.2 Order Blocks, Breakers, and Mitigation Blocks
                if (smcLayers.showOrderBlocks) {
                    smc.orderBlocks.forEach { ob ->
                        if (ob.top > minPrice && ob.bottom < maxPrice) {
                            val topY = priceToY(ob.top)
                            val botY = priceToY(ob.bottom)
                            val obColor = when (ob.type) {
                                com.example.signalx.data.model.OrderBlockType.BULLISH_OB -> BullishGreen.copy(alpha = 0.25f)
                                com.example.signalx.data.model.OrderBlockType.BEARISH_OB -> BearishRed.copy(alpha = 0.25f)
                                com.example.signalx.data.model.OrderBlockType.BREAKER_BLOCK -> AccentOrange.copy(alpha = 0.30f)
                                com.example.signalx.data.model.OrderBlockType.MITIGATION_BLOCK -> AccentCyan.copy(alpha = 0.25f)
                                com.example.signalx.data.model.OrderBlockType.REJECTION_BLOCK -> AccentYellow.copy(alpha = 0.25f)
                            }
                            drawRect(
                                color = obColor,
                                topLeft = Offset(0f, topY),
                                size = Size(chartWidth, abs(botY - topY).coerceAtLeast(2f))
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                "${ob.type.name} [${if (ob.isStrong) "STRONG" else "NORMAL"}]",
                                12.dp.toPx(),
                                topY + 10.dp.toPx(),
                                Paint().apply {
                                    color = TextPrimary.toArgb()
                                    textSize = 8.sp.toPx()
                                }
                            )
                        }
                    }
                }

                // 4.3 Market Structure: BOS, CHoCH, MSS
                if (smcLayers.showStructureBreaks) {
                    smc.structureBreaks.forEach { sb ->
                        if (sb.breakPrice in minPrice..maxPrice) {
                            val y = priceToY(sb.breakPrice)
                            val color = when (sb.type) {
                                com.example.signalx.data.model.StructureBreakType.BOS -> if (sb.isBullish) BullishGreen else BearishRed
                                com.example.signalx.data.model.StructureBreakType.CHOCH -> AccentYellow
                                com.example.signalx.data.model.StructureBreakType.MSS -> AccentCyan
                            }
                            drawLine(
                                color = color,
                                start = Offset(0f, y),
                                end = Offset(chartWidth, y),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = if (sb.confirmedByClose) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                "${sb.type.name} (${if (sb.isBullish) "BULL" else "BEAR"}) [${if (sb.confirmedByClose) "CLOSE CONFIRMED" else "WICK"}]",
                                chartWidth * 0.4f,
                                y - 3.dp.toPx(),
                                Paint().apply {
                                    this.color = color.toArgb()
                                    textSize = 8.5f.sp.toPx()
                                    isFakeBoldText = true
                                }
                            )
                        }
                    }
                }

                // 4.4 Liquidity Pools, Sweeps, EQH, EQL, BSL, SSL
                if (smcLayers.showLiquidity) {
                    smc.liquidities.forEach { liq ->
                        if (liq.price in minPrice..maxPrice) {
                            val y = priceToY(liq.price)
                            val color = if (liq.isSweep) AccentOrange else AccentYellow.copy(alpha = 0.7f)
                            drawLine(
                                color = color,
                                start = Offset(0f, y),
                                end = Offset(chartWidth, y),
                                strokeWidth = if (liq.isSweep) 2.dp.toPx() else 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                "⚡ ${liq.label} @ ${PriceFormatter.formatPrice(symbol, marketType, liq.price)}",
                                chartWidth * 0.15f,
                                y - 3.dp.toPx(),
                                Paint().apply {
                                    this.color = color.toArgb()
                                    textSize = 8.sp.toPx()
                                }
                            )
                        }
                    }
                }

                // 4.5 Premium, Discount, Equilibrium (50%) & OTE
                if (smcLayers.showPremiumDiscount && smc.premiumDiscount != null) {
                    val pd = smc.premiumDiscount
                    if (pd.equilibrium50 in minPrice..maxPrice) {
                        val eqY = priceToY(pd.equilibrium50)
                        drawLine(
                            color = AccentYellow,
                            start = Offset(0f, eqY),
                            end = Offset(chartWidth, eqY),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "EQUILIBRIUM 50% (${pd.currentZone})",
                            chartWidth * 0.65f,
                            eqY - 3.dp.toPx(),
                            Paint().apply {
                                color = AccentYellow.toArgb()
                                textSize = 8.sp.toPx()
                            }
                        )
                    }
                }

                // 4.6 Volume Profile POC, VAH, VAL
                if (smcLayers.showVolumeProfile) {
                    val vp = report.volumeProfile
                    if (vp.poc in minPrice..maxPrice) {
                        val pocY = priceToY(vp.poc)
                        drawLine(
                            color = AccentOrange,
                            start = Offset(0f, pocY),
                            end = Offset(chartWidth, pocY),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "POC (Point of Control) ${PriceFormatter.formatPrice(symbol, marketType, vp.poc)}",
                            chartWidth * 0.05f,
                            pocY - 3.dp.toPx(),
                            Paint().apply {
                                color = AccentOrange.toArgb()
                                textSize = 8.5f.sp.toPx()
                                isFakeBoldText = true
                            }
                        )
                    }
                    if (vp.vah in minPrice..maxPrice) {
                        val vahY = priceToY(vp.vah)
                        drawLine(
                            color = AccentCyan.copy(alpha = 0.6f),
                            start = Offset(0f, vahY),
                            end = Offset(chartWidth, vahY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                        )
                        drawContext.canvas.nativeCanvas.drawText("VAH 70%", chartWidth * 0.85f, vahY - 2.dp.toPx(), Paint().apply { color = AccentCyan.toArgb(); textSize = 8.sp.toPx() })
                    }
                    if (vp.valLevel in minPrice..maxPrice) {
                        val valY = priceToY(vp.valLevel)
                        drawLine(
                            color = AccentCyan.copy(alpha = 0.6f),
                            start = Offset(0f, valY),
                            end = Offset(chartWidth, valY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                        )
                        drawContext.canvas.nativeCanvas.drawText("VAL 70%", chartWidth * 0.85f, valY - 2.dp.toPx(), Paint().apply { color = AccentCyan.toArgb(); textSize = 8.sp.toPx() })
                    }
                }

                // 4.7 Support and Resistance
                if (smcLayers.showSupportResistance) {
                    if (report.keySupport in minPrice..maxPrice) {
                        val sY = priceToY(report.keySupport)
                        drawLine(
                            color = BullishGreen.copy(alpha = 0.7f),
                            start = Offset(0f, sY),
                            end = Offset(chartWidth, sY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    }
                    if (report.keyResistance in minPrice..maxPrice) {
                        val rY = priceToY(report.keyResistance)
                        drawLine(
                            color = BearishRed.copy(alpha = 0.7f),
                            start = Offset(0f, rY),
                            end = Offset(chartWidth, rY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    }
                }

                // 4.8 SMT Divergence marker
                if (smcLayers.showSmt && smc.smtDivergence?.isDetected == true) {
                    val smt = smc.smtDivergence
                    val smtColor = if (smt.isBullish) BullishGreen else BearishRed
                    drawContext.canvas.nativeCanvas.drawText(
                        "SMT: ${smt.description}",
                        12.dp.toPx(),
                        24.dp.toPx(),
                        Paint().apply {
                            color = smtColor.toArgb()
                            textSize = 9.sp.toPx()
                            isFakeBoldText = true
                        }
                    )
                }

                // 4.9 Trading Decision Entry, SL, TP Lines
                if (smcLayers.showEntrySlTp && decision != null && decision.stopLoss > 0.0) {
                    val slY = priceToY(decision.stopLoss)
                    val tp1Y = priceToY(decision.takeProfit1)
                    val tp2Y = priceToY(decision.takeProfit2)
                    val tp3Y = priceToY(decision.takeProfit3)
                    val entryY = priceToY(decision.entryMin)

                    // Entry Line
                    if (decision.entryMin in minPrice..maxPrice) {
                        drawLine(color = AccentYellow, start = Offset(0f, entryY), end = Offset(chartWidth, entryY), strokeWidth = 1.5.dp.toPx())
                        drawContext.canvas.nativeCanvas.drawText("ENTRY [${decision.signal.name}]", chartWidth * 0.02f, entryY - 3.dp.toPx(), Paint().apply { color = AccentYellow.toArgb(); textSize = 8.5f.sp.toPx(); isFakeBoldText = true })
                    }
                    // Stop Loss line
                    if (decision.stopLoss in minPrice..maxPrice) {
                        drawLine(color = BearishRed, start = Offset(0f, slY), end = Offset(chartWidth, slY), strokeWidth = 1.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f))
                        drawContext.canvas.nativeCanvas.drawText("STOP LOSS (R:R 1:${String.format(Locale.US, "%.1f", decision.riskReward1)})", chartWidth * 0.02f, slY - 3.dp.toPx(), Paint().apply { color = BearishRed.toArgb(); textSize = 8.5f.sp.toPx() })
                    }
                    // Take Profit lines
                    if (decision.takeProfit1 in minPrice..maxPrice) {
                        drawLine(color = BullishGreen, start = Offset(0f, tp1Y), end = Offset(chartWidth, tp1Y), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f))
                        drawContext.canvas.nativeCanvas.drawText("TP1", chartWidth * 0.85f, tp1Y - 2.dp.toPx(), Paint().apply { color = BullishGreen.toArgb(); textSize = 8.sp.toPx() })
                    }
                    if (decision.takeProfit2 in minPrice..maxPrice) {
                        drawLine(color = BullishGreen.copy(alpha = 0.8f), start = Offset(0f, tp2Y), end = Offset(chartWidth, tp2Y), strokeWidth = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
                        drawContext.canvas.nativeCanvas.drawText("TP2", chartWidth * 0.85f, tp2Y - 2.dp.toPx(), Paint().apply { color = BullishGreen.toArgb(); textSize = 8.sp.toPx() })
                    }
                    if (decision.takeProfit3 in minPrice..maxPrice) {
                        drawLine(color = BullishGreen.copy(alpha = 0.6f), start = Offset(0f, tp3Y), end = Offset(chartWidth, tp3Y), strokeWidth = 1.0.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                        drawContext.canvas.nativeCanvas.drawText("TP3", chartWidth * 0.85f, tp3Y - 2.dp.toPx(), Paint().apply { color = BullishGreen.toArgb(); textSize = 8.sp.toPx() })
                    }
                }
            }

            // 4b. Draw User Drawings (Trendline, H-line, Ray, Rect, Fibonacci, S/R, Long/Short)
            drawings.forEach { drawing ->
                when (drawing) {
                    is DrawingItem.HorizontalLine -> {
                        if (drawing.price in minPrice..maxPrice) {
                            val y = priceToY(drawing.price)
                            drawLine(
                                color = AccentCyan,
                                start = Offset(0f, y),
                                end = Offset(chartWidth, y),
                                strokeWidth = 1.2.dp.toPx()
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                "${drawing.label}: ${PriceFormatter.formatPrice(symbol, marketType, drawing.price)}",
                                12.dp.toPx(),
                                y - 4.dp.toPx(),
                                Paint().apply {
                                    color = AccentCyan.toArgb()
                                    textSize = 9.sp.toPx()
                                }
                            )
                        }
                    }
                    is DrawingItem.SupportResistanceLine -> {
                        if (drawing.price in minPrice..maxPrice) {
                            val y = priceToY(drawing.price)
                            val color = if (drawing.isSupport) BullishGreen else BearishRed
                            drawLine(
                                color = color,
                                start = Offset(0f, y),
                                end = Offset(chartWidth, y),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            )
                        }
                    }
                    is DrawingItem.Trendline -> {
                        val y1 = priceToY(drawing.startPrice)
                        val y2 = priceToY(drawing.endPrice)
                        drawLine(
                            color = AccentYellow,
                            start = Offset(0f, y1),
                            end = Offset(chartWidth, y2),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                    is DrawingItem.Ray -> {
                        val y1 = priceToY(drawing.startPrice)
                        val y2 = priceToY(drawing.endPrice)
                        drawLine(
                            color = AccentOrange,
                            start = Offset(chartWidth * 0.2f, y1),
                            end = Offset(chartWidth, y2),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                    is DrawingItem.RectangleZone -> {
                        val topY = priceToY(maxOf(drawing.startPrice, drawing.endPrice))
                        val botY = priceToY(minOf(drawing.startPrice, drawing.endPrice))
                        drawRect(
                            color = AccentPurple.copy(alpha = 0.2f),
                            topLeft = Offset(0f, topY),
                            size = Size(chartWidth, abs(botY - topY).coerceAtLeast(2f))
                        )
                    }
                    is DrawingItem.Fibonacci -> {
                        val fibLevels = listOf(
                            "0.0" to drawing.level0,
                            "0.236" to drawing.level236,
                            "0.382" to drawing.level382,
                            "0.500" to drawing.level500,
                            "0.618" to drawing.level618,
                            "0.786" to drawing.level786,
                            "1.000" to drawing.level1000
                        )
                        fibLevels.forEach { (label, levelPrice) ->
                            if (levelPrice in minPrice..maxPrice) {
                                val y = priceToY(levelPrice)
                                val fibColor = if (label == "0.618" || label == "0.500") AccentOrange else AccentCyan.copy(alpha = 0.6f)
                                drawLine(
                                    color = fibColor,
                                    start = Offset(0f, y),
                                    end = Offset(chartWidth, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    "Fib $label (${PriceFormatter.formatPrice(symbol, marketType, levelPrice)})",
                                    12.dp.toPx(),
                                    y - 3.dp.toPx(),
                                    Paint().apply {
                                        color = fibColor.toArgb()
                                        textSize = 8.sp.toPx()
                                    }
                                )
                            }
                        }
                    }
                    is DrawingItem.LongTool -> {
                        val entryY = priceToY(drawing.entryPrice)
                        val tpY = priceToY(drawing.takeProfit)
                        val slY = priceToY(drawing.stopLoss)
                        drawRect(
                            color = BullishGreen.copy(alpha = 0.15f),
                            topLeft = Offset(chartWidth * 0.3f, minOf(entryY, tpY)),
                            size = Size(chartWidth * 0.6f, abs(tpY - entryY))
                        )
                        drawRect(
                            color = BearishRed.copy(alpha = 0.15f),
                            topLeft = Offset(chartWidth * 0.3f, minOf(entryY, slY)),
                            size = Size(chartWidth * 0.6f, abs(slY - entryY))
                        )
                        drawLine(
                            color = TextPrimary,
                            start = Offset(chartWidth * 0.3f, entryY),
                            end = Offset(chartWidth * 0.9f, entryY),
                            strokeWidth = 1.2.dp.toPx()
                        )
                    }
                    is DrawingItem.ShortTool -> {
                        val entryY = priceToY(drawing.entryPrice)
                        val tpY = priceToY(drawing.takeProfit)
                        val slY = priceToY(drawing.stopLoss)
                        drawRect(
                            color = BearishRed.copy(alpha = 0.15f),
                            topLeft = Offset(chartWidth * 0.3f, minOf(entryY, slY)),
                            size = Size(chartWidth * 0.6f, abs(slY - entryY))
                        )
                        drawRect(
                            color = BullishGreen.copy(alpha = 0.15f),
                            topLeft = Offset(chartWidth * 0.3f, minOf(entryY, tpY)),
                            size = Size(chartWidth * 0.6f, abs(tpY - entryY))
                        )
                        drawLine(
                            color = TextPrimary,
                            start = Offset(chartWidth * 0.3f, entryY),
                            end = Offset(chartWidth * 0.9f, entryY),
                            strokeWidth = 1.2.dp.toPx()
                        )
                    }
                }
            }

            // 5. Draw Live Price Line & Right Badge
            val livePriceToDraw = if (livePrice > 0.0) livePrice else (candles.lastOrNull()?.close ?: 0.0)
            if (livePriceToDraw in minPrice..maxPrice) {
                val liveY = priceToY(livePriceToDraw)
                drawLine(
                    color = AccentYellow,
                    start = Offset(0f, liveY),
                    end = Offset(chartWidth, liveY),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )

                // Right badge for live price
                drawRect(
                    color = AccentYellow,
                    topLeft = Offset(chartWidth, liveY - 10.dp.toPx()),
                    size = Size(rightAxisWidth, 20.dp.toPx())
                )

                drawContext.canvas.nativeCanvas.drawText(
                    PriceFormatter.formatPrice(symbol, marketType, livePriceToDraw),
                    chartWidth + 4.dp.toPx(),
                    liveY + 4.dp.toPx(),
                    Paint().apply {
                        color = Color(0xFF0B0E11).toArgb()
                        textSize = 9.sp.toPx()
                        isFakeBoldText = true
                    }
                )
            }

            // 6. Crosshair & Interactive HUD
            crosshairOffset?.let { pos ->
                if (pos.x in 0f..chartWidth && pos.y in 0f..canvasHeight - bottomAxisHeight) {
                    // Vertical crosshair
                    drawLine(
                        color = TextSecondary.copy(alpha = 0.7f),
                        start = Offset(pos.x, 0f),
                        end = Offset(pos.x, canvasHeight - bottomAxisHeight),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                    // Horizontal crosshair
                    drawLine(
                        color = TextSecondary.copy(alpha = 0.7f),
                        start = Offset(0f, pos.y),
                        end = Offset(chartWidth, pos.y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    // Find corresponding candle
                    if (visibleCandles.isNotEmpty() && candleWidth > 0f) {
                        val maxIdx = (visibleCandles.size - 1).coerceAtLeast(0)
                        val candleIdx = (pos.x / candleWidth).toInt().coerceIn(0, maxIdx)
                        val c = visibleCandles.getOrNull(candleIdx)
                        selectedCandle = c
                    }

                    // Price on right axis
                    val hoveredPrice = yToPrice(pos.y)
                    drawRect(
                        color = DarkCard,
                        topLeft = Offset(chartWidth, pos.y - 10.dp.toPx()),
                        size = Size(rightAxisWidth, 20.dp.toPx())
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        PriceFormatter.formatPrice(symbol, marketType, hoveredPrice),
                        chartWidth + 4.dp.toPx(),
                        pos.y + 4.dp.toPx(),
                        Paint().apply {
                            color = TextPrimary.toArgb()
                            textSize = 9.sp.toPx()
                        }
                    )
                }
            }
        }

        // Top HUD Overlay showing OHLC + Change
        val activeCandle = selectedCandle ?: candles.lastOrNull()
        if (activeCandle != null) {
            val change = activeCandle.close - activeCandle.open
            val changePct = if (activeCandle.open > 0) (change / activeCandle.open) * 100.0 else 0.0
            val isBull = change >= 0
            val color = if (isBull) BullishGreen else BearishRed

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .background(Color(0xCC161A1E))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "O:${PriceFormatter.formatPrice(symbol, marketType, activeCandle.open)}  H:${PriceFormatter.formatPrice(symbol, marketType, activeCandle.high)}  L:${PriceFormatter.formatPrice(symbol, marketType, activeCandle.low)}  C:${PriceFormatter.formatPrice(symbol, marketType, activeCandle.close)}  ${if (isBull) "+" else ""}${String.format(Locale.US, "%.2f", changePct)}%  Vol:${formatVolumeShort(activeCandle.volume)}",
                    color = color,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

private fun DrawScope.drawGrid(
    chartWidth: Float,
    chartHeight: Float,
    minPrice: Double,
    maxPrice: Double,
    rightAxisWidth: Float,
    bottomAxisHeight: Float
) {
    val horizontalLines = 5
    val priceStep = (maxPrice - minPrice) / horizontalLines

    for (i in 0..horizontalLines) {
        val y = chartHeight * (i.toFloat() / horizontalLines)
        val price = maxPrice - i * priceStep

        drawLine(
            color = DarkBorder.copy(alpha = 0.5f),
            start = Offset(0f, y),
            end = Offset(chartWidth, y),
            strokeWidth = 0.8.dp.toPx()
        )

        // Right axis price text
        drawContext.canvas.nativeCanvas.drawText(
            if (price >= 1000.0) String.format(Locale.US, "%.1f", price) else if (price >= 1.0) String.format(Locale.US, "%.2f", price) else String.format(Locale.US, "%.5f", price),
            chartWidth + 4.dp.toPx(),
            y + 4.dp.toPx(),
            Paint().apply {
                color = TextMuted.toArgb()
                textSize = 9.sp.toPx()
            }
        )
    }
}

private fun DrawScope.drawEmaLine(
    candles: List<Candle>,
    startIndex: Int,
    endIndex: Int,
    period: Int,
    candleWidth: Float,
    color: Color,
    priceToY: (Double) -> Float
) {
    val emaValues = TechnicalIndicators.calculateEMA(candles, period)
    if (emaValues.isEmpty()) return

    val path = Path()
    var isStarted = false

    for (i in startIndex until endIndex) {
        if (i !in emaValues.indices) continue
        val x = (i - startIndex) * candleWidth + candleWidth / 2f
        val y = priceToY(emaValues[i])

        if (!isStarted) {
            path.moveTo(x, y)
            isStarted = true
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(path = path, color = color, style = Stroke(width = 1.5.dp.toPx()))
}

private fun DrawScope.drawVwapLine(
    candles: List<Candle>,
    startIndex: Int,
    endIndex: Int,
    candleWidth: Float,
    color: Color,
    priceToY: (Double) -> Float
) {
    val vwapValues = TechnicalIndicators.calculateVWAP(candles)
    if (vwapValues.isEmpty()) return

    val path = Path()
    var isStarted = false

    for (i in startIndex until endIndex) {
        if (i !in vwapValues.indices) continue
        val x = (i - startIndex) * candleWidth + candleWidth / 2f
        val y = priceToY(vwapValues[i])

        if (!isStarted) {
            path.moveTo(x, y)
            isStarted = true
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(path = path, color = color, style = Stroke(width = 1.5.dp.toPx()))
}

private fun formatVolumeShort(volume: Double): String {
    return if (volume >= 1_000_000.0) {
        String.format(Locale.US, "%.1fM", volume / 1_000_000.0)
    } else if (volume >= 1000.0) {
        String.format(Locale.US, "%.1fK", volume / 1000.0)
    } else {
        String.format(Locale.US, "%.1f", volume)
    }
}
