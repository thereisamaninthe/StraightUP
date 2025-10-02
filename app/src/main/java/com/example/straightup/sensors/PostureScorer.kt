package com.example.straightup.sensors

import com.example.straightup.models.PostureData
import com.example.straightup.models.PostureScore
import com.example.straightup.models.PostureCalculator
import com.example.straightup.models.UserBehavior
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Advanced posture scoring algorithm that combines sensor data
 */
class PostureScorer(private val scope: CoroutineScope) {

    private val _currentScore = MutableStateFlow(PostureScore())
    val currentScore: StateFlow<PostureScore> = _currentScore.asStateFlow()

    private val _sessionScores = MutableStateFlow<List<PostureScore>>(emptyList())
    val sessionScores: StateFlow<List<PostureScore>> = _sessionScores.asStateFlow()

    private val _userBehavior = MutableStateFlow(UserBehavior())
    val userBehavior: StateFlow<UserBehavior> = _userBehavior.asStateFlow()

    // Scoring parameters
    private val scoreHistorySize = 50
    private val stabilityThreshold = 5f  // Degrees for stable measurement
    private val confidenceThreshold = 0.6f

    // Adaptive thresholds based on user behavior
    private var adaptiveThresholds = AdaptiveThresholds()

    /**
     * Process combined sensor data and calculate posture score
     */
    fun processPostureData(
        tiltSensorManager: TiltSensorManager,
        visionAIDetector: VisionAIDetector
    ) {
        scope.launch {
            combine(
                tiltSensorManager.tiltAngle,
                visionAIDetector.headDistance,
                visionAIDetector.headPosition,
                visionAIDetector.confidence,
                visionAIDetector.faceDetected
            ) { tiltAngle, headDistance, headPosition, confidence, faceDetected ->

                // Only calculate score if we have reliable data
                if (faceDetected && confidence > confidenceThreshold && tiltSensorManager.isDeviceStable()) {
                    val postureData = PostureData(
                        tiltAngle = tiltAngle,
                        headDistance = headDistance,
                        headPosition = headPosition,
                        confidence = confidence
                    )

                    calculateAdvancedScore(postureData)
                } else {
                    // Return current score if data is unreliable
                    _currentScore.value
                }

            }.collect { score ->
                updateScore(score)
            }
        }
    }

    /**
     * Calculate advanced posture score with adaptive thresholds
     */
    private fun calculateAdvancedScore(data: PostureData): PostureScore {
        // Base score using standard algorithm
        val baseScore = PostureCalculator.calculatePostureScore(data)

        // Apply adaptive adjustments based on user behavior
        val adaptedScore = applyAdaptiveAdjustments(baseScore, data)

        // Apply temporal smoothing
        val smoothedScore = applyTemporalSmoothing(adaptedScore)

        return smoothedScore
    }

    /**
     * Apply adaptive adjustments based on user's historical behavior
     */
    private fun applyAdaptiveAdjustments(score: PostureScore, data: PostureData): PostureScore {
        val behavior = _userBehavior.value

        // Adjust thresholds based on user's average performance
        val personalizedThresholds = when {
            behavior.averagePostureScore > 80f -> {
                // Stricter thresholds for users with good posture
                adaptiveThresholds.copy(
                    tiltTolerance = adaptiveThresholds.tiltTolerance * 0.8f,
                    distanceTolerance = adaptiveThresholds.distanceTolerance * 0.8f
                )
            }
            behavior.averagePostureScore < 50f -> {
                // More lenient thresholds for users with poor posture
                adaptiveThresholds.copy(
                    tiltTolerance = adaptiveThresholds.tiltTolerance * 1.2f,
                    distanceTolerance = adaptiveThresholds.distanceTolerance * 1.2f
                )
            }
            else -> adaptiveThresholds
        }

        // Recalculate scores with personalized thresholds
        val adjustedTiltScore = calculateAdaptiveTiltScore(data.tiltAngle, personalizedThresholds)
        val adjustedDistanceScore = calculateAdaptiveDistanceScore(data.headDistance, personalizedThresholds)

        // Weight adjustment based on improvement trend
        val trendMultiplier = when {
            behavior.improvementTrend > 0.1f -> 1.1f  // Bonus for improving users
            behavior.improvementTrend < -0.1f -> 0.9f // Slight penalty for declining users
            else -> 1.0f
        }

        val adjustedOverall = (adjustedTiltScore * 0.4f + adjustedDistanceScore * 0.4f + score.positionScore * 0.2f) * trendMultiplier

        return score.copy(
            overall = adjustedOverall.coerceIn(0f, 100f),
            tiltScore = adjustedTiltScore,
            distanceScore = adjustedDistanceScore
        )
    }

    /**
     * Apply temporal smoothing to reduce score fluctuations
     */
    private fun applyTemporalSmoothing(score: PostureScore): PostureScore {
        val recentScores = _sessionScores.value.takeLast(5)

        if (recentScores.isEmpty()) return score

        // Calculate weighted average with recent scores
        val weights = listOf(0.4f, 0.25f, 0.2f, 0.1f, 0.05f)
        var weightedSum = score.overall * weights[0]
        var totalWeight = weights[0]

        recentScores.reversed().forEachIndexed { index, recentScore ->
            if (index < weights.size - 1) {
                weightedSum += recentScore.overall * weights[index + 1]
                totalWeight += weights[index + 1]
            }
        }

        val smoothedOverall = weightedSum / totalWeight

        return score.copy(overall = smoothedOverall)
    }

    /**
     * Update score and session history
     */
    private fun updateScore(score: PostureScore) {
        _currentScore.value = score

        // Update session scores
        val currentScores = _sessionScores.value.toMutableList()
        currentScores.add(score)
        if (currentScores.size > scoreHistorySize) {
            currentScores.removeAt(0)
        }
        _sessionScores.value = currentScores

        // Update user behavior
        updateUserBehavior(score)
    }

    /**
     * Update user behavior patterns
     */
    private fun updateUserBehavior(newScore: PostureScore) {
        val currentBehavior = _userBehavior.value
        val scores = _sessionScores.value

        if (scores.size < 2) return

        // Calculate average score
        val averageScore = scores.map { it.overall }.average().toFloat()

        // Calculate improvement trend (slope of recent scores)
        val recentScores = scores.takeLast(10)
        val improvementTrend = if (recentScores.size >= 5) {
            calculateTrend(recentScores.map { it.overall })
        } else {
            currentBehavior.improvementTrend
        }

        // Update session duration (in minutes)
        val sessionDuration = currentBehavior.sessionDuration + 1 // Simplified increment

        _userBehavior.value = currentBehavior.copy(
            averagePostureScore = averageScore,
            improvementTrend = improvementTrend,
            sessionDuration = sessionDuration
        )

        // Update adaptive thresholds based on new behavior
        updateAdaptiveThresholds()
    }

    /**
     * Calculate trend from a series of scores
     */
    private fun calculateTrend(scores: List<Float>): Float {
        if (scores.size < 2) return 0f

        val n = scores.size
        val x = (0 until n).map { it.toFloat() }
        val y = scores

        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { (xi, yi) -> xi * yi }
        val sumX2 = x.sumOf { it * it }

        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        return slope
    }

    /**
     * Update adaptive thresholds based on user behavior
     */
    private fun updateAdaptiveThresholds() {
        val behavior = _userBehavior.value

        adaptiveThresholds = when {
            behavior.averagePostureScore > 85f -> {
                AdaptiveThresholds(tiltTolerance = 20f, distanceTolerance = 15f)
            }
            behavior.averagePostureScore > 70f -> {
                AdaptiveThresholds(tiltTolerance = 25f, distanceTolerance = 18f)
            }
            else -> {
                AdaptiveThresholds(tiltTolerance = 30f, distanceTolerance = 20f)
            }
        }
    }

    private fun calculateAdaptiveTiltScore(tiltAngle: Float, thresholds: AdaptiveThresholds): Float {
        val deviation = abs(tiltAngle)
        return max(0f, 100f - (deviation / thresholds.tiltTolerance) * 100f)
    }

    private fun calculateAdaptiveDistanceScore(distance: Float, thresholds: AdaptiveThresholds): Float {
        if (distance <= 0f) return 0f
        val idealDistance = 50f
        val deviation = abs(distance - idealDistance)
        return max(0f, 100f - (deviation / thresholds.distanceTolerance) * 100f)
    }

    /**
     * Reset session data
     */
    fun resetSession() {
        _sessionScores.value = emptyList()
        _userBehavior.value = UserBehavior()
    }

    /**
     * Get current session statistics
     */
    fun getSessionStats(): SessionStats {
        val scores = _sessionScores.value
        return if (scores.isNotEmpty()) {
            SessionStats(
                averageScore = scores.map { it.overall }.average().toFloat(),
                bestScore = scores.maxOf { it.overall },
                worstScore = scores.minOf { it.overall },
                totalMeasurements = scores.size,
                improvementTrend = _userBehavior.value.improvementTrend
            )
        } else {
            SessionStats()
        }
    }
}

/**
 * Adaptive thresholds for personalized scoring
 */
data class AdaptiveThresholds(
    val tiltTolerance: Float = 30f,      // Degrees
    val distanceTolerance: Float = 20f   // Centimeters
)

/**
 * Session statistics
 */
data class SessionStats(
    val averageScore: Float = 0f,
    val bestScore: Float = 0f,
    val worstScore: Float = 0f,
    val totalMeasurements: Int = 0,
    val improvementTrend: Float = 0f
)
