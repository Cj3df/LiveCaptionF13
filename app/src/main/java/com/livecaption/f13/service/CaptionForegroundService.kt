package com.livecaption.f13.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.livecaption.f13.MainActivity
import com.livecaption.f13.R
import com.livecaption.f13.model.AudioSourceType
import com.livecaption.f13.stt.DeepgramLiveSttEngine
import com.livecaption.f13.stt.SpeechRecognitionCallback
import com.livecaption.f13.utils.PreferenceHelper

class CaptionForegroundService : Service(), SpeechRecognitionCallback {

    companion object {
        private const val TAG = "CaptionService"
        const val CHANNEL_ID = "live_caption_f13_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.livecaption.f13.ACTION_START"
        const val ACTION_STOP = "com.livecaption.f13.ACTION_STOP"
        const val ACTION_UPDATE_SETTINGS = "com.livecaption.f13.ACTION_UPDATE_SETTINGS"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        var isServiceRunning = false
            private set
    }

    private lateinit var prefHelper: PreferenceHelper
    private var overlay: FloatingCaptionOverlay? = null
    private var audioCaptureManager: AudioCaptureManager? = null
    private var sttEngine: DeepgramLiveSttEngine? = null
    private var mediaProjection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        prefHelper = PreferenceHelper(this)
        createNotificationChannel()
    }

    private var currentLanguageCode: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                startForegroundServiceWithNotification()
                initializeServices(resultCode, resultData)
            }
            ACTION_UPDATE_SETTINGS -> {
                overlay?.applyAppearance()
                if (isServiceRunning && currentLanguageCode != prefHelper.languageCode) {
                    restartSttEngine()
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val stopIntent = Intent(this, CaptionForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Transcribing audio in real time...")
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_close, "Stop Captions", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isServiceRunning = true
    }

    private fun initializeServices(resultCode: Int, resultData: Intent?) {
        // 1. Initialize Floating Overlay
        overlay = FloatingCaptionOverlay(this) {
            stopSelf()
        }
        overlay?.show()
        overlay?.updateStatus("Connecting to speech engine...")

        // 2. Obtain MediaProjection if internal audio capture is selected
        val sourceType = prefHelper.audioSource
        if (sourceType == AudioSourceType.INTERNAL && resultCode != 0 && resultData != null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, resultData)
        }

        // 3. Initialize STT Engine
        currentLanguageCode = prefHelper.languageCode
        val apiKey = prefHelper.deepgramApiKey
        sttEngine = DeepgramLiveSttEngine(apiKey, currentLanguageCode, this)
        sttEngine?.start()

        // 4. Initialize Audio Capture
        audioCaptureManager = AudioCaptureManager(
            context = this,
            sourceType = sourceType,
            mediaProjection = mediaProjection,
            onAudioChunkReady = { buffer, readBytes ->
                sttEngine?.sendAudioChunk(buffer, readBytes)
            },
            onError = { error ->
                overlay?.updateStatus(error, R.color.accent_red)
            }
        )
        audioCaptureManager?.startCapture()
    }

    private fun restartSttEngine() {
        currentLanguageCode = prefHelper.languageCode
        sttEngine?.stop()
        overlay?.updateStatus("Switching language mode...", R.color.accent_blue)
        val apiKey = prefHelper.deepgramApiKey
        sttEngine = DeepgramLiveSttEngine(apiKey, currentLanguageCode, this)
        sttEngine?.start()
    }

    // --- SpeechRecognitionCallback implementations ---

    override fun onConnected() {
        val srcName = if (prefHelper.audioSource == AudioSourceType.INTERNAL) "Internal Audio" else "Microphone"
        val langName = prefHelper.language.displayName
        overlay?.updateStatus("Live Captions • $langName • $srcName", R.color.accent_green)
    }

    override fun onTranscript(transcript: String, isFinal: Boolean, speechFinal: Boolean, detectedLanguage: String?) {
        overlay?.onNewTranscript(transcript, isFinal, speechFinal, detectedLanguage)
    }

    override fun onError(errorMessage: String) {
        Log.e(TAG, "STT Error: $errorMessage")
        overlay?.updateStatus(errorMessage, R.color.accent_red)
    }

    override fun onDisconnected() {
        overlay?.updateStatus("Speech engine disconnected", R.color.text_secondary)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Captions Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active floating subtitle service notifications"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false

        audioCaptureManager?.stopCapture()
        audioCaptureManager = null

        sttEngine?.stop()
        sttEngine = null

        mediaProjection?.stop()
        mediaProjection = null

        overlay?.hide()
        overlay = null

        Log.d(TAG, "CaptionForegroundService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
