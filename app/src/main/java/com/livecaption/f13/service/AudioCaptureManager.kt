package com.livecaption.f13.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import com.livecaption.f13.model.AudioSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioCaptureManager(
    private val context: Context,
    private val sourceType: AudioSourceType,
    private val mediaProjection: MediaProjection?,
    private val onAudioChunkReady: (ByteArray, Int) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "AudioCaptureManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var isRecording = false

    @SuppressLint("MissingPermission")
    fun startCapture() {
        if (isRecording) return

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .build()

            audioRecord = if (sourceType == AudioSourceType.INTERNAL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
                // Register callback for Android 14+ compliance
                try {
                    mediaProjection.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            Log.d(TAG, "MediaProjection stopped")
                        }
                    }, null)
                } catch (e: Exception) {
                    Log.w(TAG, "MediaProjection callback registration note: ${e.message}")
                }

                // Internal Media Audio Capture (Android 10+)
                val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                AudioRecord.Builder()
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(playbackConfig)
                    .build()
            } else {
                // Fallback / Direct Microphone Recording
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Failed to initialize AudioRecord for $sourceType")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = coroutineScope.launch {
                val buffer = ByteArray(1024 * 2) // 2KB chunks (~64ms of 16kHz 16-bit mono)
                while (isActive && isRecording) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        onAudioChunkReady(buffer, readBytes)
                    } else if (readBytes < 0) {
                        Log.e(TAG, "AudioRecord read error code: $readBytes")
                    }
                }
            }

            Log.d(TAG, "Audio capture started successfully with source: $sourceType")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting audio capture", e)
            onError("Microphone/Capture permission denied")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting audio capture", e)
            onError("Audio capture initialization error: ${e.message}")
        }
    }

    fun stopCapture() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio capture", e)
        } finally {
            audioRecord = null
        }
        Log.d(TAG, "Audio capture stopped")
    }
}
