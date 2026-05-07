package com.android.assignment

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val palmPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val fingerPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val landmarkPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val blurPaint = Paint().apply {
        color = Color.parseColor("#80000000")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val handSidePaint = Paint().apply {
        color = Color.YELLOW
        textSize = 28f
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private var palmRect: RectF? = null
    private var fingerRect: RectF? = null
    private var landmarks: List<PointF> = emptyList()
    private var currentMode = "PALM"
    private var handSideText = ""
    private var fingerGuideText = ""

    fun drawPalmOverlay(points: List<PointF>, handSide: String = "") {
        if (points.isEmpty()) return

        landmarks = points
        handSideText = handSide

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }

        val padding = 30f
        palmRect = RectF(
            (minX - padding).coerceAtLeast(0f),
            (minY - padding).coerceAtLeast(0f),
            (maxX + padding).coerceAtMost(width.toFloat()),
            (maxY + padding).coerceAtMost(height.toFloat())
        )
        currentMode = "PALM"
        invalidate()
    }

    fun drawFingerOverlay() {
        drawFingerOverlayWithGuide(null)
    }

    fun drawFingerOverlayWithGuide(fingerName: String?) {
        val centerX = width / 2f
        val centerY = height / 2f
        val ovalWidth = width * 0.35f
        val ovalHeight = height * 0.45f

        fingerRect = RectF(
            centerX - ovalWidth / 2,
            centerY - ovalHeight / 2,
            centerX + ovalWidth / 2,
            centerY + ovalHeight / 2
        )

        fingerGuideText = fingerName ?: "Place Finger Here"
        currentMode = "FINGER"
        invalidate()
    }

    fun switchToFingerMode() {
        drawFingerOverlay()
    }

    fun switchToPalmMode() {
        currentMode = "PALM"
        palmRect = null
        invalidate()
    }

    fun clearOverlay() {
        palmRect = null
        fingerRect = null
        landmarks = emptyList()
        handSideText = ""
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (currentMode) {
            "PALM" -> {
                // Draw semi-transparent background
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blurPaint)

                // Draw palm bounding box
                palmRect?.let {
                    canvas.drawRect(it, palmPaint)
                }

                // Draw landmark points
                landmarks.forEach { point ->
                    canvas.drawCircle(point.x, point.y, 10f, landmarkPaint)
                }

                // Draw hand side text
                if (handSideText.isNotEmpty()) {
                    canvas.drawText(
                        "${handSideText} HAND DETECTED",
                        width / 2f,
                        100f,
                        handSidePaint
                    )
                }

                // Draw guide text
                canvas.drawText(
                    "Place palm inside the box",
                    width / 2f,
                    height - 80f,
                    textPaint
                )
            }
            "FINGER" -> {
                // Draw semi-transparent background
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blurPaint)

                // Draw centered oval for finger placement
                fingerRect?.let {
                    canvas.drawOval(it, fingerPaint)

                    // Draw inner oval for better visibility
                    val innerRect = RectF(
                        it.left + 5,
                        it.top + 5,
                        it.right - 5,
                        it.bottom - 5
                    )
                    val innerPaint = Paint().apply {
                        color = Color.parseColor("#33FFFFFF")
                        style = Paint.Style.FILL
                    }
                    canvas.drawOval(innerRect, innerPaint)

                    // Draw guide text
                    canvas.drawText(
                        fingerGuideText,
                        width / 2f,
                        it.top - 30f,
                        textPaint
                    )

                    canvas.drawText(
                        "Place finger inside oval",
                        width / 2f,
                        it.bottom + 50f,
                        textPaint
                    )
                }
            }
        }
    }
}