package com.drishti.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drishti.models.AppSettings
import com.drishti.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    fun setLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.updateLanguage(language)
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.updateTheme(theme)
        }
    }

    fun setHapticIntensity(intensity: String) {
        viewModelScope.launch {
            settingsRepository.updateHapticIntensity(intensity)
        }
    }
}
