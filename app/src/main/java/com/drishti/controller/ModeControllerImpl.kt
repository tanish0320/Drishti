package com.drishti.controller

import android.content.Context
import android.util.Log
import com.drishti.models.AppMode
import com.drishti.models.ModeHistory
import com.drishti.models.SpeechRequest
import com.drishti.speech.SpeechEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModeControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speechEngine: SpeechEngine
) : ModeControllerInterface {

    private val _currentModeState = MutableStateFlow<AppMode>(AppMode.AUTO)
    override val currentModeState: StateFlow<AppMode> = _currentModeState.asStateFlow()

    private val _userSelectedModeState = MutableStateFlow<AppMode>(AppMode.AUTO)
    val userSelectedModeState: StateFlow<AppMode> = _userSelectedModeState.asStateFlow()

    private val _modeReasonState = MutableStateFlow<String>("Defaulting to Auto Mode.")
    val modeReasonState: StateFlow<String> = _modeReasonState.asStateFlow()

    private val _modeHistoryState = MutableStateFlow<List<ModeHistory>>(emptyList())
    val modeHistoryState: StateFlow<List<ModeHistory>> = _modeHistoryState.asStateFlow()

    // Under the hood actual perception mode (default WALK)
    var effectiveMode: AppMode = AppMode.WALK
        private set

    init {
        // Initial setup starts on WALK as effective mode
        _currentModeState.value = AppMode.WALK
    }

    override fun setMode(mode: AppMode) {
        _userSelectedModeState.value = mode
        if (mode == AppMode.WALK || mode == AppMode.READ) {
            setEffectiveMode(mode, "Manual override select: ${mode.name}")
        } else {
            _modeReasonState.value = "Auto mode enabled. Assessing scene..."
        }
    }

    override fun getCurrentMode(): AppMode {
        return _userSelectedModeState.value
    }

    override fun switchMode() {
        val nextMode = when (_userSelectedModeState.value) {
            AppMode.WALK -> AppMode.READ
            AppMode.READ -> AppMode.AUTO
            AppMode.AUTO -> AppMode.WALK
        }
        setMode(nextMode)
    }

    /**
     * Updates the running effective mode and logs the transition history.
     */
    fun setEffectiveMode(targetMode: AppMode, reason: String) {
        val previous = effectiveMode
        if (previous == targetMode && _currentModeState.value == targetMode) return

        effectiveMode = targetMode
        _currentModeState.value = targetMode
        _modeReasonState.value = reason

        // Log history entry
        val history = ModeHistory(
            previousMode = previous,
            currentMode = targetMode,
            reason = reason,
            timestamp = System.currentTimeMillis()
        )
        _modeHistoryState.value = _modeHistoryState.value + history

        // Trigger TTS voice switch alert (suppressed if identical)
        speechEngine.stop()
        val speechText = if (targetMode == AppMode.READ) "Reading mode." else "Navigation mode."
        speechEngine.speak(SpeechRequest(text = speechText))
        Log.d("ModeControllerImpl", "Mode Switch: ${previous.name} -> ${targetMode.name} | Reason: $reason")
    }
}
