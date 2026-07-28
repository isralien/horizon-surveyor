package com.astroremix.horizonsurvey.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PanoramaGeometryTest {

    @Test
    fun `shortest angle delta handles the ordinary case`() {
        assertEquals(10.0, PanoramaGeometry.shortestAngleDelta(50.0, 60.0), 1e-9)
        assertEquals(-10.0, PanoramaGeometry.shortestAngleDelta(60.0, 50.0), 1e-9)
    }

    @Test
    fun `shortest angle delta takes the short way across the 0-360 wrap`() {
        // 359 -> 2 is a 3 degree forward step, not a -357 degree one.
        assertEquals(3.0, PanoramaGeometry.shortestAngleDelta(359.0, 2.0), 1e-9)
        // 2 -> 359 is a -3 degree step backward.
        assertEquals(-3.0, PanoramaGeometry.shortestAngleDelta(2.0, 359.0), 1e-9)
    }

    @Test
    fun `accumulating shortest deltas across many wraps tracks true unwrapped travel`() {
        // Simulate panning steadily forward through the 0/360 boundary in small steps.
        val readings = listOf(358.0, 359.0, 0.0, 1.0, 2.0)
        var traveled = 0.0
        for (i in 1 until readings.size) {
            traveled += PanoramaGeometry.shortestAngleDelta(readings[i - 1], readings[i])
        }
        assertEquals(4.0, traveled, 1e-9) // 358 -> 362 (== 2) is 4 degrees of net travel
    }

    @Test
    fun `normalizeDeg wraps into 0,360`() {
        assertEquals(10.0, PanoramaGeometry.normalizeDeg(370.0), 1e-9)
        assertEquals(350.0, PanoramaGeometry.normalizeDeg(-10.0), 1e-9)
        assertEquals(0.0, PanoramaGeometry.normalizeDeg(360.0), 1e-9)
    }

    @Test
    fun `stripXPx scales and clamps into the canvas width`() {
        assertEquals(60, PanoramaGeometry.stripXPx(traveledDeg = 10.0, pixelsPerDegree = 6, totalWidthPx = 2160))
        assertEquals(0, PanoramaGeometry.stripXPx(traveledDeg = -5.0, pixelsPerDegree = 6, totalWidthPx = 2160))
        assertEquals(2159, PanoramaGeometry.stripXPx(traveledDeg = 400.0, pixelsPerDegree = 6, totalWidthPx = 2160))
    }

    @Test
    fun `azimuthForXPx reads straight off the x position and wraps past 360`() {
        assertEquals(105.0, PanoramaGeometry.azimuthForXPx(xPx = 30, startAzimuthDeg = 100.0, pixelsPerDegree = 6), 1e-9)
        // 60px / 6px-per-degree = 10 degrees past 350, wraps exactly to 0/360
        assertEquals(0.0, PanoramaGeometry.azimuthForXPx(xPx = 60, startAzimuthDeg = 350.0, pixelsPerDegree = 6), 1e-9)
    }

    @Test
    fun `altitudeForYPx is the reference altitude exactly at the reference row`() {
        val altitude = PanoramaGeometry.altitudeForYPx(
            yPx = 200.0, referenceAltitudeDeg = 3.0, referenceYPx = 200.0, stripHeightPx = 400, verticalFovDeg = 50.0,
        )
        assertEquals(3.0, altitude, 1e-9)
    }

    @Test
    fun `altitudeForYPx applies the vertical FOV correction away from the reference row`() {
        val above = PanoramaGeometry.altitudeForYPx(
            yPx = 0.0, referenceAltitudeDeg = 0.0, referenceYPx = 200.0, stripHeightPx = 400, verticalFovDeg = 50.0,
        )
        assertEquals(25.0, above, 1e-9) // above the reference row reads a higher altitude

        val below = PanoramaGeometry.altitudeForYPx(
            yPx = 400.0, referenceAltitudeDeg = 0.0, referenceYPx = 200.0, stripHeightPx = 400, verticalFovDeg = 50.0,
        )
        assertEquals(-25.0, below, 1e-9)
    }

    @Test
    fun `altitudeForYPx works with a reference row off-center, as after vertical registration`() {
        // A strip captured 10 degrees higher than the global reference gets
        // shifted down on the canvas -- its own center row (400) no longer
        // aligns with the canvas's shared reference row (200).
        val altitudeAtItsOwnCenter = PanoramaGeometry.altitudeForYPx(
            yPx = 400.0, referenceAltitudeDeg = 0.0, referenceYPx = 200.0, stripHeightPx = 400, verticalFovDeg = 50.0,
        )
        assertEquals(-25.0, altitudeAtItsOwnCenter, 1e-9)
    }

    @Test
    fun `yPxForAltitude is the exact inverse of altitudeForYPx`() {
        val cases = listOf(0.0 to 0.0, 120.0 to 3.5, 399.9 to -7.0, 5.0 to 40.0)
        for ((yPx, referenceAltitude) in cases) {
            val altitude = PanoramaGeometry.altitudeForYPx(yPx, referenceAltitude, referenceYPx = 300.0, stripHeightPx = 400, verticalFovDeg = 55.0)
            val roundTrippedYPx = PanoramaGeometry.yPxForAltitude(altitude, referenceAltitude, referenceYPx = 300.0, stripHeightPx = 400, verticalFovDeg = 55.0)
            assertEquals(yPx, roundTrippedYPx, 1e-9)
        }
    }

    @Test
    fun `verticalPaddingPx scales pitch range into pixels using the strip's own degrees-per-pixel`() {
        // 25 degrees of padding at 400px representing 50 degrees of FOV = 8px per degree.
        assertEquals(200, PanoramaGeometry.verticalPaddingPx(maxPitchRangeDeg = 25.0, stripHeightPx = 400, verticalFovDeg = 50.0))
    }
}
