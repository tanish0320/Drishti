package com.drishti.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.drishti.models.HapticRequest
import com.drishti.models.ThreatLevel
import com.drishti.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val hapticGenerator: NavigationHapticGenerator
) : HapticEngine {

    private val _currentPatternState = MutableStateFlow<String>("SAFE")
    override val currentPatternState: StateFlow<String> = _currentPatternState.asStateFlow()

    private val _currentIntensityState = MutableStateFlow<String>("Medium")
    override val currentIntensityState: StateFlow<String> = _currentIntensityState.asStateFlow()

    private val _lastVibrationTimestampState = MutableStateFlow<Long>(0L)
    override val lastVibrationTimestampState: StateFlow<Long> = _lastVibrationTimestampState.asStateFlow()

    private val _hapticStatusState = MutableStateFlow<String>("INITIALIZING")
    override val hapticStatusState: StateFlow<String> = _hapticStatusState.asStateFlow()

    private var vibrator: Vibrator? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var activeThreatLevel: ThreatLevel = ThreatLevel.SAFE

    init {
        initializeVibrator()
        observeSettings()
    }

    private fun initializeVibrator() {
        try {
            val systemVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator = systemVibrator

            if (systemVibrator == null || !systemVibrator.hasVibrator()) {
                _hapticStatusState.value = "NO_VIBRATOR"
                Log.w("HapticEngineImpl", "No hardware vibrator detected on this device.")
            } else {
                _hapticStatusState.value = "READY"
            }
        } catch (e: Exception) {
            _hapticStatusState.value = "ERROR"
            Log.e("HapticEngineImpl", "Error initializing Android Vibrator service", e)
        }
    }

    private fun observeSettings() {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                _currentIntensityState.value = settings.hapticIntensity
                if (!settings.hapticEnabled) {
                    cancel()
                } else if (activeThreatLevel != ThreatLevel.SAFE) {
                    // Re-apply active threat pattern with new settings/intensity values
                    restartThreatPattern(activeThreatLevel, settings.hapticIntensity)
                }
            }
        }
    }

    override fun triggerHaptic(request: HapticRequest) {
        val settings = settingsRepository.settings.value
        if (!settings.hapticEnabled) return

        val vib = vibrator ?: return
        try {
            val amplitudeVal = when (settings.hapticIntensity.lowercase()) {
                "low" -> 85
                "high" -> 255
                else -> 170
            }
            val duration = request.durationMs

            val effect = if (vib.hasAmplitudeControl()) {
                VibrationEffect.createOneShot(duration, amplitudeVal)
            } else {
                VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vib.vibrate(effect)
            _lastVibrationTimestampState.value = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e("HapticEngineImpl", "Error executing one-shot custom haptic", e)
        }
    }

    override fun setThreatPattern(threatLevel: ThreatLevel) {
        val settings = settingsRepository.settings.value
        if (!settings.hapticEnabled) {
            cancel()
            return
        }

        // Suppression filter: Do not restart identical patterns repeatedly
        if (threatLevel == activeThreatLevel) return

        restartThreatPattern(threatLevel, settings.hapticIntensity)
    }

    private fun restartThreatPattern(threatLevel: ThreatLevel, intensity: String) {
        val vib = vibrator ?: return
        
        try {
            vib.cancel()
            activeThreatLevel = threatLevel
            _currentPatternState.value = threatLevel.name

            if (threatLevel == ThreatLevel.SAFE) {
                _hapticStatusState.value = "READY"
                return
            }

            val pattern = hapticGenerator.generatePattern(threatLevel, intensity)
            val effect = if (vib.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, pattern.repeatIndex)
            } else {
                VibrationEffect.createWaveform(pattern.timings, pattern.repeatIndex)
            }

            vib.vibrate(effect)
            _lastVibrationTimestampState.value = System.currentTimeMillis()
            _hapticStatusState.value = "VIBRATING"
        } catch (e: Exception) {
            _hapticStatusState.value = "ERROR"
            Log.e("HapticEngineImpl", "Error triggering looped threat pattern: ${threatLevel.name}", e)
        }
    }

    override fun cancel() {
        activeThreatLevel = ThreatLevel.SAFE
        _currentPatternState.value = "SAFE"
        vibrator?.cancel()
        if (_hapticStatusState.value == "VIBRATING") {
            _hapticStatusState.value = "READY"
        }
    }
}
