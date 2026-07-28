package com.astroremix.horizonsurvey

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A ring showing how much of the 360-degree pan is done: a fixed "start" dot
 * at the top, a "you are here" dot that sweeps clockwise as the phone turns,
 * and an arc between them that closes into a full circle when the two dots meet.
 */
class PanoramaProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = Color.parseColor("#40FFFFFF")
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#3B82F6")
    }
    private val startDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val currentDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FACC15")
        style = Paint.Style.FILL
    }

    private val ringRect = RectF()
    private var traveledDeg = 0.0
    var active = false
        private set

    fun setActive(active: Boolean) {
        this.active = active
        traveledDeg = 0.0
        invalidate()
    }

    fun setTraveledDeg(deg: Double) {
        traveledDeg = deg
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return

        val strokeInset = trackPaint.strokeWidth
        val radius = (min(width, height) / 2f) - strokeInset
        val cx = width / 2f
        val cy = height / 2f
        ringRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        canvas.drawOval(ringRect, trackPaint)

        val sweep = traveledDeg.coerceIn(0.0, 360.0).toFloat()
        if (sweep > 0f) {
            canvas.drawArc(ringRect, -90f, sweep, false, progressPaint)
        }

        val dotRadius = strokeInset * 1.1f
        canvas.drawCircle(cx, cy - radius, dotRadius, startDotPaint)

        val currentAngleRad = Math.toRadians((-90.0 + sweep))
        val currentX = cx + radius * cos(currentAngleRad).toFloat()
        val currentY = cy + radius * sin(currentAngleRad).toFloat()
        canvas.drawCircle(currentX, currentY, dotRadius, currentDotPaint)
    }
}
