package com.livecaption.f13.utils

import android.content.Context
import android.content.SharedPreferences
import com.livecaption.f13.model.AudioSourceType
import com.livecaption.f13.model.CaptionFontSize
import com.livecaption.f13.model.Language

class PreferenceHelper(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "live_caption_f13_prefs"
        private const val KEY_API_KEY = "deepgram_api_key"
        private const val KEY_AUDIO_SOURCE = "audio_source"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_OPACITY = "overlay_opacity"
        private const val KEY_LANGUAGE = "transcription_language"
    }

    var deepgramApiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var audioSource: AudioSourceType
        get() {
            val name = prefs.getString(KEY_AUDIO_SOURCE, AudioSourceType.INTERNAL.name)
            return try {
                AudioSourceType.valueOf(name ?: AudioSourceType.INTERNAL.name)
            } catch (e: Exception) {
                AudioSourceType.INTERNAL
            }
        }
        set(value) = prefs.edit().putString(KEY_AUDIO_SOURCE, value.name).apply()

    var fontSize: CaptionFontSize
        get() {
            val name = prefs.getString(KEY_FONT_SIZE, CaptionFontSize.MEDIUM.name)
            return try {
                CaptionFontSize.valueOf(name ?: CaptionFontSize.MEDIUM.name)
            } catch (e: Exception) {
                CaptionFontSize.MEDIUM
            }
        }
        set(value) = prefs.edit().putString(KEY_FONT_SIZE, value.name).apply()

    var overlayOpacity: Float
        get() = prefs.getFloat(KEY_OPACITY, 0.90f)
        set(value) = prefs.edit().putFloat(KEY_OPACITY, value).apply()

    var language: Language
        get() {
            val code = prefs.getString(KEY_LANGUAGE, "auto") ?: "auto"
            return Language.fromCode(code)
        }
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.code).apply()

    var languageCode: String
        get() = prefs.getString(KEY_LANGUAGE, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()
}
