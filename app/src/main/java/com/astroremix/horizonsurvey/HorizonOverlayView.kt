package com.astroremix.horizonsurvey

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Draws the horizon reticle line and center crosshair the user aligns with the real horizon. */
class HorizonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B82F6")
        strokeWidth = 4f
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 3f
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f

        canvas.drawLine(0f, centerY, width.toFloat(), centerY, linePaint)

        val armLength = 22f
        canvas.drawLine(centerX - armLength, centerY, centerX + armLength, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - armLength, centerX, centerY + armLength, crosshairPaint)
    }
}
