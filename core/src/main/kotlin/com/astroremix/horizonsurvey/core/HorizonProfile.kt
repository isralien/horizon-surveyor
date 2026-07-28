package com.astroremix.horizonsurvey.core

/**
 * An in-progress or completed horizon survey: an unordered bag of [HorizonPoint]
 * measurements taken while panning the phone around a full turn.
 */
class HorizonProfile {

    private val points = mutableListOf<HorizonPoint>()

    val size: Int get() = points.size

    fun add(point: HorizonPoint) {
        points.add(point)
    }

    fun removeLast(): HorizonPoint? = if (points.isNotEmpty()) points.removeAt(points.size - 1) else null

    fun clear() = points.clear()

    fun toList(): List<HorizonPoint> = points.toList()

    /**
     * Normalizes azimuths into [0, 360), sorts ascending, collapses duplicate
     * azimuths (keeping the most recently recorded reading), and closes the
     * polygon by mirroring the lowest-azimuth sample at exactly 360 deg.
     * Every returned point's azimuthDeg is display-ready: real samples land
     * strictly in [0, 360), and only the synthetic closing point is 360.0,
     * so callers can read azimuthDeg directly without renormalizing.
     */
    fun sortedClosedPolygon(): List<HorizonPoint> {
        val byAzimuth = LinkedHashMap<Double, HorizonPoint>()
        for (p in points) {
            byAzimuth[p.normalizedAzimuthDeg] = HorizonPoint(p.normalizedAzimuthDeg, p.altitudeDeg)
        }
        val sorted = byAzimuth.values.sortedBy { it.azimuthDeg }
        if (sorted.isEmpty()) return emptyList()

        val closed = sorted.toMutableList()
        closed.add(HorizonPoint(360.0, sorted.first().altitudeDeg))
        return closed
    }

    /** True once there is enough data to export a usable horizon polygon. */
    fun isSurveyComplete(minDistinctAzimuths: Int = 8): Boolean =
        points.map { it.normalizedAzimuthDeg }.distinct().size >= minDistinctAzimuths
}
