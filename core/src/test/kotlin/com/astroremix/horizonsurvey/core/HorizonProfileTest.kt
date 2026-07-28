package com.astroremix.horizonsurvey.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HorizonProfileTest {

    @Test
    fun `sorts, normalizes and closes the polygon`() {
        val profile = HorizonProfile()
        profile.add(HorizonPoint(90.0, 3.0))
        profile.add(HorizonPoint(-30.0, 1.0)) // -> 330
        profile.add(HorizonPoint(10.0, 2.0))

        val polygon = profile.sortedClosedPolygon()

        assertEquals(
            listOf(10.0 to 2.0, 90.0 to 3.0, 330.0 to 1.0, 360.0 to 2.0),
            polygon.map { it.azimuthDeg to it.altitudeDeg }
        )
    }

    @Test
    fun `keeps the most recent reading for a duplicate azimuth`() {
        val profile = HorizonProfile()
        profile.add(HorizonPoint(45.0, 1.0))
        profile.add(HorizonPoint(45.0, 9.0))
        profile.add(HorizonPoint(200.0, 4.0))
        profile.add(HorizonPoint(300.0, 5.0))

        val polygon = profile.sortedClosedPolygon()

        assertEquals(9.0, polygon.first { it.azimuthDeg == 45.0 }.altitudeDeg, 1e-9)
        assertEquals(4, polygon.size) // 3 distinct azimuths + closing point
    }

    @Test
    fun `undo removes the last added point`() {
        val profile = HorizonProfile()
        profile.add(HorizonPoint(0.0, 1.0))
        profile.add(HorizonPoint(1.0, 2.0))
        val removed = profile.removeLast()
        assertEquals(2.0, removed?.altitudeDeg)
        assertEquals(1, profile.size)
    }

    @Test
    fun `survey completeness threshold`() {
        val profile = HorizonProfile()
        repeat(7) { profile.add(HorizonPoint(it * 10.0, 1.0)) }
        assertTrue(!profile.isSurveyComplete(minDistinctAzimuths = 8))
        profile.add(HorizonPoint(75.0, 1.0))
        assertTrue(profile.isSurveyComplete(minDistinctAzimuths = 8))
    }
}
