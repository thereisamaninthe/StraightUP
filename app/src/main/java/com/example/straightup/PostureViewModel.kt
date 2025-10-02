package com.example.straightup

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.straightup.models.*
import com.example.straightup.sensors.*
import com.example.straightup.reminders.*
import com.example.straightup.utils.Logger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Main ViewModel that coordinates posture monitoring, scoring, and reminders
 */
class PostureViewModel : ViewModel() {

    // Core components
    private var tiltSensorManager: TiltSensorManager? = null
    private var visionAIDetector: VisionAIDetector? = null
    private var postureScorer: PostureScorer? = null
    private var reminderManager: ReminderManager? = null

    // Camera components
    private var cameraExecutor: ExecutorService? = null
    private var imageAnalysis: ImageAnalysis? = null

    // State flows
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _postureScore = MutableStateFlow(PostureScore())
    val postureScore: StateFlow<PostureScore> = _postureScore.asStateFlow()

    private val _userBehavior = MutableStateFlow(UserBehavior())
    val userBehavior: StateFlow<UserBehavior> = _userBehavior.asStateFlow()

    private val _sessionStats = MutableStateFlow(SessionStats())
    val sessionStats: StateFlow<SessionStats> = _sessionStats.asStateFlow()

    private val _activeReminder = MutableStateFlow<ReminderEvent?>(null)
    val activeReminder: StateFlow<ReminderEvent?> = _activeReminder.asStateFlow()

    private val _cameraPermissionGranted = MutableStateFlow(false)
    val cameraPermissionGranted: StateFlow<Boolean> = _cameraPermissionGranted.asStateFlow()

    /**
     * Initialize the posture monitoring system
     */
    fun initialize(context: Context) {
        Logger.i("PostureViewModel", "Initializing posture monitoring system")

        try {
            // Initialize core components
            tiltSensorManager = TiltSensorManager(context)
            visionAIDetector = VisionAIDetector(context)
            postureScorer = PostureScorer(viewModelScope)
            reminderManager = ReminderManager(context, viewModelScope)

            // Initialize camera executor
            cameraExecutor = Executors.newSingleThreadExecutor()

            // Set up data flow connections
            setupDataFlow()

            Logger.i("PostureViewModel", "Posture monitoring system initialized successfully")

        } catch (e: Exception) {
            Logger.e("PostureViewModel", "Failed to initialize posture monitoring system", e)
        }
    }

    /**
     * Set up data flow between components
     */
    private fun setupDataFlow() {
        val scorer = postureScorer ?: return
        val reminderMgr = reminderManager ?: return

        // Connect posture scorer to sensors
        viewModelScope.launch {
            tiltSensorManager?.let { tiltSensor ->
                visionAIDetector?.let { visionAI ->
                    scorer.processPostureData(tiltSensor, visionAI)
                }
            }
        }

        // Observe posture scores
        viewModelScope.launch {
            scorer.currentScore.collect { score ->
                _postureScore.value = score
                Logger.logPostureScore(score)
            }
        }

        // Observe user behavior
        viewModelScope.launch {
            scorer.userBehavior.collect { behavior ->
                _userBehavior.value = behavior
            }
        }

        // Observe session stats
        viewModelScope.launch {
            flow {
                while (true) {
                    emit(scorer.getSessionStats())
                    kotlinx.coroutines.delay(5000) // Update every 5 seconds
                }
            }.collect { stats ->
                _sessionStats.value = stats
            }
        }

        // Connect reminder manager
        reminderMgr.startReminderMonitoring(
            postureScoreFlow = _postureScore,
            userBehaviorFlow = _userBehavior
        )

        // Observe active reminders
        viewModelScope.launch {
            reminderMgr.activeReminder.collect { reminder ->
                _activeReminder.value = reminder
                reminder?.let { Logger.logReminderEvent(it) }
            }
        }
    }

    /**
     * Start posture monitoring
     */
    suspend fun startMonitoring() {
        if (_isMonitoring.value) return

        Logger.i("PostureViewModel", "Starting posture monitoring")

        try {
            // Start tilt sensor monitoring
            tiltSensorManager?.startMonitoring()
            Logger.logSensorStatus("TiltSensor", true)

            // Start camera for vision AI (would need camera permission check)
            startCameraAnalysis()

            _isMonitoring.value = true
            Logger.i("PostureViewModel", "Posture monitoring started successfully")

        } catch (e: Exception) {
            Logger.e("PostureViewModel", "Failed to start posture monitoring", e)
            _isMonitoring.value = false
        }
    }

    /**
     * Stop posture monitoring
     */
    suspend fun stopMonitoring() {
        if (!_isMonitoring.value) return

        Logger.i("PostureViewModel", "Stopping posture monitoring")

        try {
            // Stop tilt sensor monitoring
            tiltSensorManager?.stopMonitoring()
            Logger.logSensorStatus("TiltSensor", false)

            // Stop camera analysis
            stopCameraAnalysis()

            // Stop reminder monitoring
            reminderManager?.stopReminderMonitoring()

            _isMonitoring.value = false
            Logger.i("PostureViewModel", "Posture monitoring stopped successfully")

        } catch (e: Exception) {
            Logger.e("PostureViewModel", "Error stopping posture monitoring", e)
        }
    }

    /**
     * Start camera analysis for vision AI
     */
    private fun startCameraAnalysis() {
        val detector = visionAIDetector ?: return

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor!!, detector)
            }

        Logger.logSensorStatus("VisionAI", true)
    }

    /**
     * Stop camera analysis
     */
    private fun stopCameraAnalysis() {
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        Logger.logSensorStatus("VisionAI", false)
    }

    /**
     * Handle user action from reminder
     */
    fun handleUserAction(action: UserAction) {
        Logger.d("PostureViewModel", "User action: $action")

        val reminder = _activeReminder.value ?: return

        val response = ReminderResponse(
            wasAcknowledged = action == UserAction.CORRECTED_POSTURE,
            wasIgnored = action == UserAction.DISMISSED,
            responseTime = System.currentTimeMillis() - reminder.timestamp,
            userAction = action
        )

        reminderManager?.handleReminderResponse(response)

        // Handle specific actions
        when (action) {
            UserAction.CORRECTED_POSTURE -> {
                // User claims to have corrected posture
                // Could trigger a brief validation period
            }
            UserAction.POSTPONED -> {
                // Postpone next reminder
            }
            UserAction.DISABLED_TEMPORARILY -> {
                // Disable reminders for 30 minutes
                viewModelScope.launch {
                    stopMonitoring()
                    kotlinx.coroutines.delay(30 * 60 * 1000) // 30 minutes
                    startMonitoring()
                }
            }
            UserAction.DISMISSED -> {
                // Just acknowledge dismissal
            }
        }
    }

    /**
     * Dismiss active reminder
     */
    fun dismissReminder() {
        _activeReminder.value = null
    }

    /**
     * Reset session data
     */
    fun resetSession() {
        postureScorer?.resetSession()
        _sessionStats.value = SessionStats()
        Logger.i("PostureViewModel", "Session data reset")
    }

    /**
     * Update reminder configuration
     */
    fun updateReminderConfig(config: ReminderConfig) {
        reminderManager?.updateConfig(config)
        Logger.i("PostureViewModel", "Reminder configuration updated")
    }

    /**
     * Get current reminder statistics
     */
    fun getReminderStats(): ReminderStats? {
        return reminderManager?.getReminderStats()
    }

    /**
     * Calibrate sensors
     */
    fun calibrateSensors() {
        tiltSensorManager?.calibrate()
        Logger.i("PostureViewModel", "Sensors calibrated")
    }

    /**
     * Check if device is stable for accurate measurements
     */
    fun isDeviceStable(): Boolean {
        return tiltSensorManager?.isDeviceStable() ?: false
    }

    /**
     * Get current tilt angle
     */
    fun getCurrentTiltAngle(): Float {
        return tiltSensorManager?.tiltAngle?.value ?: 0f
    }

    /**
     * Check if face is detected and suitable for analysis
     */
    fun isFaceDetected(): Boolean {
        return visionAIDetector?.faceDetected?.value ?: false
    }

    /**
     * Get vision AI confidence
     */
    fun getVisionConfidence(): Float {
        return visionAIDetector?.confidence?.value ?: 0f
    }

    /**
     * Export session data for analysis
     */
    fun exportSessionData(): String {
        val stats = _sessionStats.value
        val behavior = _userBehavior.value
        val reminderStats = getReminderStats()

        return buildString {
            appendLine("=== StraightUP Session Report ===")
            appendLine("Generated: ${java.util.Date()}")
            appendLine()
            appendLine("Posture Statistics:")
            appendLine("Average Score: ${stats.averageScore}")
            appendLine("Best Score: ${stats.bestScore}")
            appendLine("Worst Score: ${stats.worstScore}")
            appendLine("Total Measurements: ${stats.totalMeasurements}")
            appendLine("Improvement Trend: ${stats.improvementTrend}")
            appendLine()
            appendLine("User Behavior:")
            appendLine("Session Duration: ${behavior.sessionDuration} minutes")
            appendLine("Average Posture Score: ${behavior.averagePostureScore}")
            appendLine("Reminder Response Rate: ${(behavior.reminderResponseRate * 100).toInt()}%")
            appendLine()
            reminderStats?.let { rs ->
                appendLine("Reminder Statistics:")
                appendLine("Total Reminders: ${rs.totalReminders}")
                appendLine("Acknowledged Reminders: ${rs.acknowledgedReminders}")
                appendLine("Response Rate: ${(rs.responseRate * 100).toInt()}%")
                appendLine("Average Interval: ${rs.averageInterval / 1000}s")
            }
            appendLine()
            appendLine("=== End Report ===")
        }
    }

    override fun onCleared() {
        super.onCleared()

        Logger.i("PostureViewModel", "Cleaning up resources")

        viewModelScope.launch {
            stopMonitoring()
        }

        // Release resources
        visionAIDetector?.release()
        cameraExecutor?.shutdown()

        Logger.i("PostureViewModel", "Resources cleaned up")
    }
}
