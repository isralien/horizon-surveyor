package com.astroremix.horizonsurvey

import android.graphics.Bitmap
import android.graphics.Canvas
import com.astroremix.horizonsurvey.core.PanoramaGeometry

/**
 * Builds a panorama as a "pushbroom" scan: while the user pans, thin vertical
 * strips are cropped from the live preview and laid side by side, each
 * positioned by exactly how far the phone has rotated since capture began
 * (see [PanoramaGeometry], which owns the placement/lookup math and is
 * unit-tested independently of this Bitmap/Canvas glue).
 *
 * Every captured strip also produces a [Marker]: the phone's pitch at that
 * instant, recorded automatically (the same "line the crosshair up with the
 * horizon" technique as a live tap-to-mark flow, just sampled continuously
 * instead of requiring a tap). [Marker.yPx] starts at the strip's vertical
 * center -- where that reading was taken -- and is mutable so the review
 * screen can drag it to correct misreadings (e.g. an obstacle briefly
 * throwing off the crosshair alignment) without needing to recapture.
 */
class PanoramaBuilder(
    private val pixelsPerDegree: Int = 6,
    private val captureIntervalDeg: Double = 3.0,
    val panoramaHeightPx: Int = 400,
) {
    class Marker(val xPx: Int, val azimuthDeg: Double, val referenceAltitudeDeg: Double) {
        var yPx: Double = 0.0
    }

    private val totalWidthPx = 360 * pixelsPerDegree

    private var canvasBitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var startAzimuthDeg = 0.0
    private var lastRawAzimuthDeg = 0.0
    private var lastCaptureTraveledDeg = Double.NEGATIVE_INFINITY
    private val _markers = mutableListOf<Marker>()
    val markers: List<Marker> get() = _markers

    /** Net degrees rotated since [begin], unwrapped (can exceed +-360 if the user overshoots). */
    var traveledDeg: Double = 0.0
        private set

    val bitmap: Bitmap? get() = canvasBitmap
    val isLoopClosed: Boolean get() = kotlin.math.abs(traveledDeg) >= 355.0

    fun begin(currentAzimuthDeg: Double) {
        startAzimuthDeg = currentAzimuthDeg
        lastRawAzimuthDeg = currentAzimuthDeg
        traveledDeg = 0.0
        lastCaptureTraveledDeg = Double.NEGATIVE_INFINITY
        _markers.clear()
        canvasBitmap?.recycle()
        canvasBitmap = Bitmap.createBitmap(totalWidthPx, panoramaHeightPx, Bitmap.Config.ARGB_8888)
        canvas = Canvas(canvasBitmap!!)
    }

    fun reset() {
        canvasBitmap?.recycle()
        canvasBitmap = null
        canvas = null
        _markers.clear()
    }

    /** Feed a new orientation reading; returns true when enough rotation has accrued to capture another strip. */
    fun onOrientationUpdate(azimuthDeg: Double): Boolean {
        traveledDeg += PanoramaGeometry.shortestAngleDelta(lastRawAzimuthDeg, azimuthDeg)
        lastRawAzimuthDeg = azimuthDeg
        return traveledDeg - lastCaptureTraveledDeg >= captureIntervalDeg
    }

    /**
     * Crops a strip from [sourceBitmap]'s horizontal center and paints it onto
     * the panorama, filling all the way back to the previous capture's edge so
     * bursty pan speed never leaves a gap. Records a [Marker] for this strip
     * using [altitudeDeg] (the phone's current pitch) as its reference.
     */
    fun addStrip(sourceBitmap: Bitmap, altitudeDeg: Double) {
        val targetCanvas = canvas ?: return
        val xPx = PanoramaGeometry.stripXPx(traveledDeg, pixelsPerDegree, totalWidthPx)
        val prevX = _markers.lastOrNull()?.xPx ?: 0
        val stripWidth = (xPx - prevX + 1).coerceIn(1, sourceBitmap.width)

        val cropLeft = ((sourceBitmap.width - stripWidth) / 2).coerceIn(0, sourceBitmap.width - stripWidth)
        val rawStrip = Bitmap.createBitmap(sourceBitmap, cropLeft, 0, stripWidth, sourceBitmap.height)
        val scaledStrip = Bitmap.createScaledBitmap(rawStrip, stripWidth, panoramaHeightPx, true)
        targetCanvas.drawBitmap(scaledStrip, prevX.toFloat(), 0f, null)
        rawStrip.recycle()
        scaledStrip.recycle()

        val azimuthDeg = PanoramaGeometry.azimuthForXPx(xPx, startAzimuthDeg, pixelsPerDegree)
        _markers.add(Marker(xPx, azimuthDeg, altitudeDeg).apply { yPx = panoramaHeightPx / 2.0 })
        lastCaptureTraveledDeg = traveledDeg
    }
}
