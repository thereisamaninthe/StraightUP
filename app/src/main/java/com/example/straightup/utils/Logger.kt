package com.example.straightup.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Logging and debugging utilities for StraightUP app
 */
object Logger {

    private const val TAG = "StraightUP"
    private const val MAX_LOG_ENTRIES = 1000

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    enum class LogLevel(val displayName: String, val priority: Int) {
        VERBOSE("VERBOSE", Log.VERBOSE),
        DEBUG("DEBUG", Log.DEBUG),
        INFO("INFO", Log.INFO),
        WARN("WARN", Log.WARN),
        ERROR("ERROR", Log.ERROR)
    }

    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    ) {
        val formattedTime: String
            get() = dateFormat.format(Date(timestamp))
    }

    /**
     * Log verbose message
     */
    fun v(tag: String = TAG, message: String, throwable: Throwable? = null) {
        log(LogLevel.VERBOSE, tag, message, throwable)
    }

    /**
     * Log debug message
     */
    fun d(tag: String = TAG, message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, tag, message, throwable)
    }

    /**
     * Log info message
     */
    fun i(tag: String = TAG, message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, tag, message, throwable)
    }

    /**
     * Log warning message
     */
    fun w(tag: String = TAG, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    /**
     * Log error message
     */
    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }

    /**
     * Log posture data for debugging
     */
    fun logPostureData(
        tiltAngle: Float,
        headDistance: Float,
        headPosition: com.example.straightup.models.HeadPosition,
        confidence: Float
    ) {
        val message = "Posture Data - Tilt: ${String.format("%.1f", tiltAngle)}°, " +
                "Distance: ${String.format("%.1f", headDistance)}cm, " +
                "Position: (${String.format("%.1f", headPosition.x)}, ${String.format("%.1f", headPosition.y)}), " +
                "Confidence: ${String.format("%.2f", confidence)}"
        d("PostureData", message)
    }

    /**
     * Log posture score for debugging
     */
    fun logPostureScore(score: com.example.straightup.models.PostureScore) {
        val message = "Posture Score - Overall: ${String.format("%.1f", score.overall)}, " +
                "Level: ${score.level.displayName}, " +
                "Tilt: ${String.format("%.1f", score.tiltScore)}, " +
                "Distance: ${String.format("%.1f", score.distanceScore)}, " +
                "Position: ${String.format("%.1f", score.positionScore)}"
        d("PostureScore", message)
    }

    /**
     * Log reminder event
     */
    fun logReminderEvent(event: com.example.straightup.reminders.ReminderEvent) {
        val message = "Reminder - Level: ${event.level.displayName}, " +
                "Type: ${event.type}, " +
                "Score: ${String.format("%.1f", event.postureScore.overall)}"
        i("Reminder", message)
    }

    /**
     * Log sensor status
     */
    fun logSensorStatus(sensorName: String, isActive: Boolean, data: String = "") {
        val status = if (isActive) "ACTIVE" else "INACTIVE"
        val message = "Sensor [$sensorName] - $status $data"
        d("Sensors", message)
    }

    /**
     * Log performance metrics
     */
    fun logPerformance(operation: String, durationMs: Long) {
        val message = "Performance - $operation took ${durationMs}ms"
        if (durationMs > 100) {
            w("Performance", message)
        } else {
            d("Performance", message)
        }
    }

    /**
     * Internal logging method
     */
    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        // Log to Android's logcat
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, message, throwable)
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }

        // Add to internal log list for in-app debugging
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )

        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(entry)

        // Keep only the most recent entries
        if (currentLogs.size > MAX_LOG_ENTRIES) {
            currentLogs.removeAt(0)
        }

        _logs.value = currentLogs
    }

    /**
     * Clear all log entries
     */
    fun clearLogs() {
        _logs.value = emptyList()
    }

    /**
     * Get logs filtered by level
     */
    fun getLogsByLevel(level: LogLevel): List<LogEntry> {
        return _logs.value.filter { it.level == level }
    }

    /**
     * Get logs filtered by tag
     */
    fun getLogsByTag(tag: String): List<LogEntry> {
        return _logs.value.filter { it.tag == tag }
    }

    /**
     * Export logs as string for sharing/debugging
     */
    fun exportLogsAsString(): String {
        return _logs.value.joinToString("\n") { entry ->
            "${entry.formattedTime} ${entry.level.displayName}/${entry.tag}: ${entry.message}" +
                    if (entry.throwable != null) "\n${Log.getStackTraceString(entry.throwable)}" else ""
        }
    }
}
