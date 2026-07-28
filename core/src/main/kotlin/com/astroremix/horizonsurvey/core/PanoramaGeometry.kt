package com.astroremix.horizonsurvey.core

import kotlin.math.roundToInt

/**
 * Pure math behind the panorama "pushbroom" scan: how far a strip travels
 * per orientation update, where it lands on the canvas, and how a marker's
 * vertical position converts to/from altitude. Kept free of Bitmap/Canvas so
 * it's unit-testable without an Android runtime.
 */
object PanoramaGeometry {

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

    /** Azimuth of a given x position: exact, since strips are placed by measured rotation. */
    fun azimuthForXPx(xPx: Int, startAzimuthDeg: Double, pixelsPerDegree: Int): Double =
        normalizeDeg(startAzimuthDeg + xPx.toDouble() / pixelsPerDegree)

    /**
     * Altitude for a marker sitting at [yPx] within a column whose vertical
     * center corresponds to [referenceAltitudeDeg] (the phone's recorded pitch
     * when that column was captured). Moving the marker away from center
     * shifts altitude by the vertical field of view proportionally.
     */
    fun altitudeForYPx(yPx: Double, referenceAltitudeDeg: Double, panoramaHeightPx: Int, verticalFovDeg: Double): Double =
        referenceAltitudeDeg - (yPx - panoramaHeightPx / 2.0) / panoramaHeightPx * verticalFovDeg

    /** Inverse of [altitudeForYPx]: where a given altitude sits vertically within its column. */
    fun yPxForAltitude(altitudeDeg: Double, referenceAltitudeDeg: Double, panoramaHeightPx: Int, verticalFovDeg: Double): Double =
        panoramaHeightPx / 2.0 - (altitudeDeg - referenceAltitudeDeg) / verticalFovDeg * panoramaHeightPx
}
