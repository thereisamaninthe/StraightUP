package com.example.straightup.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Manages device tilt sensors for posture monitoring
 */
class TiltSensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _tiltAngle = MutableStateFlow(0f)
    val tiltAngle: StateFlow<Float> = _tiltAngle.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private var gravity = FloatArray(3)
    private var gyroValues = FloatArray(3)

    // Filtering parameters
    private val alpha = 0.8f  // Low-pass filter coefficient

    /**
     * Start monitoring device tilt
     */
    fun startMonitoring() {
        if (_isMonitoring.value) return

        accelerometer?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }

        gyroscope?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }

        _isMonitoring.value = true
    }

    /**
     * Stop monitoring device tilt
     */
    fun stopMonitoring() {
        if (!_isMonitoring.value) return

        sensorManager.unregisterListener(this)
        _isMonitoring.value = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            when (sensorEvent.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    // Apply low-pass filter to reduce noise
                    gravity[0] = alpha * gravity[0] + (1 - alpha) * sensorEvent.values[0]
                    gravity[1] = alpha * gravity[1] + (1 - alpha) * sensorEvent.values[1]
                    gravity[2] = alpha * gravity[2] + (1 - alpha) * sensorEvent.values[2]

                    calculateTiltAngle()
                }

                Sensor.TYPE_GYROSCOPE -> {
                    gyroValues = sensorEvent.values.clone()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    /**
     * Calculate device tilt angle from accelerometer data
     */
    private fun calculateTiltAngle() {
        val magnitude = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])

        if (magnitude > 0) {
            // Normalize gravity vector
            val normX = gravity[0] / magnitude
            val normY = gravity[1] / magnitude
            val normZ = gravity[2] / magnitude

            // Calculate tilt angle in degrees
            // This represents the angle between device and vertical (neck angle approximation)
            val tiltRadians = atan2(sqrt(normX * normX + normY * normY), normZ)
            val tiltDegrees = Math.toDegrees(tiltRadians.toDouble()).toFloat()

            // Adjust for portrait orientation (device held upright)
            val adjustedTilt = when {
                tiltDegrees < 30f -> 0f  // Device is upright
                tiltDegrees > 150f -> 0f // Device is upside down (shouldn't happen in portrait)
                else -> tiltDegrees - 90f // Convert to neck angle relative to vertical
            }

            _tiltAngle.value = adjustedTilt
        }
    }

    /**
     * Get current gyroscope data for additional motion detection
     */
    fun getGyroscopeData(): FloatArray = gyroValues.clone()

    /**
     * Check if device is stable (not moving much)
     */
    fun isDeviceStable(): Boolean {
        val threshold = 0.1f
        return gyroValues.all { kotlin.math.abs(it) < threshold }
    }

    /**
     * Calibrate the sensor (reset baseline)
     */
    fun calibrate() {
        gravity = FloatArray(3)
        gyroValues = FloatArray(3)
        _tiltAngle.value = 0f
    }
}
