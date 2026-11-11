package com.example.signalx.engine.analysis

import com.example.signalx.data.model.Candle
import com.example.signalx.data.model.EnhancedFvg
import com.example.signalx.data.model.EnhancedLiquidity
import com.example.signalx.data.model.EnhancedOrderBlock
import com.example.signalx.data.model.EnhancedStructureBreak
import com.example.signalx.data.model.FvgStatus
import com.example.signalx.data.model.FvgType
import com.example.signalx.data.model.LiquidityType
import com.example.signalx.data.model.OrderBlockType
import com.example.signalx.data.model.PremiumDiscountRange
import com.example.signalx.data.model.SmcAnalysis
import com.example.signalx.data.model.SmtDivergence
import com.example.signalx.data.model.StructureBreakType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object SmartMoneyEngine {

    /**
     * Complete Smart Money Concepts Analysis:
     * - FVG (Bullish, Bearish, IFVG, BPR, fill %, mitigation)
     * - Order Blocks (Bullish, Bearish, Breakers, Mitigations, Rejections)
     * - Market Structure (BOS, CHoCH, MSS, Internal/External, Close vs Wick confirmation)
     * - Liquidity Sweeps, Equal Highs/Lows, BSL, SSL, Reclaims
     * - Premium / Discount zones with 50% Equilibrium & 61.8%/78.6% OTE
     * - SMT Divergence across correlated assets (e.g. BTC vs ETH/XRP)
     * - Consolidation & Expansion Momentum Zones
     */
    fun analyze(
        candles: List<Candle>,
        swings: List<SwingPoint> = if (candles.size >= 5) MarketStructureAnalyzer.detectSwingPoints(candles) else emptyList(),
        atr: Double = com.example.signalx.engine.indicators.TechnicalIndicators.calculateATR(candles, 14),
        currentPrice: Double = candles.lastOrNull()?.close ?: 0.0,
        comparisonCandles: List<Candle>? = null,
        comparisonSymbol: String = "BTCUSDT",
        activeSymbol: String = "ETHUSDT"
    ): SmcAnalysis {
        if (candles.size < 3) return SmcAnalysis()

        val effectiveSwings = if (swings.isNotEmpty()) swings else detectSimpleSwings(candles)
        val fvgs = detectEnhancedFvgs(candles, currentPrice)
        val orderBlocks = detectEnhancedOrderBlocks(candles, effectiveSwings, currentPrice, atr)
        val structureBreaks = detectEnhancedStructureBreaks(candles, effectiveSwings)
        val liquidities = detectEnhancedLiquidity(candles, effectiveSwings, atr)
        val premDisc = calculatePremiumDiscount(candles, effectiveSwings, currentPrice)
        val smt = detectSmtDivergence(candles, comparisonCandles, activeSymbol, comparisonSymbol)
        val (instMomentum, consolHigh, consolLow, isConsol) = analyzeMomentumAndZones(candles, atr)

        return SmcAnalysis(
            fvgs = fvgs,
            orderBlocks = orderBlocks,
            structureBreaks = structureBreaks,
            liquidities = liquidities,
            premiumDiscount = premDisc,
            smtDivergence = smt,
            institutionalMomentum = instMomentum,
            consolidationZoneHigh = consolHigh,
            consolidationZoneLow = consolLow,
            isConsolidation = isConsol
        )
    }

    fun analyze(
        candles: List<Candle>,
        comparisonCandles: List<Candle>?
    ): SmcAnalysis {
        return analyze(
            candles = candles,
            comparisonCandles = comparisonCandles,
            comparisonSymbol = "BTCUSDT",
            activeSymbol = "ETHUSDT"
        )
    }

    /**
     * 1. FVG System:
     * Detects 3-candle imbalance gaps (c1, c2, c3).
     * Calculates upper/lower boundaries, midpoint, age, fill percentage, and mitigation status.
     * Identifies Inverse FVGs (IFVG) and Balanced Price Ranges (BPR).
     */
    fun detectEnhancedFvgs(candles: List<Candle>, currentPrice: Double): List<EnhancedFvg> {
        val result = mutableListOf<EnhancedFvg>()
        if (candles.size < 3) return result

        val scanLimit = min(candles.size - 2, 80)
        val startIndex = max(1, candles.size - 1 - scanLimit)

        for (i in startIndex until (candles.size - 1)) {
            if (i < 1 || i + 1 >= candles.size) continue
            val c1 = candles[i - 1]
            val c2 = candles[i]
            val c3 = candles[i + 1]

            val c2Range = c2.totalRange.coerceAtLeast(0.0001)
            val isDisplacement = c2.bodySize / c2Range > 0.65

            // Bullish FVG: c3.low > c1.high
            if (c3.low > c1.high) {
                val top = c3.low
                val bottom = c1.high
                val fvgHeight = top - bottom
                if (fvgHeight > 0.0) {
                    val age = candles.size - 1 - i
                    // Calculate subsequent penetration & fill
                    var lowestPenetration = top
                    for (k in (i + 2) until candles.size) {
                        lowestPenetration = min(lowestPenetration, candles[k].low)
                    }

                    val fillRatio = if (lowestPenetration <= top) {
                        ((top - lowestPenetration) / fvgHeight).coerceIn(0.0, 1.0)
                    } else 0.0

                    val status = when {
                        lowestPenetration < bottom -> FvgStatus.INVALIDATED
                        fillRatio >= 1.0 -> FvgStatus.FULLY_FILLED
                        fillRatio >= 0.50 -> FvgStatus.MITIGATED
                        fillRatio > 0.0 -> FvgStatus.PARTIALLY_FILLED
                        else -> FvgStatus.UNTESTED
                    }

                    // Check if Inverted (price broke down through bullish FVG and now treats it as resistance)
                    val type = if (status == FvgStatus.INVALIDATED && currentPrice < bottom) {
                        FvgType.INVERSE_FVG
                    } else {
                        FvgType.BULLISH
                    }

                    result.add(
                        EnhancedFvg(
                            id = "fvg_bull_$i",
                            type = type,
                            top = top,
                            bottom = bottom,
                            candleIndex = i,
                            timestamp = c2.timestamp,
                            status = status,
                            fillPercent = fillRatio * 100.0,
                            ageCandles = age,
                            isDisplacementFvg = isDisplacement
                        )
                    )
                }
            }
            // Bearish FVG: c3.high < c1.low
            else if (c3.high < c1.low) {
                val top = c1.low
                val bottom = c3.high
                val fvgHeight = top - bottom
                if (fvgHeight > 0.0) {
                    val age = candles.size - 1 - i
                    var highestPenetration = bottom
                    for (k in (i + 2) until candles.size) {
                        highestPenetration = max(highestPenetration, candles[k].high)
                    }

                    val fillRatio = if (highestPenetration >= bottom) {
                        ((highestPenetration - bottom) / fvgHeight).coerceIn(0.0, 1.0)
                    } else 0.0

                    val status = when {
                        highestPenetration > top -> FvgStatus.INVALIDATED
                        fillRatio >= 1.0 -> FvgStatus.FULLY_FILLED
                        fillRatio >= 0.50 -> FvgStatus.MITIGATED
                        fillRatio > 0.0 -> FvgStatus.PARTIALLY_FILLED
                        else -> FvgStatus.UNTESTED
                    }

                    val type = if (status == FvgStatus.INVALIDATED && currentPrice > top) {
                        FvgType.INVERSE_FVG
                    } else {
                        FvgType.BEARISH
                    }

                    result.add(
                        EnhancedFvg(
                            id = "fvg_bear_$i",
                            type = type,
                            top = top,
                            bottom = bottom,
                            candleIndex = i,
                            timestamp = c2.timestamp,
                            status = status,
                            fillPercent = fillRatio * 100.0,
                            ageCandles = age,
                            isDisplacementFvg = isDisplacement
                        )
                    )
                }
            }
        }

        // Detect Balanced Price Ranges (overlapping Bullish & Bearish FVGs in close proximity)
        val bprs = mutableListOf<EnhancedFvg>()
        for (i in result.indices) {
            val f1 = result[i]
            for (j in (i + 1) until result.size) {
                val f2 = result[j]
                if (f1.type != f2.type && f1.type != FvgType.INVERSE_FVG && f2.type != FvgType.INVERSE_FVG) {
                    val overlapTop = min(f1.top, f2.top)
                    val overlapBottom = max(f1.bottom, f2.bottom)
                    if (overlapTop > overlapBottom) {
                        bprs.add(
                            EnhancedFvg(
                                id = "bpr_${f1.candleIndex}_${f2.candleIndex}",
                                type = FvgType.BALANCED_PRICE_RANGE,
                                top = overlapTop,
                                bottom = overlapBottom,
                                candleIndex = max(f1.candleIndex, f2.candleIndex),
                                timestamp = max(f1.timestamp, f2.timestamp),
                                status = FvgStatus.TESTED,
                                fillPercent = 50.0,
                                ageCandles = min(f1.ageCandles, f2.ageCandles),
                                isDisplacementFvg = true
                            )
                        )
                    }
                }
            }
        }
        result.addAll(bprs)
        return result.takeLast(10)
    }

    /**
     * 2. Order Block System:
     * Identifies last opposing candle prior to displacement breakout.
     * Detects Breakers (failed OB broken and flipped), Mitigations, and Rejection blocks.
     */
    fun detectEnhancedOrderBlocks(
        candles: List<Candle>,
        swings: List<SwingPoint>,
        currentPrice: Double,
        atr: Double
    ): List<EnhancedOrderBlock> {
        val result = mutableListOf<EnhancedOrderBlock>()
        if (candles.size < 6) return result

        val recentSwings = swings.takeLast(6)
        for (s in recentSwings) {
            val idx = s.index
            if (idx !in 1 until (candles.size - 2)) continue
            val c = candles[idx]

            val isBullish = !s.isHigh
            val top = if (isBullish) c.high else c.high
            val bottom = if (isBullish) c.low else c.low

            // Check displacement following origin candle
            val next1 = candles[idx + 1]
            val next2 = candles[min(idx + 2, candles.size - 1)]
            val displacementMove = if (isBullish) {
                (max(next1.close, next2.close) - c.close)
            } else {
                (c.close - min(next1.close, next2.close))
            }
            val displacementStrength = (displacementMove / c.close) * 100.0
            val isStrong = displacementMove >= atr * 1.2

            // Retest and invalidation check
            var isRetested = false
            var isBrokenThrough = false
            for (k in (idx + 3) until candles.size) {
                val testCandle = candles[k]
                if (isBullish) {
                    if (testCandle.low <= top && testCandle.low >= bottom) isRetested = true
                    if (testCandle.close < bottom) isBrokenThrough = true
                } else {
                    if (testCandle.high >= bottom && testCandle.high <= top) isRetested = true
                    if (testCandle.close > top) isBrokenThrough = true
                }
            }

            val type = when {
                isBrokenThrough && isBullish && currentPrice < bottom -> OrderBlockType.BREAKER_BLOCK
                isBrokenThrough && !isBullish && currentPrice > top -> OrderBlockType.BREAKER_BLOCK
                isRetested && !isBrokenThrough -> OrderBlockType.MITIGATION_BLOCK
                (if (isBullish) c.lowerWick else c.upperWick) >= c.bodySize * 1.5 -> OrderBlockType.REJECTION_BLOCK
                isBullish -> OrderBlockType.BULLISH_OB
                else -> OrderBlockType.BEARISH_OB
            }

            result.add(
                EnhancedOrderBlock(
                    id = "ob_${s.index}",
                    type = type,
                    top = top,
                    bottom = bottom,
                    originCandleIndex = idx,
                    timestamp = c.timestamp,
                    volume = c.volume,
                    displacementStrength = displacementStrength,
                    isRetested = isRetested,
                    isInvalidated = isBrokenThrough,
                    isStrong = isStrong
                )
            )
        }
        return result.takeLast(6)
    }

    /**
     * 3. Market Structure System:
     * Differentiates BOS, CHoCH, and MSS.
     * Evaluates whether break was confirmed by candle close vs wick penetration only.
     */
    fun detectEnhancedStructureBreaks(
        candles: List<Candle>,
        swings: List<SwingPoint>
    ): List<EnhancedStructureBreak> {
        val result = mutableListOf<EnhancedStructureBreak>()
        if (swings.size < 2 || candles.isEmpty()) return result

        val lastCandles = candles.takeLast(30)
        val recentHighSwings = swings.filter { it.isHigh }.takeLast(4)
        val recentLowSwings = swings.filter { !it.isHigh }.takeLast(4)

        for (highSwing in recentHighSwings) {
            val breakingCandle = lastCandles.firstOrNull { it.timestamp > highSwing.timestamp && it.high > highSwing.price }
            if (breakingCandle != null) {
                val confirmedClose = breakingCandle.close > highSwing.price
                val isBOS = highSwing.label == "HH"
                val type = if (isBOS) StructureBreakType.BOS else if (confirmedClose) StructureBreakType.MSS else StructureBreakType.CHOCH

                result.add(
                    EnhancedStructureBreak(
                        type = type,
                        isBullish = true,
                        breakPrice = highSwing.price,
                        confirmedByClose = confirmedClose,
                        candleIndex = candles.indexOf(breakingCandle).coerceAtLeast(0),
                        timestamp = breakingCandle.timestamp,
                        isInternal = highSwing.label.contains("L")
                    )
                )
                break
            }
        }

        for (lowSwing in recentLowSwings) {
            val breakingCandle = lastCandles.firstOrNull { it.timestamp > lowSwing.timestamp && it.low < lowSwing.price }
            if (breakingCandle != null) {
                val confirmedClose = breakingCandle.close < lowSwing.price
                val isBOS = lowSwing.label == "LL"
                val type = if (isBOS) StructureBreakType.BOS else if (confirmedClose) StructureBreakType.MSS else StructureBreakType.CHOCH

                result.add(
                    EnhancedStructureBreak(
                        type = type,
                        isBullish = false,
                        breakPrice = lowSwing.price,
                        confirmedByClose = confirmedClose,
                        candleIndex = candles.indexOf(breakingCandle).coerceAtLeast(0),
                        timestamp = breakingCandle.timestamp,
                        isInternal = lowSwing.label.contains("H")
                    )
                )
                break
            }
        }
        return result
    }

    /**
     * 4. Liquidity & Sweep Engine:
     * Equal Highs (EQH), Equal Lows (EQL), Buy-side Liquidity (BSL), Sell-side Liquidity (SSL),
     * and confirmed sweeps with wick penetration followed by close back inside.
     */
    fun detectEnhancedLiquidity(
        candles: List<Candle>,
        swings: List<SwingPoint>,
        atr: Double
    ): List<EnhancedLiquidity> {
        val result = mutableListOf<EnhancedLiquidity>()
        if (candles.size < 5 || swings.size < 2) return result

        val tolerance = atr * 0.12
        val highs = swings.filter { it.isHigh }
        val lows = swings.filter { !it.isHigh }
        val lastCandle = candles.last()

        // Equal Highs (EQH)
        for (i in highs.indices) {
            for (j in (i + 1) until highs.size) {
                if (abs(highs[i].price - highs[j].price) <= tolerance) {
                    result.add(
                        EnhancedLiquidity(
                            type = LiquidityType.EQUAL_HIGHS,
                            price = (highs[i].price + highs[j].price) / 2.0,
                            isHigh = true,
                            label = "EQH Liquidity Pool"
                        )
                    )
                    break
                }
            }
        }

        // Equal Lows (EQL)
        for (i in lows.indices) {
            for (j in (i + 1) until lows.size) {
                if (abs(lows[i].price - lows[j].price) <= tolerance) {
                    result.add(
                        EnhancedLiquidity(
                            type = LiquidityType.EQUAL_LOWS,
                            price = (lows[i].price + lows[j].price) / 2.0,
                            isHigh = false,
                            label = "EQL Liquidity Pool"
                        )
                    )
                    break
                }
            }
        }

        // BSL & SSL from prominent swings
        highs.lastOrNull()?.let { bsl ->
            result.add(
                EnhancedLiquidity(
                    type = LiquidityType.BUY_SIDE_LIQUIDITY,
                    price = bsl.price,
                    isHigh = true,
                    label = "BSL (Buy-Side Liquidity)"
                )
            )
        }
        lows.lastOrNull()?.let { ssl ->
            result.add(
                EnhancedLiquidity(
                    type = LiquidityType.SELL_SIDE_LIQUIDITY,
                    price = ssl.price,
                    isHigh = false,
                    label = "SSL (Sell-Side Liquidity)"
                )
            )
        }

        // Liquidity Sweeps on current candle
        for (h in highs.takeLast(3)) {
            if (lastCandle.high > h.price && lastCandle.close < h.price) {
                result.add(
                    EnhancedLiquidity(
                        type = LiquidityType.LIQUIDITY_SWEEP,
                        price = h.price,
                        sweepPrice = lastCandle.high,
                        isHigh = true,
                        isConfirmedReclaim = true,
                        isSweep = true,
                        timestamp = lastCandle.timestamp,
                        label = "Bearish Liquidity Sweep"
                    )
                )
            }
        }
        for (l in lows.takeLast(3)) {
            if (lastCandle.low < l.price && lastCandle.close > l.price) {
                result.add(
                    EnhancedLiquidity(
                        type = LiquidityType.LIQUIDITY_SWEEP,
                        price = l.price,
                        sweepPrice = lastCandle.low,
                        isHigh = false,
                        isConfirmedReclaim = true,
                        isSweep = true,
                        timestamp = lastCandle.timestamp,
                        label = "Bullish Liquidity Sweep"
                    )
                )
            }
        }

        return result.distinctBy { it.label + it.price.toInt() }
    }

    /**
     * 5. Premium & Discount Engine:
     * Calculates dealing range from major confirmed swing points.
     * Computes 50% Equilibrium and 61.8% / 78.6% Optimal Trade Entry (OTE) zones.
     */
    fun calculatePremiumDiscount(
        candles: List<Candle>,
        swings: List<SwingPoint>,
        currentPrice: Double
    ): PremiumDiscountRange? {
        if (candles.isEmpty()) return null
        val recentSwings = swings.takeLast(8)
        val high = recentSwings.filter { it.isHigh }.maxOfOrNull { it.price } ?: candles.maxOf { it.high }
        val low = recentSwings.filter { !it.isHigh }.minOfOrNull { it.price } ?: candles.minOf { it.low }
        val range = high - low
        if (range <= 0.0) return null

        val eq = low + range * 0.50
        val oteUpper = high - range * 0.618
        val oteLower = high - range * 0.786

        val zone = when {
            currentPrice > eq * 1.002 -> "PREMIUM"
            currentPrice < eq * 0.998 -> "DISCOUNT"
            else -> "EQUILIBRIUM"
        }

        return PremiumDiscountRange(
            dealingRangeHigh = high,
            dealingRangeLow = low,
            equilibrium50 = eq,
            oteUpper618 = oteUpper,
            oteLower786 = oteLower,
            currentZone = zone
        )
    }

    /**
     * 6. SMT Divergence Engine:
     * Compares correlated pair (e.g. BTC vs ETH/XRP) on synchronized timestamps.
     * Detects Bullish SMT (one asset makes Lower Low, other makes Higher Low)
     * and Bearish SMT (one asset makes Higher High, other makes Lower High).
     */
    fun detectSmtDivergence(
        candles: List<Candle>,
        comparisonCandles: List<Candle>?,
        activeSymbol: String,
        comparisonSymbol: String
    ): SmtDivergence? {
        if (comparisonCandles == null || comparisonCandles.size < 3 || candles.size < 3) return null
        if (activeSymbol == comparisonSymbol) return null

        val c1Recent = candles.takeLast(10)
        val c2Recent = comparisonCandles.takeLast(10)

        val c1First = c1Recent.first()
        val c1Last = c1Recent.last()
        val c2First = c2Recent.first()
        val c2Last = c2Recent.last()

        val c1MadeHigherHigh = c1Last.high > c1First.high
        val c2MadeHigherHigh = c2Last.high > c2First.high

        val c1MadeLowerLow = c1Last.low < c1First.low
        val c2MadeLowerLow = c2Last.low < c2First.low

        // Bearish SMT: Active made Higher High, but Benchmark made Lower High (failure to confirm)
        if (c1MadeHigherHigh && !c2MadeHigherHigh) {
            return SmtDivergence(
                isDetected = true,
                isBullish = false,
                primarySymbol = activeSymbol,
                comparisonSymbol = comparisonSymbol,
                description = "Bearish SMT: $activeSymbol made HH but $comparisonSymbol failed to confirm (made LH)",
                timestamp = c1Last.timestamp
            )
        }

        // Bullish SMT: Active made Lower Low, but Benchmark made Higher Low (absorption)
        if (c1MadeLowerLow && !c2MadeLowerLow) {
            return SmtDivergence(
                isDetected = true,
                isBullish = true,
                primarySymbol = activeSymbol,
                comparisonSymbol = comparisonSymbol,
                description = "Bullish SMT: $activeSymbol swept low (LL) while $comparisonSymbol held HL (absorption)",
                timestamp = c1Last.timestamp
            )
        }

        return null
    }

    /**
     * 7. Momentum & Institutional Zone Analysis:
     * Displacements, consolidations, and expansions.
     */
    private fun analyzeMomentumAndZones(
        candles: List<Candle>,
        atr: Double
    ): Tuple4<String, Double, Double, Boolean> {
        val last20 = candles.takeLast(20)
        val high20 = last20.maxOf { it.high }
        val low20 = last20.minOf { it.low }
        val range20 = high20 - low20

        val isConsolidation = range20 < (atr * 2.5)

        val last3 = candles.takeLast(3)
        val upBodies = last3.count { it.isBullish && it.bodySize > atr * 0.8 }
        val downBodies = last3.count { !it.isBullish && it.bodySize > atr * 0.8 }

        val momentum = when {
            upBodies >= 2 -> "BULLISH_DISPLACEMENT"
            downBodies >= 2 -> "BEARISH_DISPLACEMENT"
            isConsolidation -> "CONSOLIDATION_COMPRESSION"
            else -> "BALANCED_EXPANSION"
        }

        return Tuple4(momentum, high20, low20, isConsolidation)
    }

    private fun detectSimpleSwings(candles: List<Candle>): List<SwingPoint> {
        val list = mutableListOf<SwingPoint>()
        for (i in 1 until candles.size - 1) {
            val prev = candles[i - 1]
            val curr = candles[i]
            val next = candles[i + 1]
            if (curr.high >= prev.high && curr.high >= next.high) {
                list.add(SwingPoint(i, curr.timestamp, curr.high, true, "HH"))
            } else if (curr.low <= prev.low && curr.low <= next.low) {
                list.add(SwingPoint(i, curr.timestamp, curr.low, false, "LL"))
            }
        }
        return list
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
