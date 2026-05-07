package com.android.assignment

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

object BlurDetector {

    private const val BLUR_THRESHOLD = 30.0

    /**
     * Detects if a bitmap is blurry using Laplacian variance method
     * Pure Android implementation - no OpenCV required
     */
    fun detectBlur(bitmap: Bitmap): Boolean {
        val blurScore = calculateBlurScore(bitmap)
        return blurScore < BLUR_THRESHOLD
    }

    /**
     * Calculates blur score using Laplacian variance
     * Higher score = sharper image
     */
    fun calculateBlurScore(bitmap: Bitmap): Double {
        // Scale down for faster processing
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 320, 240, true)

        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val pixels = IntArray(width * height)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Apply Laplacian operator
        val laplacian = applyLaplacian(pixels, width, height)

        // Calculate variance
        val mean = laplacian.average()
        val variance = laplacian.map { (it - mean) * (it - mean) }.average()

        scaledBitmap.recycle()

        return variance
    }

    private fun applyLaplacian(pixels: IntArray, width: Int, height: Int): DoubleArray {
        val result = DoubleArray(width * height)
        // Standard Laplacian kernel
        val kernel = arrayOf(
            intArrayOf(0, 1, 0),
            intArrayOf(1, -4, 1),
            intArrayOf(0, 1, 0)
        )

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sum = 0.0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val idx = (y + ky) * width + (x + kx)
                        val gray = getGrayValue(pixels[idx])
                        sum += gray * kernel[ky + 1][kx + 1]
                    }
                }
                result[y * width + x] = sum
            }
        }

        return result
    }

    private fun getGrayValue(pixel: Int): Double {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        // Standard grayscale conversion
        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    /**
     * Simple edge detection based blur detection
     */
    fun isBlurry(bitmap: Bitmap, threshold: Double = 15.0): Boolean {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, true)

        val width = scaledBitmap.width
        val height = scaledBitmap.height
        var totalEdgeStrength = 0.0
        var pixelCount = 0

        for (y in 0 until height - 1) {
            for (x in 0 until width - 1) {
                val pixel1 = scaledBitmap.getPixel(x, y)
                val pixel2 = scaledBitmap.getPixel(x + 1, y)
                val pixel3 = scaledBitmap.getPixel(x, y + 1)

                val gray1 = getGrayValue(pixel1)
                val gray2 = getGrayValue(pixel2)
                val gray3 = getGrayValue(pixel3)

                val horizontalEdge = kotlin.math.abs(gray1 - gray2)
                val verticalEdge = kotlin.math.abs(gray1 - gray3)
                val edgeStrength = sqrt(horizontalEdge * horizontalEdge + verticalEdge * verticalEdge)

                totalEdgeStrength += edgeStrength
                pixelCount++
            }
        }

        scaledBitmap.recycle()

        val averageEdgeStrength = totalEdgeStrength / pixelCount
        return averageEdgeStrength < threshold
    }
}