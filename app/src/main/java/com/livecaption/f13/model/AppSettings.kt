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

enum class Language(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिंदी (Hindi)"),
    AUTO("auto", "Auto-Detect"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    PORTUGUESE("pt", "Português"),
    CHINESE_MANDARIN("zh", "中文 (Mandarin)"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    RUSSIAN("ru", "Русский"),
    ITALIAN("it", "Italiano"),
    DUTCH("nl", "Nederlands"),
    TURKISH("tr", "Türkçe"),
    POLISH("pl", "Polski");

    companion object {
        fun fromCode(code: String?): Language {
            return values().find { it.code == code } ?: ENGLISH
        }

        fun getDisplayNames(): List<String> {
            return values().map { it.displayName }
        }
    }
}

data class TranscriptionResult(
    val text: String,
    val isFinal: Boolean,
    val speechFinal: Boolean
)
