package com.example.straightup.reminders

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.straightup.models.PostureScore
import com.example.straightup.models.UserBehavior
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import kotlin.math.*

/**
 * Manages adaptive reminder intervals and delivery
 */
class ReminderManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val _reminderConfig = MutableStateFlow(ReminderConfig())
    val reminderConfig: StateFlow<ReminderConfig> = _reminderConfig.asStateFlow()

    private val _lastReminderTime = MutableStateFlow(0L)
    val lastReminderTime: StateFlow<Long> = _lastReminderTime.asStateFlow()

    private val _consecutiveIgnored = MutableStateFlow(0)
    private val _totalReminders = MutableStateFlow(0)
    private val _acknowledgedReminders = MutableStateFlow(0)

    private val _activeReminder = MutableStateFlow<ReminderEvent?>(null)
    val activeReminder: StateFlow<ReminderEvent?> = _activeReminder.asStateFlow()

    private val _reminderHistory = MutableStateFlow<List<ReminderEvent>>(emptyList())
    val reminderHistory: StateFlow<List<ReminderEvent>> = _reminderHistory.asStateFlow()

    private var reminderJob: Job? = null
    private var monitoringJob: Job? = null

    /**
     * Start adaptive reminder monitoring
     */
    fun startReminderMonitoring(
        postureScoreFlow: StateFlow<PostureScore>,
        userBehaviorFlow: StateFlow<UserBehavior>
    ) {
        stopReminderMonitoring()

        monitoringJob = scope.launch {
            combine(
                postureScoreFlow,
                userBehaviorFlow,
                _reminderConfig
            ) { postureScore, userBehavior, config ->
                Triple(postureScore, userBehavior, config)
            }.collect { (postureScore, userBehavior, config) ->
                processPostureUpdate(postureScore, userBehavior, config)
            }
        }
    }

    /**
     * Stop reminder monitoring
     */
    fun stopReminderMonitoring() {
        monitoringJob?.cancel()
        reminderJob?.cancel()
        _activeReminder.value = null
    }

    /**
     * Process posture updates and determine if reminder is needed
     */
    private suspend fun processPostureUpdate(
        postureScore: PostureScore,
        userBehavior: UserBehavior,
        config: ReminderConfig
    ) {
        if (!config.isEnabled) return

        val currentTime = System.currentTimeMillis()
        val timeSinceLastReminder = currentTime - _lastReminderTime.value

        // Calculate adaptive interval based on posture and behavior
        val adaptiveInterval = calculateAdaptiveInterval(
            postureScore = postureScore,
            userBehavior = userBehavior,
            config = config,
            timeSinceLastReminder = timeSinceLastReminder
        )

        // Check if reminder should be triggered
        if (shouldTriggerReminder(postureScore, timeSinceLastReminder, adaptiveInterval)) {
            triggerReminder(postureScore, userBehavior, config)
        }
    }

    /**
     * Calculate adaptive reminder interval based on multiple factors
     */
    private fun calculateAdaptiveInterval(
        postureScore: PostureScore,
        userBehavior: UserBehavior,
        config: ReminderConfig,
        timeSinceLastReminder: Long
    ): Long {

        // Base interval from posture score
        val baseInterval = when (postureScore.level) {
            com.example.straightup.models.PostureLevel.EXCELLENT -> config.maximumInterval
            com.example.straightup.models.PostureLevel.GOOD -> config.maximumInterval * 0.8f
            com.example.straightup.models.PostureLevel.FAIR -> config.maximumInterval * 0.6f
            com.example.straightup.models.PostureLevel.POOR -> config.maximumInterval * 0.4f
            com.example.straightup.models.PostureLevel.CRITICAL -> config.minimumInterval
        }.toLong()

        // Adjust based on user behavior
        val behaviorMultiplier = when {
            userBehavior.improvementTrend > 0.2f -> 1.3f  // Less frequent for improving users
            userBehavior.improvementTrend < -0.2f -> 0.7f // More frequent for declining users
            userBehavior.reminderResponseRate > 0.8f -> 1.2f // Less frequent for responsive users
            userBehavior.reminderResponseRate < 0.3f -> 0.8f // More frequent for unresponsive users
            else -> 1.0f
        }

        // Adjust for session duration (longer sessions need more frequent reminders)
        val sessionMultiplier = when {
            userBehavior.sessionDuration > 120 -> 0.8f // More frequent after 2 hours
            userBehavior.sessionDuration > 60 -> 0.9f  // Slightly more frequent after 1 hour
            else -> 1.0f
        }

        // Adjust for time of day (less frequent during likely break times)
        val timeMultiplier = getTimeOfDayMultiplier()

        val adaptiveInterval = (baseInterval * behaviorMultiplier * sessionMultiplier * timeMultiplier).toLong()

        return adaptiveInterval.coerceIn(config.minimumInterval, config.maximumInterval)
    }

    /**
     * Get time-of-day multiplier for reminder frequency
     */
    private fun getTimeOfDayMultiplier(): Float {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 0..6 -> 2.0f    // Night hours - very infrequent
            in 7..8 -> 1.5f    // Morning preparation - less frequent
            in 9..11 -> 1.0f   // Morning work - normal
            in 12..13 -> 1.5f  // Lunch break - less frequent
            in 14..17 -> 1.0f  // Afternoon work - normal
            in 18..19 -> 1.3f  // Evening transition - slightly less frequent
            in 20..23 -> 1.8f  // Evening leisure - less frequent
            else -> 1.0f
        }
    }

    /**
     * Determine if a reminder should be triggered
     */
    private fun shouldTriggerReminder(
        postureScore: PostureScore,
        timeSinceLastReminder: Long,
        adaptiveInterval: Long
    ): Boolean {
        // Don't trigger if there's already an active reminder
        if (_activeReminder.value != null) return false

        // Always trigger for critical posture regardless of interval
        if (postureScore.level == com.example.straightup.models.PostureLevel.CRITICAL &&
            timeSinceLastReminder > _reminderConfig.value.minimumInterval) {
            return true
        }

        // Normal interval-based triggering
        return timeSinceLastReminder >= adaptiveInterval
    }

    /**
     * Trigger a reminder with appropriate level and type
     */
    private suspend fun triggerReminder(
        postureScore: PostureScore,
        userBehavior: UserBehavior,
        config: ReminderConfig
    ) {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastReminder = currentTime - _lastReminderTime.value

        // Determine reminder level and type
        val reminderLevel = ReminderLevels.determineReminderLevel(
            postureScore = postureScore,
            userBehavior = userBehavior,
            consecutiveIgnored = _consecutiveIgnored.value,
            timeSinceLastReminder = timeSinceLastReminder
        )

        val reminderType = ReminderLevels.determineReminderType(
            level = reminderLevel,
            config = config,
            isAppInForeground = true, // This would be determined by app lifecycle
            currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        )

        val message = ReminderLevels.generateReminderMessage(
            postureScore = postureScore,
            level = reminderLevel,
            userBehavior = userBehavior
        )

        val reminderEvent = ReminderEvent(
            level = reminderLevel,
            type = reminderType,
            message = message,
            postureScore = postureScore,
            timestamp = currentTime
        )

        // Execute the reminder
        executeReminder(reminderEvent)

        // Update state
        _activeReminder.value = reminderEvent
        _lastReminderTime.value = currentTime
        _totalReminders.value = _totalReminders.value + 1

        // Add to history
        val history = _reminderHistory.value.toMutableList()
        history.add(reminderEvent)
        if (history.size > 100) { // Keep last 100 reminders
            history.removeAt(0)
        }
        _reminderHistory.value = history

        // Auto-dismiss after timeout
        scheduleAutoDismiss(reminderEvent)
    }

    /**
     * Execute the reminder based on its type
     */
    private suspend fun executeReminder(reminder: ReminderEvent) {
        when (reminder.type) {
            ReminderType.VIBRATION -> {
                executeVibration(reminder.level)
            }
            ReminderType.NOTIFICATION -> {
                // This would show a system notification
                executeVibration(reminder.level) // Also vibrate for notifications
            }
            ReminderType.POPUP -> {
                // This would show an in-app popup
                executeVibration(reminder.level)
            }
            ReminderType.OVERLAY -> {
                // This would show a system overlay
                executeVibration(reminder.level)
            }
            ReminderType.COMBINED -> {
                executeVibration(reminder.level)
                // Execute multiple reminder types
            }
        }
    }

    /**
     * Execute vibration based on reminder level
     */
    private fun executeVibration(level: ReminderLevel) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val vibrationEffect = VibrationEffect.createWaveform(level.vibrationPattern, -1)
            vibrator.vibrate(vibrationEffect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(level.vibrationPattern, -1)
        }
    }

    /**
     * Schedule auto-dismiss for reminder
     */
    private fun scheduleAutoDismiss(reminder: ReminderEvent) {
        reminderJob?.cancel()
        reminderJob = scope.launch {
            delay(30000) // 30 seconds timeout

            if (_activeReminder.value?.timestamp == reminder.timestamp) {
                handleReminderResponse(ReminderResponse(
                    wasAcknowledged = false,
                    wasIgnored = true,
                    responseTime = 30000,
                    userAction = null
                ))
            }
        }
    }

    /**
     * Handle user response to reminder
     */
    fun handleReminderResponse(response: ReminderResponse) {
        val activeReminder = _activeReminder.value ?: return

        _activeReminder.value = null
        reminderJob?.cancel()

        if (response.wasAcknowledged) {
            _acknowledgedReminders.value = _acknowledgedReminders.value + 1
            _consecutiveIgnored.value = 0
        } else if (response.wasIgnored) {
            _consecutiveIgnored.value = _consecutiveIgnored.value + 1
        }

        // Update user behavior based on response
        updateUserBehaviorFromResponse(response)
    }

    /**
     * Update user behavior patterns based on reminder responses
     */
    private fun updateUserBehaviorFromResponse(response: ReminderResponse) {
        val total = _totalReminders.value
        val acknowledged = _acknowledgedReminders.value

        val responseRate = if (total > 0) acknowledged.toFloat() / total.toFloat() else 0f

        // This would ideally update the user behavior in a shared state or repository
        // For now, we just track the response rate internally
    }

    /**
     * Update reminder configuration
     */
    fun updateConfig(newConfig: ReminderConfig) {
        _reminderConfig.value = newConfig
    }

    /**
     * Get reminder statistics
     */
    fun getReminderStats(): ReminderStats {
        val total = _totalReminders.value
        val acknowledged = _acknowledgedReminders.value
        val responseRate = if (total > 0) acknowledged.toFloat() / total.toFloat() else 0f

        return ReminderStats(
            totalReminders = total,
            acknowledgedReminders = acknowledged,
            responseRate = responseRate,
            consecutiveIgnored = _consecutiveIgnored.value,
            averageInterval = calculateAverageInterval()
        )
    }

    private fun calculateAverageInterval(): Long {
        val history = _reminderHistory.value
        if (history.size < 2) return 0L

        val intervals = mutableListOf<Long>()
        for (i in 1 until history.size) {
            intervals.add(history[i].timestamp - history[i-1].timestamp)
        }

        return if (intervals.isNotEmpty()) intervals.average().toLong() else 0L
    }
}

/**
 * Reminder statistics
 */
data class ReminderStats(
    val totalReminders: Int = 0,
    val acknowledgedReminders: Int = 0,
    val responseRate: Float = 0f,
    val consecutiveIgnored: Int = 0,
    val averageInterval: Long = 0L
)
