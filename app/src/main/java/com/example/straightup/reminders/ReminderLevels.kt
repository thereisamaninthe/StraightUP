package com.example.straightup.reminders

import com.example.straightup.models.PostureLevel
import com.example.straightup.models.PostureScore
import com.example.straightup.models.UserBehavior

/**
 * Defines reminder intensity levels with escalating interventions
 */
enum class ReminderLevel(
    val displayName: String,
    val vibrationPattern: LongArray,
    val priority: Int,
    val canBlockUI: Boolean
) {
    GENTLE(
        displayName = "Gentle",
        vibrationPattern = longArrayOf(0, 100),
        priority = 1,
        canBlockUI = false
    ),
    MODERATE(
        displayName = "Moderate",
        vibrationPattern = longArrayOf(0, 200, 100, 200),
        priority = 2,
        canBlockUI = false
    ),
    STRONG(
        displayName = "Strong",
        vibrationPattern = longArrayOf(0, 300, 150, 300, 150, 300),
        priority = 3,
        canBlockUI = true
    ),
    URGENT(
        displayName = "Urgent",
        vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 500),
        priority = 4,
        canBlockUI = true
    )
}

/**
 * Reminder types with different presentation methods
 */
enum class ReminderType {
    NOTIFICATION,    // Simple notification
    POPUP,          // In-app popup
    OVERLAY,        // System overlay (requires permission)
    VIBRATION,      // Vibration only
    COMBINED        // Multiple methods
}

/**
 * Configuration for reminder behavior
 */
data class ReminderConfig(
    val isEnabled: Boolean = true,
    val minimumInterval: Long = 30_000L,      // 30 seconds minimum
    val maximumInterval: Long = 600_000L,     // 10 minutes maximum
    val escalationThreshold: Int = 3,         // Escalate after 3 ignored reminders
    val adaptToUserBehavior: Boolean = true,
    val quietHours: Pair<Int, Int>? = null,   // Hour range for quiet mode (e.g., 22 to 6)
    val workModeEnabled: Boolean = false,     // Less intrusive during work hours
    val allowedReminderTypes: Set<ReminderType> = setOf(
        ReminderType.NOTIFICATION,
        ReminderType.POPUP,
        ReminderType.VIBRATION
    )
)

/**
 * Reminder event data
 */
data class ReminderEvent(
    val level: ReminderLevel,
    val type: ReminderType,
    val message: String,
    val postureScore: PostureScore,
    val timestamp: Long = System.currentTimeMillis(),
    val isUserTriggered: Boolean = false
)

/**
 * Response to a reminder
 */
data class ReminderResponse(
    val wasAcknowledged: Boolean,
    val wasIgnored: Boolean,
    val responseTime: Long, // Time taken to respond in milliseconds
    val userAction: UserAction? = null
)

enum class UserAction {
    CORRECTED_POSTURE,
    DISMISSED,
    POSTPONED,
    DISABLED_TEMPORARILY
}

/**
 * Hierarchical reminder logic that escalates based on posture and user response
 */
class ReminderLevels {

    companion object {
        /**
         * Determine appropriate reminder level based on posture score and context
         */
        fun determineReminderLevel(
            postureScore: PostureScore,
            userBehavior: UserBehavior,
            consecutiveIgnored: Int,
            timeSinceLastReminder: Long
        ): ReminderLevel {

            // Base level from posture score
            val baseLevel = when (postureScore.level) {
                PostureLevel.EXCELLENT, PostureLevel.GOOD -> return ReminderLevel.GENTLE
                PostureLevel.FAIR -> ReminderLevel.GENTLE
                PostureLevel.POOR -> ReminderLevel.MODERATE
                PostureLevel.CRITICAL -> ReminderLevel.STRONG
            }

            // Escalate based on ignored reminders
            val escalatedLevel = when (consecutiveIgnored) {
                0, 1 -> baseLevel
                2, 3 -> escalateLevel(baseLevel, 1)
                4, 5 -> escalateLevel(baseLevel, 2)
                else -> ReminderLevel.URGENT
            }

            // Consider user behavior patterns
            val behaviorAdjustedLevel = adjustForUserBehavior(escalatedLevel, userBehavior)

            // Consider time since last reminder
            val timeAdjustedLevel = adjustForTime(behaviorAdjustedLevel, timeSinceLastReminder)

            return timeAdjustedLevel
        }

        /**
         * Escalate reminder level by specified steps
         */
        private fun escalateLevel(current: ReminderLevel, steps: Int): ReminderLevel {
            val levels = ReminderLevel.values()
            val currentIndex = levels.indexOf(current)
            val newIndex = (currentIndex + steps).coerceAtMost(levels.size - 1)
            return levels[newIndex]
        }

        /**
         * Adjust reminder level based on user behavior patterns
         */
        private fun adjustForUserBehavior(
            level: ReminderLevel,
            userBehavior: UserBehavior
        ): ReminderLevel {
            return when {
                // User is improving - be more gentle
                userBehavior.improvementTrend > 0.2f && userBehavior.reminderResponseRate > 0.7f -> {
                    deescalateLevel(level, 1)
                }

                // User frequently ignores reminders - be more assertive
                userBehavior.reminderResponseRate < 0.3f -> {
                    escalateLevel(level, 1)
                }

                // Long session with poor posture - escalate gradually
                userBehavior.sessionDuration > 60 && userBehavior.averagePostureScore < 50f -> {
                    escalateLevel(level, 1)
                }

                else -> level
            }
        }

        /**
         * Adjust reminder level based on time since last reminder
         */
        private fun adjustForTime(level: ReminderLevel, timeSinceLastReminder: Long): ReminderLevel {
            val minutes = timeSinceLastReminder / 60_000L

            return when {
                // If it's been a very long time, start gentle regardless
                minutes > 30 -> ReminderLevel.GENTLE

                // If recent reminder was ignored, escalate
                minutes < 2 -> escalateLevel(level, 1)

                else -> level
            }
        }

        /**
         * De-escalate reminder level by specified steps
         */
        private fun deescalateLevel(current: ReminderLevel, steps: Int): ReminderLevel {
            val levels = ReminderLevel.values()
            val currentIndex = levels.indexOf(current)
            val newIndex = (currentIndex - steps).coerceAtLeast(0)
            return levels[newIndex]
        }

        /**
         * Determine reminder type based on level and context
         */
        fun determineReminderType(
            level: ReminderLevel,
            config: ReminderConfig,
            isAppInForeground: Boolean,
            currentHour: Int
        ): ReminderType {

            // Check quiet hours
            config.quietHours?.let { (start, end) ->
                if (isInQuietHours(currentHour, start, end)) {
                    return if (level.priority >= 3) ReminderType.VIBRATION else ReminderType.NOTIFICATION
                }
            }

            // Check work mode
            if (config.workModeEnabled && isWorkHours(currentHour)) {
                return when (level) {
                    ReminderLevel.GENTLE, ReminderLevel.MODERATE -> ReminderType.NOTIFICATION
                    ReminderLevel.STRONG -> ReminderType.POPUP
                    ReminderLevel.URGENT -> ReminderType.COMBINED
                }
            }

            // Normal mode selection based on level and app state
            return when (level) {
                ReminderLevel.GENTLE -> {
                    if (isAppInForeground) ReminderType.NOTIFICATION else ReminderType.NOTIFICATION
                }
                ReminderLevel.MODERATE -> {
                    if (isAppInForeground) ReminderType.POPUP else ReminderType.NOTIFICATION
                }
                ReminderLevel.STRONG -> {
                    if (level.canBlockUI && ReminderType.OVERLAY in config.allowedReminderTypes) {
                        ReminderType.OVERLAY
                    } else {
                        ReminderType.COMBINED
                    }
                }
                ReminderLevel.URGENT -> ReminderType.COMBINED
            }
        }

        /**
         * Generate appropriate reminder message
         */
        fun generateReminderMessage(
            postureScore: PostureScore,
            level: ReminderLevel,
            userBehavior: UserBehavior
        ): String {
            val baseMessage = when (postureScore.level) {
                PostureLevel.CRITICAL -> "Your posture needs immediate attention!"
                PostureLevel.POOR -> "Please check your posture"
                PostureLevel.FAIR -> "Time for a posture check"
                PostureLevel.GOOD, PostureLevel.EXCELLENT -> "Gentle posture reminder"
            }

            val personalizedTip = if (postureScore.recommendations.isNotEmpty()) {
                "\n\n💡 ${postureScore.recommendations.first()}"
            } else ""

            val encouragement = when {
                userBehavior.improvementTrend > 0.1f -> "\n\n🎉 You're improving! Keep it up!"
                userBehavior.averagePostureScore > 80f -> "\n\n⭐ Great job maintaining good posture!"
                else -> ""
            }

            return baseMessage + personalizedTip + encouragement
        }

        private fun isInQuietHours(currentHour: Int, startHour: Int, endHour: Int): Boolean {
            return if (startHour <= endHour) {
                currentHour in startHour..endHour
            } else {
                currentHour >= startHour || currentHour <= endHour
            }
        }

        private fun isWorkHours(currentHour: Int): Boolean {
            return currentHour in 9..17 // 9 AM to 5 PM
        }
    }
}
