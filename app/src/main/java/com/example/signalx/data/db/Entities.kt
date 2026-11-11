package com.example.signalx.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "predictions")
data class PredictionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val marketType: String,
    val timeframe: String,
    val horizonMinutes: Int,
    val timestamp: Long,
    val basePrice: Double,
    val predictedDirection: String,
    val probUp: Double,
    val probDown: Double,
    val probSideways: Double,
    val predictedHigh: Double,
    val predictedLow: Double,
    val confidence: Double,
    val signalType: String,
    val isResolved: Boolean = false,
    val actualClose: Double = 0.0,
    val actualHigh: Double = 0.0,
    val actualLow: Double = 0.0,
    val outcomeDirection: String = "",
    val isHitHighLow: Boolean = false,
    val isWin: Boolean = false,
    val resolvedTimestamp: Long = 0
)

@Entity(tableName = "paper_trades")
data class PaperTradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val marketType: String,
    val side: String, // "BUY" or "SELL"
    val entryPrice: Double,
    val quantity: Double,
    val leverage: Int = 1,
    val stopLoss: Double = 0.0,
    val takeProfit: Double = 0.0,
    val status: String = "OPEN", // "OPEN", "CLOSED"
    val exitPrice: Double = 0.0,
    val pnl: Double = 0.0,
    val fee: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val closeTimestamp: Long = 0
)

@Entity(tableName = "favorites", primaryKeys = ["symbol", "marketType"])
data class FavoriteEntity(
    val symbol: String,
    val marketType: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "drawings")
data class DrawingEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val marketType: String,
    val type: String, // "TRENDLINE", "HORIZONTAL", "FIBONACCI", "LONG_SHORT"
    val dataJson: String
)
