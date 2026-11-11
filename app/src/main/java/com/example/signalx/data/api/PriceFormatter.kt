package com.example.signalx.data.api

import com.example.signalx.data.model.MarketType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

object PriceFormatter {

    // Store precision: key is "$marketType:$symbol", e.g. "SPOT:XRPUSDT" -> 4, "FUTURES:XRPUSDT" -> 4
    private val precisionMap = ConcurrentHashMap<String, Int>()

    fun setPrecision(symbol: String, marketType: MarketType, precision: Int) {
        val key = makeKey(symbol, marketType)
        precisionMap[key] = precision
    }

    fun getPrecision(symbol: String, marketType: MarketType): Int? {
        val key = makeKey(symbol, marketType)
        return precisionMap[key]
    }

    private fun makeKey(symbol: String, marketType: MarketType): String = "${marketType.name}:$symbol"

    /**
     * Primary price formatting function used across the ENTIRE application:
     * - Preserves every significant digit from the exchange.
     * - Uses exact symbol price precision (pricePrecision for Spot, pricePlace for Futures).
     * - Pads trailing zeros to exact precision (e.g. XRP 1.5449, BTC 75889.40).
     * - Never produces scientific notation (no 1.0E-5).
     * - If precision is not yet loaded, preserves raw exchange digits cleanly.
     */
    fun formatPrice(symbol: String, marketType: MarketType, rawValue: String?): String {
        if (rawValue.isNullOrBlank() || rawValue == "0" || rawValue == "0.0") {
            val prec = getPrecision(symbol, marketType)
            if (prec != null && rawValue != null && (rawValue == "0" || rawValue == "0.0")) {
                return BigDecimal.ZERO.setScale(prec, RoundingMode.UNNECESSARY).toPlainString()
            }
            if (rawValue.isNullOrBlank()) return "--"
        }

        val cleaned = rawValue.trim()
        val precision = getPrecision(symbol, marketType)

        return try {
            val bd = BigDecimal(cleaned)
            if (precision != null && precision >= 0) {
                bd.setScale(precision, RoundingMode.HALF_UP).toPlainString()
            } else {
                bd.toPlainString()
            }
        } catch (e: Exception) {
            cleaned
        }
    }

    fun formatPrice(symbol: String, marketType: MarketType, value: BigDecimal): String {
        val precision = getPrecision(symbol, marketType)
        return if (precision != null && precision >= 0) {
            value.setScale(precision, RoundingMode.HALF_UP).toPlainString()
        } else {
            value.toPlainString()
        }
    }

    fun formatPrice(symbol: String, marketType: MarketType, value: Double): String {
        if (value == 0.0) {
            val prec = getPrecision(symbol, marketType) ?: 2
            return BigDecimal.ZERO.setScale(prec, RoundingMode.UNNECESSARY).toPlainString()
        }
        val precision = getPrecision(symbol, marketType)
        val bd = BigDecimal.valueOf(value)
        return if (precision != null && precision >= 0) {
            bd.setScale(precision, RoundingMode.HALF_UP).toPlainString()
        } else {
            bd.toPlainString()
        }
    }

    /**
     * Rounds a calculated price (entry, SL, TP, high, low, invalidation)
     * to the symbol's exact tick size precision using RoundingMode.HALF_UP.
     */
    fun roundToPrecision(symbol: String, marketType: MarketType, value: Double): BigDecimal {
        val precision = getPrecision(symbol, marketType) ?: 2
        return BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_UP)
    }

    fun roundToPrecision(symbol: String, marketType: MarketType, value: BigDecimal): BigDecimal {
        val precision = getPrecision(symbol, marketType) ?: 2
        return value.setScale(precision, RoundingMode.HALF_UP)
    }

    /**
     * Format general numbers like volumes and fees cleanly without scientific notation.
     */
    fun formatClean(value: Double, maxDecimals: Int = 4): String {
        return try {
            val bd = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP)
            bd.stripTrailingZeros().toPlainString()
        } catch (e: Exception) {
            value.toString()
        }
    }
}
