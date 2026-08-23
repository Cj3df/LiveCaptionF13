package com.livecaption.f13

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.livecaption.f13.databinding.ActivityMainBinding
import com.livecaption.f13.model.AudioSourceType
import com.livecaption.f13.model.CaptionFontSize
import com.livecaption.f13.service.CaptionForegroundService
import com.livecaption.f13.utils.PermissionHelper
import com.livecaption.f13.utils.PreferenceHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefHelper: PreferenceHelper
    private lateinit var mediaProjectionManager: MediaProjectionManager

    // Permission launchers
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkAndRequestNotificationPermission()
        } else {
            Toast.makeText(this, "Microphone permission is required for captions", Toast.LENGTH_LONG).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Continue with service start
        initiateServiceStart()
    }

    // Media projection launcher (for capturing internal audio)
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startCaptionService(result.resultCode, result.data)
        } else {
            Toast.makeText(this, "Internal audio capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefHelper = PreferenceHelper(this)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setupViews()
        loadSavedPreferences()
    }

    override fun onResume() {
        super.onResume()
        updateUiState()
    }

    private fun setupViews() {
        // Toggle service button
        binding.btnToggleService.setOnClickListener {
            if (CaptionForegroundService.isServiceRunning) {
                stopCaptionService()
            } else {
                validateAndStartService()
            }
        }

        // Audio Source Selection
        binding.radioGroupAudioSource.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbInternalAudio -> prefHelper.audioSource = AudioSourceType.INTERNAL
                R.id.rbMicrophone -> prefHelper.audioSource = AudioSourceType.MICROPHONE
            }
        }

        // Save API Key
        binding.btnSaveApiKey.setOnClickListener {
            val key = binding.etApiKey.text.toString().trim()
            prefHelper.deepgramApiKey = key
            Toast.makeText(this, "API Key saved", Toast.LENGTH_SHORT).show()
        }

        // Font Size selection
        binding.toggleGroupFontSize.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val size = when (checkedId) {
                    R.id.btnFontSmall -> CaptionFontSize.SMALL
                    R.id.btnFontLarge -> CaptionFontSize.LARGE
                    else -> CaptionFontSize.MEDIUM
                }
                prefHelper.fontSize = size
                notifySettingsChanged()
            }
        }

        // Opacity Slider
        binding.sliderOpacity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val opacity = value / 100f
                prefHelper.overlayOpacity = opacity
                binding.tvOpacityLabel.text = "Background Opacity: ${value.toInt()}%"
                notifySettingsChanged()
            }
        }
    }

    private fun loadSavedPreferences() {
        // API Key
        binding.etApiKey.setText(prefHelper.deepgramApiKey)

        // Audio source
        if (prefHelper.audioSource == AudioSourceType.INTERNAL) {
            binding.rbInternalAudio.isChecked = true
        } else {
            binding.rbMicrophone.isChecked = true
        }

        // Font Size
        when (prefHelper.fontSize) {
            CaptionFontSize.SMALL -> binding.toggleGroupFontSize.check(R.id.btnFontSmall)
            CaptionFontSize.LARGE -> binding.toggleGroupFontSize.check(R.id.btnFontLarge)
            CaptionFontSize.MEDIUM -> binding.toggleGroupFontSize.check(R.id.btnFontMedium)
        }

        // Opacity
        val currentOpacityPercent = (prefHelper.overlayOpacity * 100).toInt()
        binding.sliderOpacity.value = currentOpacityPercent.toFloat()
        binding.tvOpacityLabel.text = "Background Opacity: $currentOpacityPercent%"
    }

    private fun updateUiState() {
        if (CaptionForegroundService.isServiceRunning) {
            binding.tvStatus.text = getString(R.string.status_running)
            binding.tvStatusSub.text = "Floating live captions are active on your screen"
            binding.statusIndicator.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_green)
            binding.btnToggleService.text = getString(R.string.btn_stop)
            binding.btnToggleService.setIconResource(R.drawable.ic_stop)
            binding.btnToggleService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_red)
        } else {
            binding.tvStatus.text = getString(R.string.status_ready)
            binding.tvStatusSub.text = "Tap start to begin real-time floating captions"
            binding.statusIndicator.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_blue)
            binding.btnToggleService.text = getString(R.string.btn_start)
            binding.btnToggleService.setIconResource(R.drawable.ic_play)
            binding.btnToggleService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_blue)
        }
    }

    private fun validateAndStartService() {
        if (prefHelper.deepgramApiKey.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("API Key Required")
                .setMessage("Please enter your Deepgram API Key to enable real-time speech recognition.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        if (!PermissionHelper.hasOverlayPermission(this)) {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("To display floating subtitles over other apps (YouTube, Reels, etc.), please grant 'Display over other apps' permission.")
                .setPositiveButton("Grant") { _, _ ->
                    PermissionHelper.requestOverlayPermission(this)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        if (!PermissionHelper.hasAudioRecordPermission(this)) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        checkAndRequestNotificationPermission()
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PermissionHelper.hasNotificationPermission(this)) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            initiateServiceStart()
        }
    }

    private fun initiateServiceStart() {
        if (prefHelper.audioSource == AudioSourceType.INTERNAL) {
            // Request Screen / Audio capture intent
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            mediaProjectionLauncher.launch(captureIntent)
        } else {
            startCaptionService(0, null)
        }
    }

    private fun startCaptionService(resultCode: Int, resultData: Intent?) {
        val intent = Intent(this, CaptionForegroundService::class.java).apply {
            action = CaptionForegroundService.ACTION_START
            putExtra(CaptionForegroundService.EXTRA_RESULT_CODE, resultCode)
            if (resultData != null) {
                putExtra(CaptionForegroundService.EXTRA_RESULT_DATA, resultData)
            }
        }
        ContextCompat.startForegroundService(this, intent)
        updateUiState()
    }

    private fun stopCaptionService() {
        val intent = Intent(this, CaptionForegroundService::class.java).apply {
            action = CaptionForegroundService.ACTION_STOP
        }
        startService(intent)
        updateUiState()
    }

    private fun notifySettingsChanged() {
        if (CaptionForegroundService.isServiceRunning) {
            val intent = Intent(this, CaptionForegroundService::class.java).apply {
                action = CaptionForegroundService.ACTION_UPDATE_SETTINGS
            }
            startService(intent)
        }
    }
}
