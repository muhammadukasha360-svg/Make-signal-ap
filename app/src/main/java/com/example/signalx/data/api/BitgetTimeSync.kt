package com.example.signalx.data.api

import com.example.signalx.data.model.TimeFrame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object BitgetTimeSync {
    // Offset in milliseconds: serverTime - localTime
    @Volatile
    var serverOffsetMs: Long = 0L
        private set

    @Volatile
    var lastSyncTimestamp: Long = 0L
        private set

    private val pktTimeZone = TimeZone.getTimeZone("Asia/Karachi")

    private val timeFormatPkt = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
        timeZone = pktTimeZone
    }

    private val fullDateTimeFormatPkt = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.US).apply {
        timeZone = pktTimeZone
    }

    fun updateServerTime(serverTimeMs: Long, localRequestStartMs: Long, localRequestEndMs: Long) {
        val rtt = (localRequestEndMs - localRequestStartMs).coerceAtLeast(0)
        serverOffsetMs = serverTimeMs - (localRequestEndMs - rtt / 2)
        lastSyncTimestamp = System.currentTimeMillis()
    }

    /**
     * Set offset directly (useful for tests and manual syncs)
     */
    fun setOffset(offsetMs: Long) {
        serverOffsetMs = offsetMs
        lastSyncTimestamp = System.currentTimeMillis()
    }

    /**
     * Returns the synchronized current server time in epoch milliseconds.
     */
    fun getCorrectedServerTime(): Long {
        return System.currentTimeMillis() + serverOffsetMs
    }

    /**
     * Formats any epoch millisecond in Pakistan Standard Time (PKT, UTC+5).
     */
    fun formatPktTime(timestampMs: Long): String {
        return timeFormatPkt.format(Date(timestampMs))
    }

    fun formatPktFullDateTime(timestampMs: Long): String {
        return fullDateTimeFormatPkt.format(Date(timestampMs))
    }

    /**
     * Calculates the candle boundary (open and close timestamp) for any timeframe
     * aligned to UTC epoch boundaries, given a reference timestamp.
     */
    fun getCandleBoundaries(
        timeFrame: TimeFrame,
        referenceTimeMs: Long = getCorrectedServerTime()
    ): Pair<Long, Long> {
        val duration = timeFrame.durationMs
        val openMs = (referenceTimeMs / duration) * duration
        val closeMs = openMs + duration
        return Pair(openMs, closeMs)
    }

    /**
     * Calculates remaining milliseconds until the current candle closes.
     */
    fun getRemainingTimeMs(
        timeFrame: TimeFrame,
        referenceTimeMs: Long = getCorrectedServerTime()
    ): Long {
        val (_, closeMs) = getCandleBoundaries(timeFrame, referenceTimeMs)
        return (closeMs - referenceTimeMs).coerceAtLeast(0L)
    }

    /**
     * Formats remaining time into mm:ss or hh:mm:ss.
     */
    fun formatCountdown(remainingMs: Long): String {
        val totalSecs = (remainingMs / 1000).coerceAtLeast(0)
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Returns true when the candle is in preparation mode:
     * remaining time <= 30 seconds or <= 10% of timeframe duration (whichever is smaller).
     */
    fun isPreparationMode(timeFrame: TimeFrame, remainingMs: Long): Boolean {
        val threshold = 30_000L.coerceAtMost(timeFrame.durationMs)
        return remainingMs <= threshold
    }
}
