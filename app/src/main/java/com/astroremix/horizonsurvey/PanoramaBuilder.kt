package com.astroremix.horizonsurvey

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import com.astroremix.horizonsurvey.core.PanoramaGeometry

/**
 * Builds a panorama as a "pushbroom" scan: while the user pans, thin vertical
 * strips are cropped from the live preview and laid side by side horizontally,
 * each positioned by exactly how far the phone has rotated since capture
 * began. Vertically, each strip is *shifted* by how far the phone's pitch at
 * capture time deviated from [globalReferenceAltitudeDeg] (the pitch when
 * capture started) -- registering every strip against one shared reference so
 * the photo reads as a single coherent panorama instead of a jump-cut between
 * frames, even though the user's hand naturally drifts up/down while tracking
 * an uneven horizon. (See [PanoramaGeometry], which owns this placement/lookup
 * math and is unit-tested independently of this Bitmap/Canvas glue.)
 *
 * Every captured strip also produces a [Marker] at the canvas row matching
 * its own pitch reading -- the same "line the crosshair up with the horizon"
 * technique as a live tap-to-mark flow, just sampled continuously instead of
 * requiring a tap. Because the image is now vertically registered, an
 * unedited line through these markers traces the true horizon shape, not a
 * flat line -- [Marker.yPx] is still mutable so the review screen can drag a
 * marker to correct a misreading without needing to recapture.
 */
class PanoramaBuilder(
    private val pixelsPerDegree: Int = 6,
    // Deliberately fine-grained: each strip's vertical registration shift is
    // an independent block offset with no blending at the seam (see
    // addStrip), so wider strips make any shift between neighbors read as a
    // chunky, stepped "fan" rather than a fine, near-invisible stagger. This
    // alone doesn't bound how *often* a strip fires, though -- panning
    // faster covers more degrees per second, so angle-only throttling can
    // still fire arbitrarily fast. minCaptureIntervalMs below is what
    // actually caps the rate.
    private val captureIntervalDeg: Double = 1.0,
    // Hard floor on time between captures, regardless of pan speed.
    // PreviewView.getBitmap() is a documented-expensive, main-thread call;
    // without this, fast panning can queue it up faster than it can drain,
    // which reads as the whole UI hanging.
    private val minCaptureIntervalMs: Long = 150,
    val panoramaHeightPx: Int = 400,
    private val maxPitchRangeDeg: Double = 25.0,
) {
    class Marker(val xPx: Int, val azimuthDeg: Double) {
        var yPx: Double = 0.0
    }

    private val totalWidthPx = 360 * pixelsPerDegree

    private var canvasBitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var startAzimuthDeg = 0.0
    private var lastRawAzimuthDeg = 0.0
    private var lastCaptureTraveledDeg = Double.NEGATIVE_INFINITY
    private var lastCaptureTimeMs = 0L
    private var lastDrawY: Double? = null
    private val _markers = mutableListOf<Marker>()
    val markers: List<Marker> get() = _markers

    /** The phone's pitch when capture started; every strip is registered against this. */
    var globalReferenceAltitudeDeg: Double = 0.0
        private set

    /** The canvas row that corresponds to [globalReferenceAltitudeDeg]. */
    var canvasReferenceYPx: Double = 0.0
        private set

    /** Net degrees rotated since [begin], unwrapped (can exceed +-360 if the user overshoots). */
    var traveledDeg: Double = 0.0
        private set

    val bitmap: Bitmap? get() = canvasBitmap
    val isLoopClosed: Boolean get() = kotlin.math.abs(traveledDeg) >= 355.0

    fun begin(currentAzimuthDeg: Double, currentAltitudeDeg: Double, verticalFovDeg: Double) {
        startAzimuthDeg = currentAzimuthDeg
        lastRawAzimuthDeg = currentAzimuthDeg
        globalReferenceAltitudeDeg = currentAltitudeDeg
        traveledDeg = 0.0
        lastCaptureTraveledDeg = Double.NEGATIVE_INFINITY
        lastCaptureTimeMs = 0L
        lastDrawY = null
        _markers.clear()

        val paddingPx = PanoramaGeometry.verticalPaddingPx(maxPitchRangeDeg, panoramaHeightPx, verticalFovDeg)
        canvasReferenceYPx = paddingPx + panoramaHeightPx / 2.0
        val canvasHeightPx = panoramaHeightPx + 2 * paddingPx

        canvasBitmap?.recycle()
        canvasBitmap = Bitmap.createBitmap(totalWidthPx, canvasHeightPx, Bitmap.Config.ARGB_8888)
        canvas = Canvas(canvasBitmap!!)
    }

    fun reset() {
        canvasBitmap?.recycle()
        canvasBitmap = null
        canvas = null
        _markers.clear()
    }

    /**
     * Feed a new orientation reading; returns true when enough rotation has
     * accrued *and* enough wall-clock time has passed since the last capture
     * (see [minCaptureIntervalMs]) -- both must hold, so fast panning is
     * throttled by time rather than firing every sensor tick.
     */
    fun onOrientationUpdate(azimuthDeg: Double): Boolean {
        traveledDeg += PanoramaGeometry.shortestAngleDelta(lastRawAzimuthDeg, azimuthDeg)
        lastRawAzimuthDeg = azimuthDeg
        val enoughAngle = traveledDeg - lastCaptureTraveledDeg >= captureIntervalDeg
        val enoughTime = SystemClock.elapsedRealtime() - lastCaptureTimeMs >= minCaptureIntervalMs
        return enoughAngle && enoughTime
    }

    /**
     * Crops a strip from [sourceBitmap]'s horizontal center and paints it onto
     * the panorama, filling all the way back to the previous capture's edge so
     * bursty pan speed never leaves a gap. The strip is shifted vertically so
     * its content lines up against [globalReferenceAltitudeDeg], using
     * [altitudeDeg] (the phone's current pitch) and [verticalFovDeg]. Rather
     * than pasting the whole strip as one rigid block at that offset, it's
     * drawn in narrow sub-columns whose vertical position ramps smoothly from
     * where the previous strip left off to this strip's own offset -- so a
     * jump in the pitch reading between captures shows as a gentle slope
     * instead of a hard step, regardless of how wide the strip itself is
     * (which depends on pan speed and the capture throttle, not something
     * this method controls). A [Marker] is recorded at the resulting row.
     */
    fun addStrip(sourceBitmap: Bitmap, altitudeDeg: Double, verticalFovDeg: Double) {
        val targetCanvas = canvas ?: return
        val canvasBmp = canvasBitmap ?: return
        val xPx = PanoramaGeometry.stripXPx(traveledDeg, pixelsPerDegree, totalWidthPx)
        val prevX = _markers.lastOrNull()?.xPx ?: 0
        val stripWidth = (xPx - prevX + 1).coerceIn(1, sourceBitmap.width)

        val idealMarkerY = PanoramaGeometry.yPxForAltitude(
            altitudeDeg, globalReferenceAltitudeDeg, canvasReferenceYPx, panoramaHeightPx, verticalFovDeg,
        )
        val drawY = (idealMarkerY - panoramaHeightPx / 2.0).coerceIn(0.0, (canvasBmp.height - panoramaHeightPx).toDouble())
        val markerY = drawY + panoramaHeightPx / 2.0

        val cropLeft = ((sourceBitmap.width - stripWidth) / 2).coerceIn(0, sourceBitmap.width - stripWidth)
        val rawStrip = Bitmap.createBitmap(sourceBitmap, cropLeft, 0, stripWidth, sourceBitmap.height)
        val scaledStrip = Bitmap.createScaledBitmap(rawStrip, stripWidth, panoramaHeightPx, true)

        val fromDrawY = lastDrawY ?: drawY
        for (column in PanoramaGeometry.interpolatedSubColumns(stripWidth, SUB_COLUMN_WIDTH_PX, fromDrawY, drawY)) {
            val srcRect = Rect(column.srcXStart, 0, column.srcXEnd, panoramaHeightPx)
            val dstLeft = (prevX + column.srcXStart).toFloat()
            val dstRect = RectF(
                dstLeft, column.drawY.toFloat(),
                dstLeft + (column.srcXEnd - column.srcXStart), (column.drawY + panoramaHeightPx).toFloat(),
            )
            targetCanvas.drawBitmap(scaledStrip, srcRect, dstRect, null)
        }
        rawStrip.recycle()
        scaledStrip.recycle()
        lastDrawY = drawY

        val azimuthDeg = PanoramaGeometry.azimuthForXPx(xPx, startAzimuthDeg, pixelsPerDegree)
        _markers.add(Marker(xPx, azimuthDeg).apply { yPx = markerY })
        lastCaptureTraveledDeg = traveledDeg
        lastCaptureTimeMs = SystemClock.elapsedRealtime()
    }

    private companion object {
        const val SUB_COLUMN_WIDTH_PX = 3
    }
}
