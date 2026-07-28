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
    fun `azimuthAltitudeAt reads azimuth straight off the x position`() {
        val anchors = listOf(
            PanoramaGeometry.AltitudeAnchor(0, 0.0),
            PanoramaGeometry.AltitudeAnchor(60, 0.0),
        )
        val (azimuth, _) = PanoramaGeometry.azimuthAltitudeAt(
            xPx = 30, yPx = 200, panoramaHeightPx = 400,
            startAzimuthDeg = 100.0, pixelsPerDegree = 6, verticalFovDeg = 50.0, anchors = anchors,
        )!!
        assertEquals(105.0, azimuth, 1e-9) // 30px / 6px-per-degree = 5 degrees past the 100 degree start
    }

    @Test
    fun `azimuthAltitudeAt wraps azimuth past 360`() {
        val anchors = listOf(PanoramaGeometry.AltitudeAnchor(0, 0.0))
        val (azimuth, _) = PanoramaGeometry.azimuthAltitudeAt(
            xPx = 60, yPx = 200, panoramaHeightPx = 400,
            startAzimuthDeg = 350.0, pixelsPerDegree = 6, verticalFovDeg = 50.0, anchors = anchors,
        )!!
        assertEquals(0.0, azimuth, 1e-9) // 60px / 6px-per-degree = 10 degrees past 350, wraps exactly to 0/360
    }

    @Test
    fun `azimuthAltitudeAt interpolates altitude linearly between the two nearest anchors`() {
        val anchors = listOf(
            PanoramaGeometry.AltitudeAnchor(0, 2.0),
            PanoramaGeometry.AltitudeAnchor(100, 6.0),
        )
        val (_, altitude) = PanoramaGeometry.azimuthAltitudeAt(
            // Tap exactly at vertical center (yPx = height/2) so the FOV correction is zero.
            xPx = 25, yPx = 200, panoramaHeightPx = 400,
            startAzimuthDeg = 0.0, pixelsPerDegree = 6, verticalFovDeg = 50.0, anchors = anchors,
        )!!
        assertEquals(3.0, altitude, 1e-9) // 25% of the way from 2.0 to 6.0
    }

    @Test
    fun `azimuthAltitudeAt applies the vertical FOV correction for taps off-center`() {
        val anchors = listOf(PanoramaGeometry.AltitudeAnchor(0, 0.0))

        val (_, above) = PanoramaGeometry.azimuthAltitudeAt(
            xPx = 0, yPx = 0, panoramaHeightPx = 400, // top edge: half the FOV above center
            startAzimuthDeg = 0.0, pixelsPerDegree = 6, verticalFovDeg = 50.0, anchors = anchors,
        )!!
        assertEquals(25.0, above, 1e-9) // tapping above center reads a higher altitude

        val (_, below) = PanoramaGeometry.azimuthAltitudeAt(
            xPx = 0, yPx = 400, panoramaHeightPx = 400, // bottom edge: half the FOV below center
            startAzimuthDeg = 0.0, pixelsPerDegree = 6, verticalFovDeg = 50.0, anchors = anchors,
        )!!
        assertEquals(-25.0, below, 1e-9)
    }

    @Test
    fun `azimuthAltitudeAt returns null with no capture data`() {
        val result = PanoramaGeometry.azimuthAltitudeAt(
            xPx = 0, yPx = 0, panoramaHeightPx = 400,
            startAzimuthDeg = 0.0, pixelsPerDegree = 6, verticalFovDeg = 50.0, anchors = emptyList(),
        )
        assertEquals(null, result)
    }
}
