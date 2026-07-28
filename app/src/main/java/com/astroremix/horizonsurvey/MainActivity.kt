package com.astroremix.horizonsurvey

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.astroremix.horizonsurvey.core.HorizonPoint
import com.astroremix.horizonsurvey.core.HorizonProfile
import com.astroremix.horizonsurvey.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var orientationTracker: OrientationTracker

    private val profile = HorizonProfile()
    private var currentAzimuthDeg = 0.0
    private var currentAltitudeDeg = 0.0

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
        updatePointsCount()

        binding.markButton.setOnClickListener { onMarkPoint() }
        binding.undoButton.setOnClickListener { onUndo() }
        binding.resetButton.setOnClickListener { onReset() }
        binding.finishButton.setOnClickListener { onFinish() }

        permissionRequest.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    override fun onResume() {
        super.onResume()
        if (orientationTracker.isAvailable) {
            orientationTracker.start(OrientationTracker.Listener { azimuthDeg, altitudeDeg ->
                currentAzimuthDeg = azimuthDeg
                currentAltitudeDeg = altitudeDeg
                updateReadout()
            })
        } else {
            Toast.makeText(this, R.string.no_orientation_sensor, Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        orientationTracker.stop()
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
        }, ContextCompat.getMainExecutor(this))
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

    private fun updateReadout() {
        binding.readoutText.text = String.format(
            Locale.US,
            "az %05.1f  alt %+05.1f",
            currentAzimuthDeg,
            currentAltitudeDeg,
        )
    }

    private fun updatePointsCount() {
        binding.pointsCountText.text = getString(R.string.points_recorded, profile.size)
    }

    private fun onMarkPoint() {
        if (!hasCameraPermission()) return
        profile.add(HorizonPoint(currentAzimuthDeg, currentAltitudeDeg))
        binding.overlayView.flashMarked()
        updatePointsCount()
    }

    private fun onUndo() {
        if (profile.removeLast() != null) {
            updatePointsCount()
        }
    }

    private fun onReset() {
        if (profile.size == 0) return
        AlertDialog.Builder(this)
            .setTitle(R.string.reset)
            .setMessage(getString(R.string.confirm_discard_points, profile.size))
            .setPositiveButton(R.string.reset) { _, _ ->
                profile.clear()
                updatePointsCount()
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
}
