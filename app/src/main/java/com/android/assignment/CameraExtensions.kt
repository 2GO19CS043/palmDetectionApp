package com.android.assignment

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

fun ImageProxy.toBitmap(): Bitmap? {
    val buffer: ByteBuffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    // Convert bytes to bitmap based on image format
    // Implementation depends on actual format
    return null // Placeholder
}

fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}