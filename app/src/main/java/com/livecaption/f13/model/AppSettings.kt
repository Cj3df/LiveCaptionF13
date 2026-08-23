package com.livecaption.f13.model

enum class AudioSourceType {
    INTERNAL,
    MICROPHONE
}

enum class CaptionFontSize(val spValue: Float) {
    SMALL(14f),
    MEDIUM(18f),
    LARGE(24f)
}

enum class ServiceState {
    IDLE,
    CONNECTING,
    RUNNING,
    ERROR
}

data class TranscriptionResult(
    val text: String,
    val isFinal: Boolean,
    val speechFinal: Boolean
)
