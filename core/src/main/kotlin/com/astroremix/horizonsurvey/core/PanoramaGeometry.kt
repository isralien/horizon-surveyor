package com.astroremix.horizonsurvey.core

import kotlin.math.roundToInt

/**
 * Pure math behind the panorama "pushbroom" scan: how far a strip travels
 * per orientation update, and how a tapped pixel maps back to azimuth/altitude.
 * Kept free of Bitmap/Canvas so it's unit-testable without an Android runtime.
 */
object PanoramaGeometry {

    /** The phone's recorded altitude at one capture checkpoint, keyed by its x position. */
    data class AltitudeAnchor(val xPx: Int, val altitudeDeg: Double)

    /** Signed shortest angular step from [from] to [to], in (-180, 180]. */
    fun shortestAngleDelta(from: Double, to: Double): Double {
        var delta = (to - from) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta <= -180.0) delta += 360.0
        return delta
    }

    fun normalizeDeg(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

    /** x pixel position for a capture made after rotating [traveledDeg] since the scan began. */
    fun stripXPx(traveledDeg: Double, pixelsPerDegree: Int, totalWidthPx: Int): Int =
        (traveledDeg * pixelsPerDegree).roundToInt().coerceIn(0, totalWidthPx - 1)

    /**
     * Azimuth/altitude for a tapped panorama pixel, or null if there's no
     * capture data yet. Azimuth comes directly from the x position (exact,
     * since strips are placed by measured rotation, not estimated). Altitude
     * interpolates the phone's recorded pitch between the two nearest capture
     * anchors, then corrects for how far above/below vertical center the tap
     * landed, using the camera's vertical field of view.
     */
    fun azimuthAltitudeAt(
        xPx: Int,
        yPx: Int,
        panoramaHeightPx: Int,
        startAzimuthDeg: Double,
        pixelsPerDegree: Int,
        verticalFovDeg: Double,
        anchors: List<AltitudeAnchor>,
    ): Pair<Double, Double>? {
        if (anchors.isEmpty() || panoramaHeightPx <= 0) return null

        val azimuthDeg = normalizeDeg(startAzimuthDeg + xPx.toDouble() / pixelsPerDegree)

        val insertion = anchors.binarySearchBy(xPx) { it.xPx }.let { if (it >= 0) it else -(it + 1) }
        val before = anchors.getOrNull((insertion - 1).coerceAtLeast(0)) ?: anchors.first()
        val after = anchors.getOrNull(insertion.coerceAtMost(anchors.size - 1)) ?: anchors.last()
        val t = if (after.xPx == before.xPx) 0.0 else (xPx - before.xPx).toDouble() / (after.xPx - before.xPx)
        val baseAltitudeDeg = before.altitudeDeg + t * (after.altitudeDeg - before.altitudeDeg)

        val verticalOffsetDeg = (yPx - panoramaHeightPx / 2.0) / panoramaHeightPx * verticalFovDeg
        return azimuthDeg to (baseAltitudeDeg - verticalOffsetDeg)
    }
}
