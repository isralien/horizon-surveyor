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
     * Altitude for canvas row [yPx], given that row [referenceYPx] is known to
     * be [referenceAltitudeDeg] and each strip's [stripHeightPx] pixels span
     * [verticalFovDeg] of vertical field of view -- the same px-per-degree
     * scale holds across the whole canvas regardless of how individual strips
     * were vertically shifted to register against that one shared reference.
     */
    fun altitudeForYPx(yPx: Double, referenceAltitudeDeg: Double, referenceYPx: Double, stripHeightPx: Int, verticalFovDeg: Double): Double =
        referenceAltitudeDeg - (yPx - referenceYPx) / stripHeightPx * verticalFovDeg

    /** Inverse of [altitudeForYPx]: the canvas row where a given altitude sits. */
    fun yPxForAltitude(altitudeDeg: Double, referenceAltitudeDeg: Double, referenceYPx: Double, stripHeightPx: Int, verticalFovDeg: Double): Double =
        referenceYPx - (altitudeDeg - referenceAltitudeDeg) / verticalFovDeg * stripHeightPx

    /**
     * Extra vertical padding (px, one side) to add above and below a strip's
     * own height so strips can be shifted to register against a shared
     * reference without any content falling off the canvas, covering up to
     * [maxPitchRangeDeg] of deviation from that reference in either direction.
     */
    fun verticalPaddingPx(maxPitchRangeDeg: Double, stripHeightPx: Int, verticalFovDeg: Double): Int =
        (maxPitchRangeDeg / verticalFovDeg * stripHeightPx).roundToInt()
}
