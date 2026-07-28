package com.astroremix.horizonsurvey

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Draws the horizon points the user has tapped on the panorama review image.
 * Sized and positioned 1:1 over the panorama ImageView, so marker coordinates
 * are plain bitmap pixel coordinates with no scale conversion needed.
 */
class PanoramaMarkerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FACC15")
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val points = mutableListOf<Pair<Float, Float>>()

    fun setMarkers(markers: List<Pair<Float, Float>>) {
        points.clear()
        points.addAll(markers)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((x, y) in points) {
            canvas.drawCircle(x, y, 9f, dotPaint)
            canvas.drawCircle(x, y, 9f, ringPaint)
        }
    }
}
