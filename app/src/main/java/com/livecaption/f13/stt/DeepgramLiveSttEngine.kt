package com.livecaption.f13.stt

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

class DeepgramLiveSttEngine(
    private val apiKey: String,
    private val language: String = "auto",
    private val callback: SpeechRecognitionCallback
) {
    companion object {
        private const val TAG = "DeepgramLiveStt"
        private const val BASE_WS_URL = "wss://api.deepgram.com/v1/listen"
        private const val SAMPLE_RATE = 16000
    }

    private var client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private var isRunning = false

    fun start() {
        if (isRunning) return
        val cleanApiKey = apiKey.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        if (cleanApiKey.isBlank()) {
            callback.onError("Deepgram API Key is missing. Please set it in app settings.")
            return
        }

        isRunning = true

        // For auto-detection / multilingual (Hindi + English code-switching), use language=multi with endpointing=100
        val isMultilingual = language.equals("auto", ignoreCase = true) || language.equals("multi", ignoreCase = true)
        val endpointing = if (isMultilingual || language.equals("hi", ignoreCase = true)) "100" else "300"
        val languageParam = if (isMultilingual) "&language=multi" else "&language=$language"

        val url = "$BASE_WS_URL?encoding=linear16&sample_rate=$SAMPLE_RATE&channels=1&model=nova-2&smart_format=true&interim_results=true&endpointing=$endpointing&punctuate=true$languageParam"

        Log.d(TAG, "Connecting to Deepgram with URL: $url (language: $language)")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Token $cleanApiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully to Deepgram Nova-2 (Language: $language)")
                callback.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = gson.fromJson(text, JsonObject::class.java)
                    if (root.has("channel")) {
                        val channel = root.getAsJsonObject("channel")
                        val alternatives = channel.getAsJsonArray("alternatives")
                        if (alternatives != null && alternatives.size() > 0) {
                            val firstAlt = alternatives.get(0).asJsonObject
                            val transcript = firstAlt.get("transcript")?.asString ?: ""
                            val isFinal = root.get("is_final")?.asBoolean ?: false
                            val speechFinal = root.get("speech_final")?.asBoolean ?: false

                            // Extract detected language if provided by Deepgram
                            var detectedLang: String? = null
                            if (firstAlt.has("languages")) {
                                val langsArray = firstAlt.getAsJsonArray("languages")
                                if (langsArray != null && langsArray.size() > 0) {
                                    detectedLang = langsArray.get(0).asString
                                }
                            }
                            if (detectedLang == null && firstAlt.has("detected_language")) {
                                detectedLang = firstAlt.get("detected_language")?.asString
                            }
                            if (detectedLang == null && root.has("detected_language")) {
                                detectedLang = root.get("detected_language")?.asString
                            }

                            if (transcript.isNotBlank()) {
                                callback.onTranscript(transcript, isFinal, speechFinal, detectedLang)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing Deepgram response", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                val msg = when {
                    response?.code == 401 -> "Invalid Deepgram API Key. Please verify in settings."
                    response?.code == 402 -> "Deepgram quota exceeded. Check account credits."
                    else -> t.message ?: "Connection to Speech Engine failed"
                }
                callback.onError(msg)
                isRunning = false
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed")
                callback.onDisconnected()
                isRunning = false
            }
        })
    }

    fun sendAudioChunk(buffer: ByteArray, readBytes: Int) {
        if (!isRunning || webSocket == null) return
        try {
            val byteString = buffer.toByteString(0, readBytes)
            webSocket?.send(byteString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send audio chunk", e)
        }
    }

    fun stop() {
        isRunning = false
        try {
            // Deepgram close stream frame
            webSocket?.send("{\"type\": \"CloseStream\"}")
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WebSocket", e)
        } finally {
            webSocket = null
        }
    }
}
