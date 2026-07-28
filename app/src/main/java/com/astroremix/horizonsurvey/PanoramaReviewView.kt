package com.astroremix.horizonsurvey

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.astroremix.horizonsurvey.core.PanoramaViewTransform
import com.astroremix.horizonsurvey.core.PanoramaViewTransform.Transform

/**
 * Shows the captured panorama pinch-zoomable/pannable, with a line connecting
 * the recorded horizon markers. Dragging a marker moves it vertically within
 * its own column to correct a bad reading (e.g. the crosshair briefly caught
 * on an obstacle) -- it never changes which azimuth the marker belongs to.
 *
 * All coordinate math (fit scale, pan clamping, view<->content conversion)
 * comes from [PanoramaViewTransform] in `core`, which is unit-tested
 * independently of this Bitmap/Canvas/touch-handling glue.
 */
class PanoramaReviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private var markers: List<PanoramaBuilder.Marker> = emptyList()
    private var transform = Transform(1f, 0f, 0f)
    private var draggingMarker: PanoramaBuilder.Marker? = null
    private var onMarkerDragged: ((PanoramaBuilder.Marker) -> Unit)? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FACC15")
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FACC15")
        style = Paint.Style.FILL
    }
    private val draggedDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B82F6")
        style = Paint.Style.FILL
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val bmp = bitmap ?: return false
                val fitScale = PanoramaViewTransform.fitHeight(
                    bmp.width.toFloat(), bmp.height.toFloat(), width.toFloat(), height.toFloat(),
                ).scale
                transform = PanoramaViewTransform.clamp(
                    transform.copy(scale = transform.scale * detector.scaleFactor),
                    bmp.width.toFloat(), bmp.height.toFloat(), width.toFloat(), height.toFloat(),
                    minScale = fitScale, maxScale = fitScale.coerceAtLeast(1f) * MAX_ZOOM_MULTIPLIER,
                )
                invalidate()
                return true
            }
        },
    )

    fun setPanorama(bitmap: Bitmap, markers: List<PanoramaBuilder.Marker>) {
        this.bitmap = bitmap
        this.markers = markers
        applyFitIfReady()
        invalidate()
    }

    fun setOnMarkerDragged(listener: (PanoramaBuilder.Marker) -> Unit) {
        onMarkerDragged = listener
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyFitIfReady()
    }

    private fun applyFitIfReady() {
        val bmp = bitmap ?: return
        if (width <= 0 || height <= 0) return
        transform = PanoramaViewTransform.fitHeight(bmp.width.toFloat(), bmp.height.toFloat(), width.toFloat(), height.toFloat())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        val t = transform

        canvas.save()
        canvas.translate(t.translateX, t.translateY)
        canvas.scale(t.scale, t.scale)
        canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)

        if (markers.isNotEmpty()) {
            val path = Path()
            markers.forEachIndexed { i, m ->
                if (i == 0) path.moveTo(m.xPx.toFloat(), m.yPx.toFloat()) else path.lineTo(m.xPx.toFloat(), m.yPx.toFloat())
            }
            // Divide by scale so line/dot size reads as constant on screen regardless of zoom.
            linePaint.strokeWidth = LINE_WIDTH_VIEW_PX / t.scale
            canvas.drawPath(path, linePaint)

            val dotRadius = DOT_RADIUS_VIEW_PX / t.scale
            for (m in markers) {
                val paint = if (m === draggingMarker) draggedDotPaint else dotPaint
                canvas.drawCircle(m.xPx.toFloat(), m.yPx.toFloat(), dotRadius, paint)
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                draggingMarker = findMarkerNearView(event.x, event.y)
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger just joined: hand the gesture to the scale
                // detector and drop any in-progress single-finger drag/pan.
                draggingMarker = null
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val marker = draggingMarker
                    val bmp = bitmap
                    if (marker != null && bmp != null) {
                        val deltaContentY = (event.y - lastTouchY) / transform.scale
                        marker.yPx = (marker.yPx + deltaContentY).coerceIn(0.0, bmp.height.toDouble())
                        onMarkerDragged?.invoke(marker)
                    } else {
                        val bmpForClamp = bitmap
                        transform = transform.copy(
                            translateX = transform.translateX + (event.x - lastTouchX),
                            translateY = transform.translateY + (event.y - lastTouchY),
                        )
                        if (bmpForClamp != null) {
                            val fitScale = PanoramaViewTransform.fitHeight(
                                bmpForClamp.width.toFloat(), bmpForClamp.height.toFloat(), width.toFloat(), height.toFloat(),
                            ).scale
                            transform = PanoramaViewTransform.clamp(
                                transform, bmpForClamp.width.toFloat(), bmpForClamp.height.toFloat(),
                                width.toFloat(), height.toFloat(),
                                minScale = fitScale, maxScale = fitScale.coerceAtLeast(1f) * MAX_ZOOM_MULTIPLIER,
                            )
                        }
                    }
                    invalidate()
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingMarker = null
                invalidate()
            }
        }
        return true
    }

    private fun findMarkerNearView(viewX: Float, viewY: Float): PanoramaBuilder.Marker? {
        val t = transform
        var closest: PanoramaBuilder.Marker? = null
        var closestDistSq = Float.MAX_VALUE
        for (m in markers) {
            val mvx = PanoramaViewTransform.contentToViewX(m.xPx.toFloat(), t)
            val mvy = PanoramaViewTransform.contentToViewY(m.yPx.toFloat(), t)
            val dx = mvx - viewX
            val dy = mvy - viewY
            val distSq = dx * dx + dy * dy
            if (distSq < closestDistSq) {
                closestDistSq = distSq
                closest = m
            }
        }
        return closest?.takeIf { closestDistSq <= HIT_RADIUS_VIEW_PX * HIT_RADIUS_VIEW_PX }
    }

    private companion object {
        const val LINE_WIDTH_VIEW_PX = 4f
        const val DOT_RADIUS_VIEW_PX = 10f
        const val HIT_RADIUS_VIEW_PX = 56f
        const val MAX_ZOOM_MULTIPLIER = 6f
    }
}
