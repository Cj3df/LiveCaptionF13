package com.livecaption.f13.stt

interface SpeechRecognitionCallback {
    fun onConnected()
    fun onTranscript(transcript: String, isFinal: Boolean, speechFinal: Boolean)
    fun onError(errorMessage: String)
    fun onDisconnected()
}
