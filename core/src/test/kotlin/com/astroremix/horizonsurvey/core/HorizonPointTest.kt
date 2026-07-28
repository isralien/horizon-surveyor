package com.astroremix.horizonsurvey.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HorizonPointTest {

    @Test
    fun `normalizes positive azimuth over 360`() {
        assertEquals(10.0, HorizonPoint(370.0, 5.0).normalizedAzimuthDeg, 1e-9)
    }

    @Test
    fun `normalizes negative azimuth`() {
        assertEquals(350.0, HorizonPoint(-10.0, 5.0).normalizedAzimuthDeg, 1e-9)
    }

    @Test
    fun `rejects altitude outside range`() {
        assertFailsWith<IllegalArgumentException> { HorizonPoint(0.0, 91.0) }
        assertFailsWith<IllegalArgumentException> { HorizonPoint(0.0, -91.0) }
    }
}
