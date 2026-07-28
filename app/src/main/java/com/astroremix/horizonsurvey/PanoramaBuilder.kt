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
 */
class PanoramaBuilder(
    private val pixelsPerDegree: Int = 6,
    private val captureIntervalDeg: Double = 3.0,
    private val panoramaHeightPx: Int = 400,
) {
    private val totalWidthPx = 360 * pixelsPerDegree

    private var canvasBitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var startAzimuthDeg = 0.0
    private var lastRawAzimuthDeg = 0.0
    private var lastCaptureTraveledDeg = Double.NEGATIVE_INFINITY
    private val anchors = mutableListOf<PanoramaGeometry.AltitudeAnchor>()

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
        anchors.clear()
        canvasBitmap?.recycle()
        canvasBitmap = Bitmap.createBitmap(totalWidthPx, panoramaHeightPx, Bitmap.Config.ARGB_8888)
        canvas = Canvas(canvasBitmap!!)
    }

    fun reset() {
        canvasBitmap?.recycle()
        canvasBitmap = null
        canvas = null
        anchors.clear()
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
     * bursty pan speed never leaves a gap.
     */
    fun addStrip(sourceBitmap: Bitmap, altitudeDeg: Double) {
        val targetCanvas = canvas ?: return
        val xPx = PanoramaGeometry.stripXPx(traveledDeg, pixelsPerDegree, totalWidthPx)
        val prevX = anchors.lastOrNull()?.xPx ?: 0
        val stripWidth = (xPx - prevX + 1).coerceIn(1, sourceBitmap.width)

        val cropLeft = ((sourceBitmap.width - stripWidth) / 2).coerceIn(0, sourceBitmap.width - stripWidth)
        val rawStrip = Bitmap.createBitmap(sourceBitmap, cropLeft, 0, stripWidth, sourceBitmap.height)
        val scaledStrip = Bitmap.createScaledBitmap(rawStrip, stripWidth, panoramaHeightPx, true)
        targetCanvas.drawBitmap(scaledStrip, prevX.toFloat(), 0f, null)
        rawStrip.recycle()
        scaledStrip.recycle()

        anchors.add(PanoramaGeometry.AltitudeAnchor(xPx, altitudeDeg))
        lastCaptureTraveledDeg = traveledDeg
    }

    /** Azimuth/altitude for a tapped point in the finished panorama, or null before any capture. */
    fun azimuthAltitudeAt(xPx: Int, yPx: Int, verticalFovDeg: Double): Pair<Double, Double>? =
        PanoramaGeometry.azimuthAltitudeAt(
            xPx = xPx,
            yPx = yPx,
            panoramaHeightPx = panoramaHeightPx,
            startAzimuthDeg = startAzimuthDeg,
            pixelsPerDegree = pixelsPerDegree,
            verticalFovDeg = verticalFovDeg,
            anchors = anchors,
        )
}
