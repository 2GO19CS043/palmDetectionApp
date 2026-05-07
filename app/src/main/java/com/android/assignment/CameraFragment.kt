package com.android.assignment

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.ExperimentalGetImage
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
class CameraFragment : Fragment() {

    private lateinit var viewModel: HandDetectionViewModel
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var progressIndicator: TextView
    private lateinit var captureButton: ImageButton
    private lateinit var toggleModeButton: Button
    private lateinit var resetButton: Button
    private lateinit var statusText: TextView

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var poseDetector: PoseDetector? = null
    private lateinit var cameraExecutor: ExecutorService
    private var currentLuminosity: Double = 0.0
    private var currentFingerBeingScanned: HandDetectionViewModel.FingerType = HandDetectionViewModel.FingerType.THUMB

    enum class ScanMode { PALM, FINGER }
    private var currentMode = ScanMode.PALM
    private var lastErrorMessage = ""
    private var lastErrorTime = 0L
    private val ERROR_COOLDOWN_MS = 3000L // 3 seconds cooldown

    companion object {
        private const val FINGER_SCAN_TIMEOUT_MS = 5000L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_camera, container, false).apply {
            previewView = findViewById(R.id.previewView)
            overlayView = findViewById(R.id.overlayView)
            progressIndicator = findViewById(R.id.progressIndicator)
            captureButton = findViewById(R.id.captureButton)
            toggleModeButton = findViewById(R.id.toggleModeButton)
            resetButton = findViewById(R.id.resetButton)
            statusText = findViewById(R.id.statusText)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[HandDetectionViewModel::class.java]

        cameraExecutor = Executors.newSingleThreadExecutor()
        overlayView.bringToFront()
        overlayView.visibility = View.VISIBLE

        setupCaptureButton()
        setupCamera()
        setupToggleButton()
        setupResetButton()
        setupPoseDetector()
        observeViewModel()
        updateProgressIndicator()
        updateStatusText()
    }

    private fun setupToggleButton() {
        toggleModeButton.setOnClickListener {
            if (viewModel.handDetected.value == null && currentMode == ScanMode.FINGER) {
                Toast.makeText(context, "Please capture palm first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            currentMode = if (currentMode == ScanMode.PALM) {
                toggleModeButton.text = "Switch to Palm Mode"
                overlayView.switchToFingerMode()
                ScanMode.FINGER
            } else {
                toggleModeButton.text = "Switch to Finger Mode"
                overlayView.switchToPalmMode()
                ScanMode.PALM
            }
            updateStatusText()
        }
    }

    private fun setupResetButton() {
        resetButton.setOnClickListener {
            resetCapture()
        }
    }

    private fun resetCapture() {
        viewModel.reset()
        currentMode = ScanMode.PALM
        currentFingerBeingScanned = HandDetectionViewModel.FingerType.THUMB
        toggleModeButton.text = "Switch to Finger Mode"
        overlayView.switchToPalmMode()
        overlayView.clearOverlay()
        updateProgressIndicator()
        updateStatusText()
        Toast.makeText(context, "Reset complete. Please capture palm again.", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatusText(text: String? = null) {
        if (::statusText.isInitialized) {
            statusText.text = text ?: when (currentMode) {
                ScanMode.PALM -> "Show your palm clearly"
                ScanMode.FINGER -> {
                    val nextFinger = viewModel.getNextFingerToScan()
                    val scannedCount = viewModel.getScannedFingersCount()
                    if (nextFinger != HandDetectionViewModel.FingerType.UNKNOWN) {
                        "Place ${nextFinger.getDisplayName()} finger ($scannedCount/5)"
                    } else {
                        "All fingers captured! 🎉"
                    }
                }
            }
        }
    }

    private fun setupCaptureButton() {
        captureButton.setOnClickListener {
            when (currentMode) {
                ScanMode.PALM -> capturePalmImage()
                ScanMode.FINGER -> captureFingerImage()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (!::cameraExecutor.isInitialized || cameraExecutor.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
            setupCamera()
        }
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // Combine pose and luminosity analysis into a single analyzer
                val combinedImageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, CombinedAnalyzer())
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    combinedImageAnalysis  // Only ONE ImageAnalysis
                )
                setupAutoFocus()
            } catch (e: Exception) {
                Log.e("CameraFragment", "Camera setup failed", e)
                Toast.makeText(context, "Camera setup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    // Add this inside CameraFragment.kt as an inner class
    private inner class CombinedAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            // First, analyze luminosity
            val buffer = imageProxy.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val pixels = data.map { it.toInt() and 0xFF }
            if (pixels.isNotEmpty()) {
                val luminosity = pixels.average()
                currentLuminosity = luminosity
                adjustExposure(luminosity)
            }

            // Then, analyze pose
            when (currentMode) {
                ScanMode.PALM -> analyzePalm(imageProxy)
                ScanMode.FINGER -> analyzeFinger(imageProxy)
            }
        }
    }

    private fun adjustExposure(luminosity: Double) {
        camera?.cameraControl?.let { cameraControl ->
            val exposureState = camera?.cameraInfo?.exposureState
            if (exposureState != null && exposureState.isExposureCompensationSupported) {
                val currentExposureIndex = exposureState.exposureCompensationIndex
                val minExposureIndex = exposureState.exposureCompensationRange.lower
                val maxExposureIndex = exposureState.exposureCompensationRange.upper

                val targetExposureIndex = when {
                    luminosity < 50 -> (currentExposureIndex + 1).coerceAtMost(maxExposureIndex)
                    luminosity > 150 -> (currentExposureIndex - 1).coerceAtLeast(minExposureIndex)
                    else -> currentExposureIndex
                }

                if (targetExposureIndex != currentExposureIndex) {
                    cameraControl.setExposureCompensationIndex(targetExposureIndex)
                        .addListener({
                            Log.d("CameraFragment", "Adjusted exposure to: $targetExposureIndex, Luminosity: $luminosity")
                        }, ContextCompat.getMainExecutor(requireContext()))
                }
            }
        }
    }

    private fun setupAutoFocus() {
        camera?.cameraControl?.let { cameraControl ->
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(previewView.width / 2f, previewView.height / 2f, 0.5f)

            val action = FocusMeteringAction.Builder(point)
                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            cameraControl.startFocusAndMetering(action)
        }
    }

    private fun setupPoseDetector() {
        try {
            val options = PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build()
            poseDetector = PoseDetection.getClient(options)
            Log.d("CameraFragment", "PoseDetector initialized successfully")
        } catch (e: Exception) {
            Log.e("CameraFragment", "Failed to initialize PoseDetector", e)
            Toast.makeText(context, "ML Kit initialization failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun createAnalyzer(): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            when (currentMode) {
                ScanMode.PALM -> analyzePalm(imageProxy)
                ScanMode.FINGER -> analyzeFinger(imageProxy)
            }
        }
    }


    @OptIn(ExperimentalGetImage::class)
    private fun analyzeFinger(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            poseDetector?.process(image)
                ?.addOnSuccessListener { pose ->
                    val fingerType = identifyFingerType(pose)
                    val isDorsal = detectDorsalSideForFinger(pose)

                    if (isDorsal) {
                        showErrorOverlay("Finger dorsal side detected, please show palm side finger which contains finger record or minutiae points")
                    } else {
                        currentFingerBeingScanned = fingerType
                        overlayView.drawFingerOverlayWithGuide(fingerType.getDisplayName())
                        updateStatusText("Detected: ${fingerType.getDisplayName()} Finger")
                    }
                    imageProxy.close()
                }
                ?.addOnFailureListener { e ->
                    Log.e("CameraFragment", "Finger detection failed", e)
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun identifyFingerType(pose: Pose): HandDetectionViewModel.FingerType {
        return viewModel.getNextFingerToScan()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzePalm(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            poseDetector?.process(image)
                ?.addOnSuccessListener { pose ->
                    val handLandmarks = extractHandLandmarks(pose)
                    if (handLandmarks.isNotEmpty()) {
                        val boundingBox = calculateBoundingBox(handLandmarks)
                        val handSide = determineHandSide(pose)
                        val isDorsal = detectDorsalSide(pose)

                        if (isDorsal) {
                            showErrorOverlay("Palm dorsal side detected, minutiae points won't be extracted.")
                        }

                        viewModel.processHandDetectionResult(
                            handSide = handSide,
                            isDorsal = isDorsal,
                            landmarks = handLandmarks,
                            boundingBox = boundingBox
                        )

                        requireActivity().runOnUiThread {
                            overlayView.drawPalmOverlay(handLandmarks, handSide.name)
                            if (!isDorsal && handSide != HandDetectionViewModel.HandSide.UNKNOWN) {
                                Toast.makeText(context, "${handSide.name} Hand Detected!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    imageProxy.close()
                }
                ?.addOnFailureListener { e ->
                    Log.e("CameraFragment", "Pose detection failed", e)
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun extractHandLandmarks(pose: Pose): List<PointF> {
        val landmarks = mutableListOf<PointF>()

        pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)?.let {
            landmarks.add(PointF(it.position.x, it.position.y))
        }
        pose.getPoseLandmark(PoseLandmark.LEFT_THUMB)?.let {
            landmarks.add(PointF(it.position.x, it.position.y))
        }
        pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)?.let {
            landmarks.add(PointF(it.position.x, it.position.y))
        }
        pose.getPoseLandmark(PoseLandmark.LEFT_PINKY)?.let {
            landmarks.add(PointF(it.position.x, it.position.y))
        }
        pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)?.let {
            landmarks.add(PointF(it.position.x, it.position.y))
        }
        pose.getPoseLandmark(PoseLandmark.RIGHT_THUMB)?.let {
            landmarks.add(PointF(it.position.x, it.position.y))
        }
        pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)?.let {
            landmarks.add(PointF(it.position.x, it.position.y))
        }
        pose.getPoseLandmark(PoseLandmark.RIGHT_PINKY)?.let {
            landmarks.add(PointF(it.position.x, it.position.y))
        }

        return landmarks
    }

    private fun calculateBoundingBox(landmarks: List<PointF>): RectF {
        if (landmarks.isEmpty()) return RectF(0f, 0f, 0f, 0f)

        var minX = landmarks[0].x
        var maxX = landmarks[0].x
        var minY = landmarks[0].y
        var maxY = landmarks[0].y

        landmarks.forEach { point ->
            minX = minOf(minX, point.x)
            maxX = maxOf(maxX, point.x)
            minY = minOf(minY, point.y)
            maxY = maxOf(maxY, point.y)
        }

        return RectF(minX, minY, maxX, maxY)
    }

    private fun determineHandSide(pose: Pose): HandDetectionViewModel.HandSide {
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        return when {
            leftWrist != null && leftWrist.inFrameLikelihood > 0.5f ->
                HandDetectionViewModel.HandSide.LEFT
            rightWrist != null && rightWrist.inFrameLikelihood > 0.5f ->
                HandDetectionViewModel.HandSide.RIGHT
            else -> HandDetectionViewModel.HandSide.UNKNOWN
        }
    }

    private fun detectDorsalSide(pose: Pose): Boolean {
        val thumb = pose.getPoseLandmark(PoseLandmark.LEFT_THUMB)
            ?: pose.getPoseLandmark(PoseLandmark.RIGHT_THUMB)
        val index = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)
            ?: pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)

        return (thumb?.inFrameLikelihood ?: 0f) < 0.5f ||
                (index?.inFrameLikelihood ?: 0f) < 0.5f
    }

    private fun detectDorsalSideForFinger(pose: Pose): Boolean {
        val wrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) ?: pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val indexFinger = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX) ?: pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)

        return (wrist?.inFrameLikelihood ?: 0f) < 0.5f || (indexFinger?.inFrameLikelihood ?: 0f) < 0.5f
    }

    private fun capturePalmImage() {
        val imageCapture = this.imageCapture
        if (imageCapture == null) {
            Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
            return
        }

        val handSide = viewModel.getCapturedHandSide()
        if (handSide == HandDetectionViewModel.HandSide.UNKNOWN) {
            Toast.makeText(context, "Please show your palm clearly to detect hand side", Toast.LENGTH_LONG).show()
            return
        }

        val outputFile = createOutputFile(ScanMode.PALM)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    requireActivity().runOnUiThread {
                        val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                        viewModel.setCapturedPalmImage(bitmap)

                        Toast.makeText(context, "Palm captured successfully! Switch to Finger Mode", Toast.LENGTH_LONG).show()

                        currentMode = ScanMode.FINGER
                        toggleModeButton.text = "Switch to Palm Mode"
                        overlayView.switchToFingerMode()
                        updateStatusText("Palm captured! Now scanning fingers...")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    private fun captureFingerImage() {
        val imageCapture = this.imageCapture
        if (imageCapture == null) {
            Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
            return
        }

        if (viewModel.handDetected.value == null) {
            Toast.makeText(context, "Please capture palm first", Toast.LENGTH_LONG).show()
            currentMode = ScanMode.PALM
            toggleModeButton.text = "Switch to Finger Mode"
            overlayView.switchToPalmMode()
            return
        }

        // Show which finger should be scanned next
        val nextFinger = viewModel.getNextFingerToScan()
        if (nextFinger != currentFingerBeingScanned) {
            currentFingerBeingScanned = nextFinger
            updateStatusText("Place ${currentFingerBeingScanned.getDisplayName()} finger in the oval")
            overlayView.drawFingerOverlayWithGuide(currentFingerBeingScanned.getDisplayName())
        }

        val outputFile = createOutputFile(ScanMode.FINGER)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    requireActivity().runOnUiThread {
                        val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)

                        val isBlurred = BlurDetector.detectBlur(bitmap)

                        if (isBlurred) {
                            Toast.makeText(context, "Image is blurry. Please recapture ${currentFingerBeingScanned.getDisplayName()} finger.", Toast.LENGTH_LONG).show()
                            outputFile.delete()
                            return@runOnUiThread
                        }

                        val success = viewModel.processFingerDetection(bitmap, currentFingerBeingScanned)

                        if (success) {
                            saveFingerImageToStorage(bitmap, outputFile)
                            updateProgressIndicator()
                            updateStatusText(viewModel.getFingerDetectionStatus())

                            val scannedCount = viewModel.getScannedFingersCount()
                            Toast.makeText(
                                context,
                                "✅ ${currentFingerBeingScanned.getDisplayName()} finger captured! ($scannedCount/5)",
                                Toast.LENGTH_SHORT
                            ).show()

                            if (viewModel.isCaptureComplete()) {
                                navigateToResultScreen()
                            } else {
                                // Update to next finger
                                currentFingerBeingScanned = viewModel.getNextFingerToScan()
                                updateStatusText("Place ${currentFingerBeingScanned.getDisplayName()} finger in the oval")
                                overlayView.drawFingerOverlayWithGuide(currentFingerBeingScanned.getDisplayName())
                            }
                        } else {
                            outputFile.delete()
                            // Error message already set in ViewModel
                            val errorMsg = viewModel.errorMessage.value ?: "Finger verification failed"
                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }


    private fun saveFingerImageToStorage(bitmap: Bitmap, file: File) {
        try {
            val handSide = viewModel.getCapturedHandSide()
            val fingerType = currentFingerBeingScanned
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            val properFilename = "${handSide.name}_Hand_${fingerType.name}_Finger_$timestamp.jpg"
            val properFile = File(file.parent, properFilename)

            file.renameTo(properFile)

            val metrics = HandDetectionViewModel.ImageMetrics(
                brightnessScore = currentLuminosity.toFloat(),
                blurScore = 100.0,
                focusDistance = 0f
            )
            viewModel.updateImageMetrics(metrics)

        } catch (e: Exception) {
            Log.e("CameraFragment", "Error saving image", e)
        }
    }

    private fun createOutputFile(mode: ScanMode): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val handSide = viewModel.getCapturedHandSide()

        val fingerDataDir = File(requireContext().getExternalFilesDir(null), "FingerData")
        if (!fingerDataDir.exists()) fingerDataDir.mkdirs()

        val filename = when (mode) {
            ScanMode.PALM -> {
                if (handSide != HandDetectionViewModel.HandSide.NONE) {
                    "${handSide.name}_Hand_$timestamp.png"
                } else {
                    "Hand_$timestamp.png"
                }
            }
            ScanMode.FINGER -> {
                val fingerType = currentFingerBeingScanned
                "${handSide.name}_Hand_${fingerType.name}_Finger_$timestamp.jpg"
            }
        }

        return File(fingerDataDir, filename)
    }

    private fun updateProgress() {
        updateProgressIndicator()
    }

    private fun updateProgressIndicator() {
        val scannedFingers = viewModel.getScannedFingersCount()
        val totalFingers = viewModel.getTotalFingersRequired()
        progressIndicator.text = "$scannedFingers/$totalFingers"
    }

    private fun navigateToResultScreen() {
        // Create and navigate to ResultFragment
        val resultFragment = ResultFragment.newInstance(
            handSide = viewModel.getCapturedHandSide().name,
            scannedFingers = viewModel.getScannedFingersCount(),
            sessionId = viewModel.getSessionId()
        )
        // Navigate to result fragment
        (activity as? BaseActivity)?.navigateToFragment(resultFragment, addToBackStack = true)
    }

    private fun observeViewModel() {
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                showErrorOverlay(it)
            }
        }

        viewModel.handDetected.observe(viewLifecycleOwner) { result ->
            result?.let {
                if (!it.isDorsal) {
                    currentMode = ScanMode.FINGER
                    overlayView.switchToFingerMode()
                    Toast.makeText(context, "Palm detected! Now scan fingers.", Toast.LENGTH_SHORT).show()
                    updateProgressIndicator()
                }
            }
        }
    }

    private fun showErrorOverlay(message: String) {
        // Prevent showing the same error message repeatedly within cooldown period
        val currentTime = System.currentTimeMillis()
        if (message == lastErrorMessage && currentTime - lastErrorTime < ERROR_COOLDOWN_MS) {
            return // Skip showing duplicate error
        }

        lastErrorMessage = message
        lastErrorTime = currentTime

        // Existing showErrorOverlay code...
        val errorTextView = TextView(requireContext()).apply {
            text = message
            setTextColor(Color.RED)
            setBackgroundColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(32, 16, 32, 16)
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = 80
        }

        val parentView = view
        if (parentView is FrameLayout) {
            parentView.addView(errorTextView, params)

            Handler(Looper.getMainLooper()).postDelayed({
                parentView.removeView(errorTextView)
            }, 3000)
        } else {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            camera?.cameraControl?.cancelFocusAndMetering()
            camera = null
            imageCapture = null
            poseDetector?.close()
            poseDetector = null
            if (::cameraExecutor.isInitialized && !cameraExecutor.isShutdown) {
                cameraExecutor.shutdownNow()
            }
        } catch (e: Exception) {
            Log.e("CameraFragment", "Error cleaning up camera", e)
        }
    }

    // Helper extension function to convert ByteArray to Bitmap
    fun ByteArray.toBitmap(): Bitmap {
        return BitmapFactory.decodeByteArray(this, 0, this.size)
    }
}