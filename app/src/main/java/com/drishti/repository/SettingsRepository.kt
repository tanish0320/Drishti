package com.drishti.repository

import android.content.Context
import com.drishti.models.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("drishti_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            language = prefs.getString("language", "English") ?: "English",
            theme = prefs.getString("theme", "Light") ?: "Light",
            hapticIntensity = prefs.getString("haptic_intensity", "Medium") ?: "Medium",
            speechEnabled = prefs.getBoolean("speech_enabled", true),
            speechRate = prefs.getFloat("speech_rate", 1.0f),
            pitch = prefs.getFloat("pitch", 1.0f),
            hapticEnabled = prefs.getBoolean("haptic_enabled", true),
            ocrEnabled = prefs.getBoolean("ocr_enabled", true),
            ocrMinTextSize = prefs.getFloat("ocr_min_text_size", 12.0f),
            overlayVisible = prefs.getBoolean("overlay_visible", true),
            debugMode = prefs.getBoolean("debug_mode", true)
        )
    }

    private fun saveSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        prefs.edit().apply {
            putString("language", newSettings.language)
            putString("theme", newSettings.theme)
            putString("haptic_intensity", newSettings.hapticIntensity)
            putBoolean("speech_enabled", newSettings.speechEnabled)
            putFloat("speech_rate", newSettings.speechRate)
            putFloat("pitch", newSettings.pitch)
            putBoolean("haptic_enabled", newSettings.hapticEnabled)
            putBoolean("ocr_enabled", newSettings.ocrEnabled)
            putFloat("ocr_min_text_size", newSettings.ocrMinTextSize)
            putBoolean("overlay_visible", newSettings.overlayVisible)
            putBoolean("debug_mode", newSettings.debugMode)
            apply()
        }
    }

    fun updateLanguage(language: String) {
        saveSettings(_settings.value.copy(language = language))
    }

    fun updateTheme(theme: String) {
        saveSettings(_settings.value.copy(theme = theme))
    }

    fun updateHapticIntensity(intensity: String) {
        saveSettings(_settings.value.copy(hapticIntensity = intensity))
    }

    fun updateHapticEnabled(enabled: Boolean) {
        saveSettings(_settings.value.copy(hapticEnabled = enabled))
    }

    fun updateOcrEnabled(enabled: Boolean) {
        saveSettings(_settings.value.copy(ocrEnabled = enabled))
    }

    fun updateOcrMinTextSize(size: Float) {
        saveSettings(_settings.value.copy(ocrMinTextSize = size))
    }

    fun updateSpeechEnabled(enabled: Boolean) {
        saveSettings(_settings.value.copy(speechEnabled = enabled))
    }

    fun updateSpeechRate(rate: Float) {
        saveSettings(_settings.value.copy(speechRate = rate))
    }

    fun updatePitch(pitch: Float) {
        saveSettings(_settings.value.copy(pitch = pitch))
    }

    fun updateOverlayVisible(visible: Boolean) {
        saveSettings(_settings.value.copy(overlayVisible = visible))
    }

    fun updateDebugMode(enabled: Boolean) {
        saveSettings(_settings.value.copy(debugMode = enabled))
        com.drishti.utils.Logger.setDebugEnabled(enabled)
    }
}
