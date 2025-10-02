package com.example.straightup.models

import kotlin.math.abs

/**
 * Data class representing neck posture measurements
 */
data class PostureData(
    val tiltAngle: Float = 0f,          // Device tilt angle in degrees
    val headDistance: Float = 0f,        // Distance from camera to face in cm
    val headPosition: HeadPosition = HeadPosition(),
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 0f           // Confidence score of the measurement
)

/**
 * Head position relative to device
 */
data class HeadPosition(
    val x: Float = 0f,                   // Horizontal offset from center
    val y: Float = 0f,                   // Vertical offset from center
    val rotation: Float = 0f             // Head rotation angle
)

/**
 * Posture score calculated from multiple metrics
 */
data class PostureScore(
    val overall: Float = 0f,             // Overall score 0-100
    val tiltScore: Float = 0f,           // Tilt component score
    val distanceScore: Float = 0f,       // Distance component score
    val positionScore: Float = 0f,       // Position component score
    val level: PostureLevel = PostureLevel.GOOD,
    val recommendations: List<String> = emptyList()
)

/**
 * Posture quality levels
 */
enum class PostureLevel(val displayName: String, val color: Long) {
    EXCELLENT("Excellent", 0xFF4CAF50),
    GOOD("Good", 0xFF8BC34A),
    FAIR("Fair", 0xFFFFC107),
    POOR("Poor", 0xFFFF9800),
    CRITICAL("Critical", 0xFFF44336)
}

/**
 * User behavior patterns for adaptive reminders
 */
data class UserBehavior(
    val averagePostureScore: Float = 0f,
    val improvementTrend: Float = 0f,      // Positive = improving, negative = worsening
    val sessionDuration: Long = 0L,        // Current session duration in minutes
    val dailyUsage: Long = 0L,             // Daily usage in minutes
    val reminderResponseRate: Float = 0f   // How often user responds to reminders
)

/**
 * Posture calculation utilities
 */
object PostureCalculator {

    // Ideal posture thresholds
    private const val IDEAL_TILT_ANGLE = 0f        // Degrees
    private const val IDEAL_DISTANCE = 50f         // cm
    private const val MAX_TILT_DEVIATION = 30f     // Degrees
    private const val MAX_DISTANCE_DEVIATION = 20f // cm

    /**
     * Calculate comprehensive posture score
     */
    fun calculatePostureScore(data: PostureData): PostureScore {
        val tiltScore = calculateTiltScore(data.tiltAngle)
        val distanceScore = calculateDistanceScore(data.headDistance)
        val positionScore = calculatePositionScore(data.headPosition)

        // Weighted average
        val overall = (tiltScore * 0.4f + distanceScore * 0.4f + positionScore * 0.2f)

        val level = when {
            overall >= 90f -> PostureLevel.EXCELLENT
            overall >= 75f -> PostureLevel.GOOD
            overall >= 60f -> PostureLevel.FAIR
            overall >= 40f -> PostureLevel.POOR
            else -> PostureLevel.CRITICAL
        }

        val recommendations = generateRecommendations(data, tiltScore, distanceScore, positionScore)

        return PostureScore(
            overall = overall,
            tiltScore = tiltScore,
            distanceScore = distanceScore,
            positionScore = positionScore,
            level = level,
            recommendations = recommendations
        )
    }

    private fun calculateTiltScore(tiltAngle: Float): Float {
        val deviation = abs(tiltAngle - IDEAL_TILT_ANGLE)
        return maxOf(0f, 100f - (deviation / MAX_TILT_DEVIATION) * 100f)
    }

    private fun calculateDistanceScore(distance: Float): Float {
        if (distance <= 0f) return 0f
        val deviation = abs(distance - IDEAL_DISTANCE)
        return maxOf(0f, 100f - (deviation / MAX_DISTANCE_DEVIATION) * 100f)
    }

    private fun calculatePositionScore(position: HeadPosition): Float {
        val horizontalDeviation = abs(position.x) / 100f  // Normalize to percentage
        val verticalDeviation = abs(position.y) / 100f
        val rotationDeviation = abs(position.rotation) / 45f  // Max 45 degrees

        val averageDeviation = (horizontalDeviation + verticalDeviation + rotationDeviation) / 3f
        return maxOf(0f, 100f - averageDeviation * 100f)
    }

    private fun generateRecommendations(
        data: PostureData,
        tiltScore: Float,
        distanceScore: Float,
        positionScore: Float
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (tiltScore < 60f) {
            if (data.tiltAngle > IDEAL_TILT_ANGLE + 15f) {
                recommendations.add("Lift your device higher to reduce neck strain")
            } else if (data.tiltAngle < IDEAL_TILT_ANGLE - 15f) {
                recommendations.add("Lower your device slightly for better posture")
            }
        }

        if (distanceScore < 60f) {
            if (data.headDistance < IDEAL_DISTANCE - 10f) {
                recommendations.add("Move farther from your device to reduce eye strain")
            } else if (data.headDistance > IDEAL_DISTANCE + 10f) {
                recommendations.add("Move closer to your device for optimal viewing")
            }
        }

        if (positionScore < 60f) {
            recommendations.add("Center your head with the device and keep it straight")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Great posture! Keep it up!")
        }

        return recommendations
    }
}
