package com.example.signalx.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PredictionDao {
    @Query("SELECT * FROM predictions ORDER BY timestamp DESC")
    fun getAllPredictions(): Flow<List<PredictionEntity>>

    @Query("SELECT * FROM predictions WHERE isResolved = 0 ORDER BY timestamp ASC")
    suspend fun getUnresolvedPredictions(): List<PredictionEntity>

    @Query("SELECT * FROM predictions WHERE symbol = :symbol AND marketType = :marketType ORDER BY timestamp DESC LIMIT 100")
    fun getPredictionsForSymbol(symbol: String, marketType: String): Flow<List<PredictionEntity>>

    @Query("SELECT * FROM predictions WHERE isResolved = 1 ORDER BY timestamp DESC")
    fun getResolvedPredictions(): Flow<List<PredictionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrediction(prediction: PredictionEntity): Long

    @Update
    suspend fun updatePrediction(prediction: PredictionEntity)

    @Query("DELETE FROM predictions")
    suspend fun clearAll()
}

@Dao
interface PaperTradeDao {
    @Query("SELECT * FROM paper_trades ORDER BY timestamp DESC")
    fun getAllTrades(): Flow<List<PaperTradeEntity>>

    @Query("SELECT * FROM paper_trades WHERE status = 'OPEN' ORDER BY timestamp DESC")
    fun getOpenTrades(): Flow<List<PaperTradeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: PaperTradeEntity): Long

    @Update
    suspend fun updateTrade(trade: PaperTradeEntity)

    @Query("DELETE FROM paper_trades")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE symbol = :symbol AND marketType = :marketType")
    suspend fun removeFavorite(symbol: String, marketType: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE symbol = :symbol AND marketType = :marketType)")
    suspend fun isFavorite(symbol: String, marketType: String): Boolean
}

@Dao
interface DrawingDao {
    @Query("SELECT * FROM drawings WHERE symbol = :symbol AND marketType = :marketType")
    fun getDrawings(symbol: String, marketType: String): Flow<List<DrawingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDrawing(drawing: DrawingEntity)

    @Query("DELETE FROM drawings WHERE id = :id")
    suspend fun deleteDrawing(id: String)

    @Query("DELETE FROM drawings WHERE symbol = :symbol AND marketType = :marketType")
    suspend fun clearDrawingsForSymbol(symbol: String, marketType: String)
}
