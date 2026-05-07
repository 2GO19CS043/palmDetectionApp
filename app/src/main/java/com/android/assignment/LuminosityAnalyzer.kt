package com.android.assignment

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

@OptIn(ExperimentalGetImage::class)
class LuminosityAnalyzer(private val listener: (Double) -> Unit) : ImageAnalysis.Analyzer {

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }

    override fun analyze(imageProxy: ImageProxy) {
        val buffer = imageProxy.planes[0].buffer
        val data = buffer.toByteArray()
        val pixels = data.map { it.toInt() and 0xFF }
        if (pixels.isNotEmpty()) {
            val luminosity = pixels.average()
            listener(luminosity)
        }
        imageProxy.close()
    }
}