package com.astroremix.horizonsurvey.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StellariumHorizonExporterTest {

    private fun sampleProfile(): HorizonProfile {
        val profile = HorizonProfile()
        profile.add(HorizonPoint(0.0, 2.0))
        profile.add(HorizonPoint(90.0, 5.0))
        profile.add(HorizonPoint(180.0, 3.0))
        profile.add(HorizonPoint(270.0, 4.0))
        return profile
    }

    @Test
    fun `horizon txt is a closed az-alt polygon with a comment header`() {
        val txt = StellariumHorizonExporter.buildHorizonTxt(sampleProfile(), "Backyard")
        val lines = txt.lines().filter { it.isNotBlank() }

        assertTrue(lines[0].startsWith("#"))
        val dataLines = lines.filter { !it.startsWith("#") }
        assertEquals(
            listOf("0.00 2.00", "90.00 5.00", "180.00 3.00", "270.00 4.00", "360.00 2.00"),
            dataLines
        )
    }

    @Test
    fun `refuses to export too few points`() {
        val profile = HorizonProfile()
        profile.add(HorizonPoint(0.0, 1.0))
        profile.add(HorizonPoint(90.0, 1.0))
        assertFailsWith<IllegalArgumentException> {
            StellariumHorizonExporter.buildHorizonTxt(profile, "Too Sparse")
        }
    }

    @Test
    fun `landscape ini declares a polygonal landscape pointing at the horizon file`() {
        val ini = StellariumHorizonExporter.buildLandscapeIni(
            landscapeName = "Backyard",
            horizonFileName = "backyard_horizon.txt",
            author = "Ivailo",
            description = "Surveyed with AstroRemix Horizon Surveyor",
        )

        assertTrue(ini.contains("type = polygonal"))
        assertTrue(ini.contains("polygonal_horizon_list = backyard_horizon.txt"))
        assertTrue(ini.contains("polygonal_horizon_list_mode = azDeg_altDeg"))
        assertTrue(ini.contains("author = Ivailo"))
    }

    @Test
    fun `az alt csv matches the same closed polygon`() {
        val csv = StellariumHorizonExporter.buildAzAltCsv(sampleProfile())
        val lines = csv.lines().filter { it.isNotBlank() }
        assertEquals("azimuth_deg,altitude_deg", lines.first())
        assertEquals(6, lines.size) // header + 5 rows
    }
}
