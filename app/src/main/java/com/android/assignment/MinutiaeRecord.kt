package com.android.assignment

import android.graphics.PointF

data class MinutiaeRecord(
    val point: PointF,
    val timestamp: Long,
    val ridgeEnding: Boolean = true,
    val bifurcation: Boolean = false,
    val angle: Float = 0f
)