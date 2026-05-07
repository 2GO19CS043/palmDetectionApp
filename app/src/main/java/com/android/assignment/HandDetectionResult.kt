package com.android.assignment

import android.graphics.PointF
import android.graphics.RectF

data class HandDetectionResult(
    val handSide: HandSide,
    val isDorsal: Boolean,
    val minutiaePoints: List<PointF>,
    val boundingBox: RectF
)

