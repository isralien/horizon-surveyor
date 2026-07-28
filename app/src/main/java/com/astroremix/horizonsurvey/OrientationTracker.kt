package com.astroremix.horizonsurvey

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location

/**
 * Turns the fused rotation-vector sensor into a live (azimuth, altitude)
 * reading for the direction the back camera is pointed, assuming the classic
 * "hold it up like a photo" grip: phone vertical, portrait, top edge roughly
 * skyward.
 *
 * SensorManager.getOrientation() assumes the device is flat on a table
 * (its Z axis pointing at the sky). Held vertically instead, that assumption
 * is off by a quarter turn, so the axes are remapped first: new X = device X
 * (unchanged), new Y = -device Z (the camera's look direction), which makes
 * device Y (top edge, skyward when level) the new Z. That derivation fixes
 * which physical tilt becomes azimuth vs. altitude; it does not by itself
 * guarantee the sign of the altitude output. If on-device testing shows
 * altitude decreasing as the camera tilts up, flip [INVERT_ALTITUDE].
 */
class OrientationTracker(context: Context) : SensorEventListener {

    fun interface Listener {
        fun onOrientationChanged(azimuthDeg: Double, altitudeDeg: Double)
    }

    private companion object {
        const val INVERT_ALTITUDE = false

        // Exponential moving average factor for the altitude reading: lower
        // = smoother but slower to react. Raw altitude is noisy enough,
        // sample to sample, to visibly stagger the panorama's strip
        // registration (see PanoramaBuilder) if used unsmoothed -- this
        // damps that jitter while still tracking real, slower elevation
        // changes across a horizon within a capture or two.
        const val ALTITUDE_SMOOTHING_FACTOR = 0.15
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var smoothedAltitudeDeg: Double? = null

    /** Correction added to magnetic azimuth to report true-north azimuth. Never persisted. */
    var declinationDeg: Float = 0f
        private set

    private var listener: Listener? = null

    val isAvailable: Boolean get() = rotationVectorSensor != null

    fun setLocationForDeclination(location: Location) {
        declinationDeg = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            System.currentTimeMillis(),
        ).declination
    }

    fun start(listener: Listener) {
        this.listener = listener
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        listener = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_MINUS_Z,
            remappedMatrix,
        )
        SensorManager.getOrientation(remappedMatrix, orientationAngles)

        val magneticAzimuthDeg = Math.toDegrees(orientationAngles[0].toDouble())
        val trueAzimuthDeg = (magneticAzimuthDeg + declinationDeg + 360.0) % 360.0

        var rawAltitudeDeg = Math.toDegrees(orientationAngles[1].toDouble())
        if (INVERT_ALTITUDE) rawAltitudeDeg = -rawAltitudeDeg

        val previous = smoothedAltitudeDeg
        val altitudeDeg = if (previous == null) {
            rawAltitudeDeg
        } else {
            previous + ALTITUDE_SMOOTHING_FACTOR * (rawAltitudeDeg - previous)
        }
        smoothedAltitudeDeg = altitudeDeg

        listener?.onOrientationChanged(trueAzimuthDeg, altitudeDeg)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
}
