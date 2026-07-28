package com.astroremix.horizonsurvey.core

/**
 * A single horizon measurement: how high (altitude, degrees above the astronomical
 * horizon) an obstruction reaches at a given compass bearing (azimuth, degrees
 * clockwise from true/magnetic north, matching Android's SensorManager.getOrientation
 * convention and Stellarium's azDeg_altDeg polygonal horizon format).
 */
data class HorizonPoint(val azimuthDeg: Double, val altitudeDeg: Double) {
    init {
        require(altitudeDeg in -90.0..90.0) {
            "altitudeDeg must be in -90..90, was $altitudeDeg"
        }
        require(azimuthDeg.isFinite()) { "azimuthDeg must be finite, was $azimuthDeg" }
    }

    /** Azimuth normalized into [0, 360). */
    val normalizedAzimuthDeg: Double
        get() = ((azimuthDeg % 360.0) + 360.0) % 360.0
}
