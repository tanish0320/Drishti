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

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateHapticEnabled(enabled)
        }
    }

    fun setOcrEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateOcrEnabled(enabled)
        }
    }

    fun setOcrMinTextSize(size: Float) {
        viewModelScope.launch {
            settingsRepository.updateOcrMinTextSize(size)
        }
    }

    fun setSpeechEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSpeechEnabled(enabled)
        }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch {
            settingsRepository.updateSpeechRate(rate)
        }
    }

    fun setPitch(pitch: Float) {
        viewModelScope.launch {
            settingsRepository.updatePitch(pitch)
        }
    }

    fun setOverlayVisible(visible: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateOverlayVisible(visible)
        }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateDebugMode(enabled)
        }
    }

    fun setGoogleMapsNav(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateGoogleMapsNav(enabled)
        }
    }

    fun setVoiceNav(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateVoiceNav(enabled)
        }
    }

    fun setRouteRecalculation(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateRouteRecalculation(enabled)
        }
    }

    fun setAvoidBusyRoads(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAvoidBusyRoads(enabled)
        }
    }

    fun setAvoidStairs(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAvoidStairs(enabled)
        }
    }

    fun setNavVoiceVolume(volume: Float) {
        viewModelScope.launch {
            settingsRepository.updateNavVoiceVolume(volume)
        }
    }
}
