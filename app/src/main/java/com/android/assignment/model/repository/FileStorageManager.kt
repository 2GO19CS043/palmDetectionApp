package com.android.assignment

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileStorageManager(private val context: Context) {

    companion object {
        private const val FINGER_DATA_DIR = "Finger Data"
    }

    fun savePalmImage(bitmap: Bitmap, handSide: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "${handSide}_Hand_$timestamp.png"
        return saveImage(bitmap, filename)
    }

    fun saveFingerImage(bitmap: Bitmap, handSide: String, fingerType: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "${handSide}_Hand_${fingerType}_Finger_$timestamp.jpg"
        return saveImage(bitmap, filename)
    }

    private fun saveImage(bitmap: Bitmap, filename: String): File {
        val fingerDataDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            FINGER_DATA_DIR
        )

        if (!fingerDataDir.exists()) {
            fingerDataDir.mkdirs()
        }

        val file = File(fingerDataDir, filename)

        FileOutputStream(file).use { out ->
            val format = if (filename.endsWith(".png")) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            bitmap.compress(format, 95, out)
        }

        // Notify media scanner
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            null,
            null
        )

        return file
    }

    fun loadLuminosityData(): LuminosityData? {
        val prefs = context.getSharedPreferences("camera_analytics", Context.MODE_PRIVATE)
        return try {
            LuminosityData(
                brightnessScore = prefs.getFloat("brightness_score", 0f),
                cameraType = prefs.getString("camera_type", "REAR") ?: "REAR",
                focalLength = prefs.getFloat("focal_length", 0f),
                aperture = prefs.getFloat("aperture", 0f),
                focusDistance = prefs.getFloat("focus_distance", 0f),
                blurScore = prefs.getFloat("blur_score", 0f).toDouble(),
                deviceId = prefs.getString("device_id", "") ?: ""
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveLuminosityData(data: LuminosityData) {
        val prefs = context.getSharedPreferences("camera_analytics", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat("brightness_score", data.brightnessScore)
            putString("camera_type", data.cameraType)
            putFloat("focal_length", data.focalLength)
            putFloat("aperture", data.aperture)
            putFloat("focus_distance", data.focusDistance)
            putFloat("blur_score", data.blurScore.toFloat())
            putString("device_id", data.deviceId)
            apply()
        }
    }
}