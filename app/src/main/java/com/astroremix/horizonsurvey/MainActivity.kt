package com.astroremix.horizonsurvey

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.location.LocationManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.astroremix.horizonsurvey.core.HorizonPoint
import com.astroremix.horizonsurvey.core.HorizonProfile
import com.astroremix.horizonsurvey.core.PanoramaGeometry
import com.astroremix.horizonsurvey.databinding.ActivityMainBinding
import java.util.Locale
import kotlin.math.abs

private enum class SurveyState { IDLE, CAPTURING, REVIEW }

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var orientationTracker: OrientationTracker
    private val panoramaBuilder = PanoramaBuilder()

    private var camera: Camera? = null
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
        binding.finishButton.setOnClickListener { onFinish() }
        binding.panoramaReviewView.setOnMarkerDragged { marker ->
            val altitudeDeg = PanoramaGeometry.altitudeForYPx(
                marker.yPx, marker.referenceAltitudeDeg, panoramaBuilder.panoramaHeightPx, verticalFovDeg,
            )
            binding.reviewReadoutText.text = String.format(
                Locale.US, "az %05.1f  alt %+05.1f", marker.azimuthDeg, altitudeDeg
            )
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
            val boundCamera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            camera = boundCamera
            verticalFovDeg = computeVerticalFovDeg(boundCamera) ?: DEFAULT_VERTICAL_FOV_DEG
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

    /**
     * Locks auto-exposure/white-balance for the duration of a capture so
     * consecutive strips don't visibly band where the camera's 3A drifted
     * between one capture and the next.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun setExposureLocked(locked: Boolean) {
        val cameraControl = camera?.cameraControl ?: return
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, locked)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, locked)
            .build()
        Camera2CameraControl.from(cameraControl).captureRequestOptions = options
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
                setExposureLocked(true)
                binding.progressRingView.setActive(true)
                binding.captureActionButton.setText(R.string.cancel_capture)
                state = SurveyState.CAPTURING
            }
            SurveyState.CAPTURING -> {
                panoramaBuilder.reset()
                setExposureLocked(false)
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
        setExposureLocked(false)
        binding.progressRingView.setActive(false)
        binding.captureActionButton.setText(R.string.start_capture)

        binding.panoramaReviewView.setPanorama(panorama, panoramaBuilder.markers)
        binding.reviewReadoutText.setText(R.string.drag_to_correct)

        binding.captureGroup.visibility = View.GONE
        binding.reviewGroup.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------
    // Review
    // ------------------------------------------------------------------

    private fun onRetake() {
        AlertDialog.Builder(this)
            .setTitle(R.string.retake)
            .setMessage(getString(R.string.confirm_discard_points, panoramaBuilder.markers.size))
            .setPositiveButton(R.string.retake) { _, _ ->
                panoramaBuilder.reset()
                binding.reviewGroup.visibility = View.GONE
                binding.captureGroup.visibility = View.VISIBLE
                state = SurveyState.IDLE
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onFinish() {
        val markers = panoramaBuilder.markers
        if (markers.size < 3) {
            Toast.makeText(this, R.string.no_points_captured, Toast.LENGTH_LONG).show()
            return
        }

        val profile = HorizonProfile()
        for (marker in markers) {
            val altitudeDeg = PanoramaGeometry.altitudeForYPx(
                marker.yPx, marker.referenceAltitudeDeg, panoramaBuilder.panoramaHeightPx, verticalFovDeg,
            )
            profile.add(HorizonPoint(marker.azimuthDeg, altitudeDeg))
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
