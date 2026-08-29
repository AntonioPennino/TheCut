package com.antoniopennino.thecut.customviews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.antoniopennino.thecut.utils.performAppHapticFeedback

data class SpeedPoint(var timeFraction: Float, var speedMultiplier: Float)

class SpeedCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var points = mutableListOf(
        SpeedPoint(0.0f, 1.0f),
        SpeedPoint(0.3f, 3.0f),
        SpeedPoint(0.7f, 0.3f),
        SpeedPoint(1.0f, 1.0f)
    )
        set(value) {
            field = value
            invalidate()
        }

    var onCurveChanged: ((List<SpeedPoint>) -> Unit)? = null

    private val minSpeed = 0.1f
    private val maxSpeed = 5.0f

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D2133")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E3550")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6366F1") // Electric Indigo
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(24f)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#266366F1") // Translucent Indigo Fill
        style = Paint.Style.FILL
        pathEffect = CornerPathEffect(24f)
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#06B6D4") // Cyber Cyan
        style = Paint.Style.FILL
    }

    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F1F5F9") // Soft Slate White
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 26f
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    private val curvePath = Path()
    private val fillPath = Path()
    private var activePointIndex = -1
    private val touchRadius = 60f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 40f
        val chartWidth = width - (padding * 2)
        val chartHeight = height - (padding * 2)

        if (chartWidth <= 0 || chartHeight <= 0) return

        // 1. Draw Grid Lines & Reference Speeds (1.0x, 3.0x, 5.0x)
        val y1x = padding + chartHeight * (1f - (1.0f - minSpeed) / (maxSpeed - minSpeed))
        val y3x = padding + chartHeight * (1f - (3.0f - minSpeed) / (maxSpeed - minSpeed))

        canvas.drawLine(padding, y1x, padding + chartWidth, y1x, baselinePaint)
        canvas.drawText("1.0x", padding + 8f, y1x - 8f, textPaint)

        canvas.drawLine(padding, y3x, padding + chartWidth, y3x, gridPaint)
        canvas.drawText("3.0x", padding + 8f, y3x - 8f, textPaint)

        // 2. Build Smooth Spline / Curve Path
        curvePath.reset()
        fillPath.reset()

        if (points.isNotEmpty()) {
            val sortedPoints = points.sortedBy { it.timeFraction }
            val firstScreenPos = toScreen(sortedPoints.first(), padding, chartWidth, chartHeight)
            curvePath.moveTo(firstScreenPos.x, firstScreenPos.y)
            fillPath.moveTo(firstScreenPos.x, padding + chartHeight)
            fillPath.lineTo(firstScreenPos.x, firstScreenPos.y)

            for (i in 1 until sortedPoints.size) {
                val pt = toScreen(sortedPoints[i], padding, chartWidth, chartHeight)
                curvePath.lineTo(pt.x, pt.y)
                fillPath.lineTo(pt.x, pt.y)
            }

            val lastScreenPos = toScreen(sortedPoints.last(), padding, chartWidth, chartHeight)
            fillPath.lineTo(lastScreenPos.x, padding + chartHeight)
            fillPath.close()

            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(curvePath, curvePaint)

            // 3. Draw Interactive Control Points
            for ((idx, sp) in points.withIndex()) {
                val pos = toScreen(sp, padding, chartWidth, chartHeight)
                val radius = if (idx == activePointIndex) 18f else 13f
                canvas.drawCircle(pos.x, pos.y, radius, pointPaint)
                canvas.drawCircle(pos.x, pos.y, radius, pointStrokePaint)

                // Draw speed tag above point if active
                if (idx == activePointIndex) {
                    val speedLabel = String.format("%.1fx", sp.speedMultiplier)
                    canvas.drawText(speedLabel, pos.x - 20f, pos.y - 24f, textPaint)
                }
            }
        }
    }

    private fun toScreen(sp: SpeedPoint, padding: Float, w: Float, h: Float): PointF {
        val x = padding + sp.timeFraction * w
        val yNorm = (sp.speedMultiplier - minSpeed) / (maxSpeed - minSpeed)
        val y = padding + h * (1f - yNorm.coerceIn(0f, 1f))
        return PointF(x, y)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val padding = 40f
        val chartWidth = width - (padding * 2)
        val chartHeight = height - (padding * 2)

        if (chartWidth <= 0 || chartHeight <= 0) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointIndex = -1
                for ((idx, sp) in points.withIndex()) {
                    val pos = toScreen(sp, padding, chartWidth, chartHeight)
                    val dist = Math.hypot((event.x - pos.x).toDouble(), (event.y - pos.y).toDouble()).toFloat()
                    if (dist <= touchRadius) {
                        activePointIndex = idx
                        performAppHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        invalidate()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointIndex in points.indices) {
                    val pt = points[activePointIndex]
                    // First and last points locked on timeFraction = 0 and 1
                    if (activePointIndex != 0 && activePointIndex != points.lastIndex) {
                        val newTimeFraction = ((event.x - padding) / chartWidth).coerceIn(0.05f, 0.95f)
                        pt.timeFraction = newTimeFraction
                    }

                    val newNormY = 1f - ((event.y - padding) / chartHeight).coerceIn(0f, 1f)
                    val newSpeed = minSpeed + newNormY * (maxSpeed - minSpeed)
                    pt.speedMultiplier = Math.round(newSpeed * 10f) / 10f

                    onCurveChanged?.invoke(points)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activePointIndex != -1) {
                    activePointIndex = -1
                    invalidate()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    fun applyPreset(presetName: String) {
        points = when (presetName.lowercase()) {
            "hero", "flash in" -> mutableListOf(
                SpeedPoint(0.0f, 3.5f),
                SpeedPoint(0.25f, 0.3f),
                SpeedPoint(0.75f, 0.3f),
                SpeedPoint(1.0f, 1.0f)
            )
            "bullet time", "bullet" -> mutableListOf(
                SpeedPoint(0.0f, 1.0f),
                SpeedPoint(0.35f, 1.0f),
                SpeedPoint(0.5f, 0.15f),
                SpeedPoint(0.65f, 1.0f),
                SpeedPoint(1.0f, 2.0f)
            )
            "montage", "jump beat" -> mutableListOf(
                SpeedPoint(0.0f, 1.5f),
                SpeedPoint(0.3f, 0.4f),
                SpeedPoint(0.5f, 2.8f),
                SpeedPoint(0.7f, 0.4f),
                SpeedPoint(1.0f, 1.5f)
            )
            "flash out" -> mutableListOf(
                SpeedPoint(0.0f, 0.4f),
                SpeedPoint(0.6f, 0.4f),
                SpeedPoint(0.85f, 3.2f),
                SpeedPoint(1.0f, 3.2f)
            )
            else -> mutableListOf(
                SpeedPoint(0.0f, 1.0f),
                SpeedPoint(0.33f, 1.0f),
                SpeedPoint(0.66f, 1.0f),
                SpeedPoint(1.0f, 1.0f)
            )
        }
        onCurveChanged?.invoke(points)
        invalidate()
    }
}
