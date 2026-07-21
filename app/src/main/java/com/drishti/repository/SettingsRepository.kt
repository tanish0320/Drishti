package com.drishti.repository

import com.drishti.models.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor() {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun updateLanguage(language: String) {
        // TODO: Persist and emit new language configuration in Phase 2
        _settings.value = _settings.value.copy(language = language)
    }

    fun updateTheme(theme: String) {
        // TODO: Persist and emit new theme configuration in Phase 2
        _settings.value = _settings.value.copy(theme = theme)
    }

    fun updateHapticIntensity(intensity: String) {
        // TODO: Persist and emit new haptic intensity configuration in Phase 2
        _settings.value = _settings.value.copy(hapticIntensity = intensity)
    }

    fun updateHapticEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(hapticEnabled = enabled)
    }

    fun updateOcrEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(ocrEnabled = enabled)
    }

    fun updateOcrMinTextSize(size: Float) {
        _settings.value = _settings.value.copy(ocrMinTextSize = size)
    }
}
