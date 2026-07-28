package com.astroremix.horizonsurvey

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.location.LocationManager
import android.os.Bundle
import android.text.InputType
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.astroremix.horizonsurvey.core.HorizonPoint
import com.astroremix.horizonsurvey.core.HorizonProfile
import com.astroremix.horizonsurvey.databinding.ActivityMainBinding
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class SurveyState { IDLE, CAPTURING, REVIEW }

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var orientationTracker: OrientationTracker
    private val panoramaBuilder = PanoramaBuilder()
    private val profile = HorizonProfile()
    private val markers = mutableListOf<Pair<Float, Float>>()

    private var state = SurveyState.IDLE
    private var currentAzimuthDeg = 0.0
    private var verticalFovDeg = DEFAULT_VERTICAL_FOV_DEG

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.CAMERA] == true) {
                startCamera()
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
            }
            if (granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                applyDeclinationFromLastKnownLocation()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orientationTracker = OrientationTracker(this)

        binding.captureActionButton.setOnClickListener { onCaptureActionClicked() }
        binding.retakeButton.setOnClickListener { onRetake() }
        binding.undoMarkButton.setOnClickListener { onUndoMark() }
        binding.finishButton.setOnClickListener { onFinish() }
        binding.reviewImageFrame.setOnTouchListener { _, event ->
            reviewTapDetector.onTouchEvent(event)
            false // let the HorizontalScrollView still handle drag/fling
        }

        permissionRequest.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    override fun onResume() {
        super.onResume()
        if (orientationTracker.isAvailable) {
            orientationTracker.start(OrientationTracker.Listener { azimuthDeg, altitudeDeg ->
                onOrientationChanged(azimuthDeg, altitudeDeg)
            })
        } else {
            Toast.makeText(this, R.string.no_orientation_sensor, Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        orientationTracker.stop()
    }

    // ------------------------------------------------------------------
    // Camera
    // ------------------------------------------------------------------

    @OptIn(ExperimentalCamera2Interop::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            verticalFovDeg = computeVerticalFovDeg(camera) ?: DEFAULT_VERTICAL_FOV_DEG
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun computeVerticalFovDeg(camera: Camera): Double? {
        val characteristics = Camera2CameraInfo.from(camera.cameraInfo)
        val sensorSize = characteristics.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            ?: return null
        val focalLengthMm = characteristics
            .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()
            ?: return null
        if (focalLengthMm <= 0f) return null
        // Approximation: assumes the preview crop is proportional to the full sensor
        // and a rectilinear (non-fisheye) lens -- close enough for backyard use, not
        // survey-grade precision. See README for the on-device sanity check.
        return Math.toDegrees(2.0 * kotlin.math.atan((sensorSize.height / 2.0) / focalLengthMm.toDouble()))
    }

    private fun applyDeclinationFromLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Only ACCESS_COARSE_LOCATION is requested, so GPS_PROVIDER isn't accessible;
        // NETWORK_PROVIDER's coarse fix is plenty precise for declination correction.
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val location = runCatching {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()
        if (location != null) {
            orientationTracker.setLocationForDeclination(location)
        }
    }

    // ------------------------------------------------------------------
    // Orientation / capture
    // ------------------------------------------------------------------

    private fun onOrientationChanged(azimuthDeg: Double, altitudeDeg: Double) {
        currentAzimuthDeg = azimuthDeg
        binding.readoutText.text = String.format(
            Locale.US, "az %05.1f  alt %+05.1f", azimuthDeg, altitudeDeg
        )

        if (state != SurveyState.CAPTURING) return

        val dueForCapture = panoramaBuilder.onOrientationUpdate(azimuthDeg)
        binding.progressRingView.setTraveledDeg(panoramaBuilder.traveledDeg)
        val remainingDeg = (360.0 - abs(panoramaBuilder.traveledDeg)).coerceAtLeast(0.0)
        binding.remainingText.text = String.format(Locale.US, "%.0f° remaining", remainingDeg)

        if (dueForCapture) {
            binding.previewView.bitmap?.let { panoramaBuilder.addStrip(it, altitudeDeg) }
        }
        if (panoramaBuilder.isLoopClosed) {
            finalizeCapture()
        }
    }

    private fun onCaptureActionClicked() {
        when (state) {
            SurveyState.IDLE -> {
                panoramaBuilder.begin(currentAzimuthDeg)
                binding.progressRingView.setActive(true)
                binding.captureActionButton.setText(R.string.cancel_capture)
                state = SurveyState.CAPTURING
            }
            SurveyState.CAPTURING -> {
                panoramaBuilder.reset()
                binding.progressRingView.setActive(false)
                binding.captureActionButton.setText(R.string.start_capture)
                state = SurveyState.IDLE
            }
            SurveyState.REVIEW -> Unit
        }
    }

    private fun finalizeCapture() {
        val panorama = panoramaBuilder.bitmap ?: return
        state = SurveyState.REVIEW
        binding.progressRingView.setActive(false)
        binding.captureActionButton.setText(R.string.start_capture)

        binding.panoramaImageView.setImageBitmap(panorama)
        binding.markerOverlayView.layoutParams = binding.markerOverlayView.layoutParams.apply {
            width = panorama.width
            height = panorama.height
        }
        binding.markerOverlayView.requestLayout()

        profile.clear()
        markers.clear()
        binding.markerOverlayView.setMarkers(markers)
        updateReviewPointsCount()

        binding.captureGroup.visibility = View.GONE
        binding.reviewGroup.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------
    // Review: tap-to-mark
    // ------------------------------------------------------------------

    private val reviewTapDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onPanoramaTapped(e.x, e.y)
                return true
            }
        })
    }

    private fun onPanoramaTapped(viewX: Float, viewY: Float) {
        val panorama = panoramaBuilder.bitmap ?: return
        val xPx = viewX.roundToInt().coerceIn(0, panorama.width - 1)
        val yPx = viewY.roundToInt().coerceIn(0, panorama.height - 1)
        val (azimuthDeg, altitudeDeg) = panoramaBuilder.azimuthAltitudeAt(xPx, yPx, verticalFovDeg) ?: return

        profile.add(HorizonPoint(azimuthDeg, altitudeDeg))
        markers.add(xPx.toFloat() to yPx.toFloat())
        binding.markerOverlayView.setMarkers(markers)

        binding.reviewReadoutText.text = String.format(
            Locale.US, "az %05.1f  alt %+05.1f", azimuthDeg, altitudeDeg
        )
        updateReviewPointsCount()
    }

    private fun onUndoMark() {
        if (profile.removeLast() != null) {
            if (markers.isNotEmpty()) markers.removeAt(markers.size - 1)
            binding.markerOverlayView.setMarkers(markers)
            updateReviewPointsCount()
        }
    }

    private fun updateReviewPointsCount() {
        binding.reviewPointsCountText.text = getString(R.string.points_recorded, profile.size)
    }

    private fun onRetake() {
        AlertDialog.Builder(this)
            .setTitle(R.string.retake)
            .setMessage(getString(R.string.confirm_discard_points, profile.size))
            .setPositiveButton(R.string.retake) { _, _ ->
                panoramaBuilder.reset()
                profile.clear()
                markers.clear()
                binding.reviewGroup.visibility = View.GONE
                binding.captureGroup.visibility = View.VISIBLE
                state = SurveyState.IDLE
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onFinish() {
        if (!profile.isSurveyComplete()) {
            Toast.makeText(this, R.string.not_enough_points, Toast.LENGTH_LONG).show()
            return
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.survey_name_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.finish_survey)
            .setView(input)
            .setPositiveButton(R.string.export_share_title) { _, _ ->
                val name = input.text.toString().ifBlank { "Horizon" }
                val intent = ExportManager.export(this, profile, name)
                startActivity(Intent.createChooser(intent, getString(R.string.export_share_title)))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object {
        const val DEFAULT_VERTICAL_FOV_DEG = 55.0
    }
}
