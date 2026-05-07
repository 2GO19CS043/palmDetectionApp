package com.android.assignment

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HandDetectionViewModel : ViewModel() {

    enum class HandSide { LEFT, RIGHT, UNKNOWN, NONE }
    enum class FingerType {
        THUMB, INDEX, MIDDLE, RING, LITTLE, UNKNOWN;

        companion object {
            val ALL = listOf(THUMB, INDEX, MIDDLE, RING, LITTLE)
        }

        fun getDisplayName(): String = when(this) {
            THUMB -> "Thumb"
            INDEX -> "Index"
            MIDDLE -> "Middle"
            RING -> "Ring"
            LITTLE -> "Little"
            UNKNOWN -> "Unknown"
        }
    }

    data class HandDetectionResult(
        val handSide: HandSide,
        val isDorsal: Boolean,
        val landmarks: List<PointF>,
        val boundingBox: RectF,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class FingerMinutiae(
        val fingerType: FingerType,
        val minutiaePoints: List<PointF>,
        val pattern: String, // Simplified pattern representation
        val confidence: Float
    )

    data class FingerDetectionResult(
        val fingerType: FingerType,
        val confidence: Float,
        val isMatching: Boolean,
        val matchScore: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ImageMetrics(
        val brightnessScore: Float,
        val blurScore: Double,
        val focusDistance: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _handDetected = MutableLiveData<HandDetectionResult>()
    val handDetected: LiveData<HandDetectionResult> = _handDetected

    private val _fingerDetected = MutableLiveData<FingerDetectionResult>()
    val fingerDetected: LiveData<FingerDetectionResult> = _fingerDetected

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _currentFingerIndex = MutableLiveData(0)
    val currentFingerIndex: LiveData<Int> = _currentFingerIndex

    private val _imageMetrics = MutableLiveData<ImageMetrics>()
    val imageMetrics: LiveData<ImageMetrics> = _imageMetrics

    // Store captured palm minutiae
    private var capturedPalmMinutiae: Map<FingerType, FingerMinutiae> = emptyMap()
    private var capturedHandSide: HandSide = HandSide.NONE
    private var expectedNextFinger: FingerType = FingerType.THUMB

    private var capturedPalmImage: Bitmap? = null

    // Track scanned fingers
    private val scannedFingers = mutableSetOf<FingerType>()

    // Track session ID for device-specific data
    private val sessionId: String = System.currentTimeMillis().toString()

    fun processHandDetectionResult(
        handSide: HandSide,
        isDorsal: Boolean,
        landmarks: List<PointF>,
        boundingBox: RectF
    ) {
        capturedHandSide = handSide

        // Reset scanned fingers when new palm is detected
        if (!isDorsal && handSide != HandSide.UNKNOWN && handSide != HandSide.NONE) {
            scannedFingers.clear()
            expectedNextFinger = FingerType.THUMB  // Add this line
            _currentFingerIndex.value = 0
        }

        _handDetected.value = HandDetectionResult(
            handSide = handSide,
            isDorsal = isDorsal,
            landmarks = landmarks,
            boundingBox = boundingBox
        )

        if (isDorsal) {
            _errorMessage.value = "Palm dorsal side detected, minutiae points won't be extracted."
        } else {
            extractMinutiaeFromPalm()
        }
    }

    fun setCapturedPalmImage(bitmap: Bitmap) {
        capturedPalmImage = bitmap
        _handDetected.value?.isDorsal?.let {
            if (!it == true) {
                extractMinutiaeFromPalm()
            }
        }
    }

    private fun extractMinutiaeFromPalm() {
        // Simulate minutiae extraction for each finger position
        val minutiaeMap = mutableMapOf<FingerType, FingerMinutiae>()

        FingerType.ALL.forEach { fingerType ->
            // In production, this would use actual image processing
            // Here we simulate with dummy minutiae points
            val dummyPoints = List(5) { PointF(it * 50f, it * 50f) }
            minutiaeMap[fingerType] = FingerMinutiae(
                fingerType = fingerType,
                minutiaePoints = dummyPoints,
                pattern = generateFingerprintPattern(fingerType),
                confidence = 0.85f
            )
        }

        capturedPalmMinutiae = minutiaeMap
    }

    private fun generateFingerprintPattern(fingerType: FingerType): String {
        // Generate a simplified pattern string based on finger type and hand side
        val basePattern = when (fingerType) {
            FingerType.THUMB -> "WHORL"
            FingerType.INDEX -> "LOOP"
            FingerType.MIDDLE -> "ARCH"
            FingerType.RING -> "LOOP"
            FingerType.LITTLE -> "ARCH"
            else -> "UNKNOWN"
        }
        return "${capturedHandSide.name}_${basePattern}_${fingerType.name}"
    }

    fun validateFingerAgainstPalm(
        fingerBitmap: Bitmap,
        claimedFingerType: FingerType
    ): Pair<Boolean, Float> {
        // Check if palm has been captured
        if (capturedPalmMinutiae.isEmpty()) {
            _errorMessage.value = "Please capture palm first before scanning fingers"
            return Pair(false, 0f)
        }

        // Check if hand side was detected
        if (capturedHandSide == HandSide.NONE || capturedHandSide == HandSide.UNKNOWN) {
            _errorMessage.value = "Hand side not detected properly. Please recapture palm."
            return Pair(false, 0f)
        }

        // In production, this would do actual minutiae matching
        // Here we simulate matching with a match score

        val expectedMinutiae = capturedPalmMinutiae[claimedFingerType]
        if (expectedMinutiae == null) {
            return Pair(false, 0f)
        }

        // Simulate matching algorithm
        // For demo, we generate a match score based on finger type consistency
        val matchScore = when {
            !isSamePerson(fingerBitmap) -> 0.1f  // Different person
            !isSameHand(fingerBitmap) -> 0.2f    // Different hand
            else -> 0.85f + Math.random().toFloat() * 0.1f  // Same person, same hand
        }

        val isMatching = matchScore > 0.7f

        if (!isMatching) {
            val errorMsg = if (matchScore < 0.15f) {
                "Finger does not match - Different person detected"
            } else if (matchScore < 0.3f) {
                "Incorrect Finger - Hand side mismatch"
            } else {
                "Finger does not match the palm record"
            }
            _errorMessage.value = errorMsg
        }

        return Pair(isMatching, matchScore)
    }

    private fun isSamePerson(fingerBitmap: Bitmap): Boolean {
        // In production, implement actual person identification
        // For demo, we simulate by checking if we're in the same session
        // In real implementation, you'd compare against stored palm features
        return true  // Simplified for demo
    }

    private fun isSameHand(fingerBitmap: Bitmap): Boolean {
        // In production, analyze hand side from finger image
        // For demo, we assume same hand unless specified otherwise
        return true  // Simplified for demo
    }

    fun processFingerDetection(
        bitmap: Bitmap,
        detectedFingerType: FingerType
    ): Boolean {
        // Check if palm has been captured
        if (capturedHandSide == HandSide.NONE || capturedHandSide == HandSide.UNKNOWN) {
            _errorMessage.value = "Please capture palm first"
            return false
        }

        // CRITICAL: Check if this finger has already been captured
        if (scannedFingers.contains(detectedFingerType)) {
            _errorMessage.value = "${detectedFingerType.getDisplayName()} finger already captured! Please scan ${getNextFingerToScan().getDisplayName()} finger next."
            return false
        }

        // CRITICAL: Check if it's the expected finger (maintains order)
        if (detectedFingerType != expectedNextFinger) {
            val scannedList = scannedFingers.map { it.getDisplayName() }.joinToString(", ")
            _errorMessage.value = "Please scan ${expectedNextFinger.getDisplayName()} finger next.\n✅ Captured: ${if (scannedList.isEmpty()) "None" else scannedList}\n⏳ Expected: ${expectedNextFinger.getDisplayName()}"
            return false
        }

        // Validate against palm records
        val (isMatching, matchScore) = validateFingerAgainstPalm(bitmap, detectedFingerType)

        val result = FingerDetectionResult(
            fingerType = detectedFingerType,
            confidence = matchScore,
            isMatching = isMatching,
            matchScore = matchScore
        )

        _fingerDetected.value = result

        if (isMatching && detectedFingerType != FingerType.UNKNOWN) {
            scannedFingers.add(detectedFingerType)
            _currentFingerIndex.value = scannedFingers.size
            // Update expected next finger
            updateExpectedNextFinger()
            return true
        }

        return false
    }

    // Add this new helper function
    private fun updateExpectedNextFinger() {
        val remaining = FingerType.ALL.filter { it !in scannedFingers }
        expectedNextFinger = remaining.firstOrNull() ?: FingerType.UNKNOWN
    }

    fun getNextFingerToScan(): FingerType {
        val remaining = FingerType.ALL.filter { it !in scannedFingers }
        return remaining.firstOrNull() ?: FingerType.UNKNOWN
    }

    fun getScannedFingersCount(): Int = scannedFingers.size

    fun getTotalFingersRequired(): Int = 5

    fun isCaptureComplete(): Boolean = scannedFingers.size >= 5

    fun getCapturedHandSide(): HandSide = capturedHandSide

    fun updateImageMetrics(metrics: ImageMetrics) {
        _imageMetrics.value = metrics
    }

    fun getSessionId(): String = sessionId

    fun reset() {
        capturedPalmMinutiae = emptyMap()
        capturedHandSide = HandSide.NONE
        capturedPalmImage = null
        scannedFingers.clear()
        expectedNextFinger = FingerType.THUMB  // Add this line
        _currentFingerIndex.value = 0
        _handDetected.value = null
        _fingerDetected.value = null
        _errorMessage.value = null
        _imageMetrics.value = null
    }
    fun getScannedFingersList(): String {
        return scannedFingers.map { it.getDisplayName() }.joinToString(", ")
    }

    fun getRemainingFingersList(): String {
        val remaining = FingerType.ALL.filter { it !in scannedFingers }
        return remaining.map { it.getDisplayName() }.joinToString(", ")
    }
    fun getFingerDetectionStatus(): String {
        val scanned = scannedFingers.map { it.getDisplayName() }
        val remaining = FingerType.ALL.filter { it !in scannedFingers }.map { it.getDisplayName() }
        return "Scanned: ${scanned.joinToString()} | Remaining: ${remaining.joinToString()}"
    }
}