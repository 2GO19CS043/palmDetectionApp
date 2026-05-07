package com.android.assignment

data class FingerDetectionResult(
    val fingerType: FingerType,
    val confidence: Float,
    val minutiaeMatches: Int
)

