package com.example.straightup.sensors

import android.content.Context
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.example.straightup.models.HeadPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Vision AI detector for face tracking and distance measurement
 */
class VisionAIDetector(private val context: Context) : ImageAnalysis.Analyzer {

    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.1f)
        .enableTracking()
        .build()

    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    private val _headDistance = MutableStateFlow(0f)
    val headDistance: StateFlow<Float> = _headDistance.asStateFlow()

    private val _headPosition = MutableStateFlow(HeadPosition())
    val headPosition: StateFlow<HeadPosition> = _headPosition.asStateFlow()

    private val _faceDetected = MutableStateFlow(false)
    val faceDetected: StateFlow<Boolean> = _faceDetected.asStateFlow()

    private val _confidence = MutableStateFlow(0f)
    val confidence: StateFlow<Float> = _confidence.asStateFlow()

    // Camera parameters for distance calculation
    private val focalLength = 500f  // Approximate focal length in pixels
    private val averageFaceWidth = 14f  // Average face width in cm

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    processFaces(faces, imageProxy.width, imageProxy.height)
                }
                .addOnFailureListener { exception ->
                    _faceDetected.value = false
                    _confidence.value = 0f
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun processFaces(faces: List<Face>, imageWidth: Int, imageHeight: Int) {
        if (faces.isEmpty()) {
            _faceDetected.value = false
            _confidence.value = 0f
            return
        }

        // Use the largest face (closest to camera)
        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }

        primaryFace?.let { face ->
            _faceDetected.value = true

            // Calculate distance from face size
            val faceWidthPixels = face.boundingBox.width().toFloat()
            val distance = calculateDistance(faceWidthPixels)
            _headDistance.value = distance

            // Calculate head position relative to center
            val headPos = calculateHeadPosition(face.boundingBox, imageWidth, imageHeight, face)
            _headPosition.value = headPos

            // Calculate confidence based on face detection quality
            val confidence = calculateConfidence(face, faceWidthPixels, imageWidth, imageHeight)
            _confidence.value = confidence
        }
    }

    /**
     * Calculate distance from camera to face using face width
     */
    private fun calculateDistance(faceWidthPixels: Float): Float {
        return if (faceWidthPixels > 0) {
            (averageFaceWidth * focalLength) / faceWidthPixels
        } else {
            0f
        }
    }

    /**
     * Calculate head position relative to device center
     */
    private fun calculateHeadPosition(
        boundingBox: Rect,
        imageWidth: Int,
        imageHeight: Int,
        face: Face
    ): HeadPosition {
        val centerX = imageWidth / 2f
        val centerY = imageHeight / 2f

        val faceCenterX = boundingBox.centerX().toFloat()
        val faceCenterY = boundingBox.centerY().toFloat()

        // Calculate offset from center as percentage
        val xOffset = ((faceCenterX - centerX) / centerX) * 100f
        val yOffset = ((faceCenterY - centerY) / centerY) * 100f

        // Calculate head rotation (Euler Y angle if available)
        val rotation = face.headEulerAngleY

        return HeadPosition(
            x = xOffset,
            y = yOffset,
            rotation = rotation
        )
    }

    /**
     * Calculate confidence score based on various factors
     */
    private fun calculateConfidence(
        face: Face,
        faceWidthPixels: Float,
        imageWidth: Int,
        imageHeight: Int
    ): Float {
        var confidence = 0f

        // Face size confidence (larger faces are more reliable)
        val sizeRatio = faceWidthPixels / imageWidth
        val sizeConfidence = when {
            sizeRatio > 0.3f -> 1f
            sizeRatio > 0.2f -> 0.8f
            sizeRatio > 0.1f -> 0.6f
            else -> 0.3f
        }
        confidence += sizeConfidence * 0.4f

        // Face position confidence (centered faces are more reliable)
        val centerX = imageWidth / 2f
        val centerY = imageHeight / 2f
        val faceCenterX = face.boundingBox.centerX().toFloat()
        val faceCenterY = face.boundingBox.centerY().toFloat()

        val distanceFromCenter = sqrt(
            (faceCenterX - centerX) * (faceCenterX - centerX) +
            (faceCenterY - centerY) * (faceCenterY - centerY)
        )
        val maxDistance = sqrt(centerX * centerX + centerY * centerY)
        val positionConfidence = 1f - (distanceFromCenter / maxDistance)
        confidence += positionConfidence * 0.3f

        // Landmarks confidence
        val landmarks = face.allLandmarks
        val landmarkConfidence = if (landmarks.size >= 5) 1f else landmarks.size / 5f
        confidence += landmarkConfidence * 0.3f

        return confidence.coerceIn(0f, 1f)
    }

    /**
     * Check if the detected face is suitable for posture analysis
     */
    fun isFaceSuitableForAnalysis(): Boolean {
        return _faceDetected.value && _confidence.value > 0.5f
    }

    /**
     * Get the current tracking ID if available
     */
    fun getCurrentTrackingId(): Int? {
        // This would need to be stored from the last processed face
        return null // Placeholder
    }

    /**
     * Release resources
     */
    fun release() {
        faceDetector.close()
    }
}
