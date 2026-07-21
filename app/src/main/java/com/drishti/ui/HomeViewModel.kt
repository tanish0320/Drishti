package com.drishti.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drishti.models.AppMode
import com.drishti.models.AppUiState
import com.drishti.repository.ControllerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.drishti.camera.FrameProvider
import com.drishti.detection.DecisionEngine
import com.drishti.detection.DetectionEngine
import com.drishti.controller.ModeControllerInterface
import com.drishti.controller.AutoModeManager
import com.drishti.haptics.HapticEngine
import com.drishti.ocr.OCRProcessor
import com.drishti.speech.SpeechEngine

import com.drishti.repository.SettingsRepository
import com.drishti.utils.PerformanceMonitor

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val controllerRepository: ControllerRepository,
    val frameProvider: FrameProvider,
    val detectionEngine: DetectionEngine,
    val decisionEngine: DecisionEngine,
    val speechEngine: SpeechEngine,
    val hapticEngine: HapticEngine,
    val ocrProcessor: OCRProcessor,
    val modeController: ModeControllerInterface,
    val autoModeManager: AutoModeManager,
    val settingsRepository: SettingsRepository,
    val performanceMonitor: PerformanceMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            controllerRepository.currentMode.collect { mode ->
                _uiState.value = _uiState.value.copy(currentMode = mode)
            }
        }
    }

    fun setMode(mode: AppMode) {
        viewModelScope.launch {
            controllerRepository.setAppMode(mode)
        }
    }

    fun toggleMode() {
        viewModelScope.launch {
            controllerRepository.toggleMode()
        }
    }

    fun emergencyStop() {
        // TODO: Implement emergency pipeline reset (cancel alerts/vibrations) in Phase 2
    }
}
