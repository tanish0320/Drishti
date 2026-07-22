package com.drishti.controller

import android.util.Log
import com.drishti.detection.DecisionEngine
import com.drishti.models.AppMode
import com.drishti.models.ThreatLevel
import com.drishti.ocr.OCRProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoModeManager @Inject constructor(
    private val decisionEngine: DecisionEngine,
    private val ocrProcessor: OCRProcessor,
    private val sceneAnalyzer: SceneAnalyzer,
    private val modeController: ModeControllerInterface
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    companion object {
        // Hysteresis config constants
        const val MIN_WALK_DURATION_MS = 2000L
        const val MIN_READ_DURATION_MS = 2000L
        const val TEXT_STABILITY_THRESHOLD_MS = 2000L
        const val TEXT_ABSENCE_THRESHOLD_MS = 1000L
    }

    private var lastModeSwitchTimestamp = 0L
    
    // Stability timers
    private var textFirstSeenTimestamp = 0L
    private var textLastSeenTimestamp = 0L

    /**
     * Starts monitoring decision and OCR results streams to assess active scene state.
     */
    fun startObserving() {
        scope.launch {
            combine(
                decisionEngine.navigationDecisionState,
                ocrProcessor.ocrResultsState
            ) { decision, ocrResult ->
                Pair(decision.threatLevel, ocrResult.blocks.isNotEmpty())
            }.collect { (threatLevel, hasText) ->
                evaluateScene(threatLevel, hasText)
            }
        }
    }

    private fun evaluateScene(threatLevel: ThreatLevel, hasText: Boolean) {
        val currentTime = System.currentTimeMillis()
        val userMode = modeController.getCurrentMode()

        // Track stable text appearance
        if (hasText) {
            if (textFirstSeenTimestamp == 0L) {
                textFirstSeenTimestamp = currentTime
            }
            textLastSeenTimestamp = currentTime
        } else {
            textFirstSeenTimestamp = 0L
        }

        // Only evaluate auto transitions if the user is in AppMode.AUTO
        if (userMode != AppMode.AUTO) return

        val controller = modeController as ModeControllerImpl
        val currentEffectiveMode = controller.effectiveMode
        val recommended = sceneAnalyzer.analyze(threatLevel, hasText)
        val targetMode = recommended.recommendedMode

        if (targetMode == currentEffectiveMode) return

        val timeSinceLastSwitch = currentTime - lastModeSwitchTimestamp
        var shouldSwitch = false
        var switchReason = recommended.reason

        val isDanger = threatLevel == ThreatLevel.WARNING || threatLevel == ThreatLevel.CRITICAL

        if (isDanger) {
            // Rule 1, 4 & 5: Dangerous obstacles immediately trigger WALK mode (cooldown bypass)
            shouldSwitch = true
            switchReason = "Urgent threat override: obstacle ($threatLevel) blocking walking corridor."
        } else {
            // Stability hysteresis gates
            when (targetMode) {
                AppMode.READ -> {
                    // Rule 2: WALK -> READ requires stable text for 2s AND min walk duration
                    val textStableDuration = if (textFirstSeenTimestamp > 0L) currentTime - textFirstSeenTimestamp else 0L
                    val isTextStable = textStableDuration >= TEXT_STABILITY_THRESHOLD_MS
                    val isMinWalkElapsed = timeSinceLastSwitch >= MIN_WALK_DURATION_MS

                    if (isTextStable && isMinWalkElapsed) {
                        shouldSwitch = true
                    }
                }
                AppMode.WALK -> {
                    // Rule 3: READ -> WALK requires text absence for 1s AND min read duration
                    val textAbsentDuration = if (textLastSeenTimestamp > 0L) currentTime - textLastSeenTimestamp else 0L
                    val isTextAbsent = textAbsentDuration >= TEXT_ABSENCE_THRESHOLD_MS
                    val isMinReadElapsed = timeSinceLastSwitch >= MIN_READ_DURATION_MS

                    if (isTextAbsent && isMinReadElapsed) {
                        shouldSwitch = true
                    }
                }
                else -> {}
            }
        }

        if (shouldSwitch) {
            Log.d("DrishtiDebug", "Auto transition: $currentEffectiveMode -> $targetMode | Reason: $switchReason")
            lastModeSwitchTimestamp = currentTime
            controller.setEffectiveMode(targetMode, switchReason)
        }
    }

    fun getStableTextTimerRemaining(): Long {
        if (textFirstSeenTimestamp == 0L) return 0L
        return (TEXT_STABILITY_THRESHOLD_MS - (System.currentTimeMillis() - textFirstSeenTimestamp)).coerceAtLeast(0L)
    }

    fun getThreatPersistenceTimerRemaining(): Long {
        val decision = decisionEngine.navigationDecisionState.value
        val isDanger = decision.threatLevel == ThreatLevel.WARNING || decision.threatLevel == ThreatLevel.CRITICAL
        return if (isDanger) 0L else 1000L
    }

    fun getLastTransitionTimeElapsed(): Long {
        if (lastModeSwitchTimestamp == 0L) return 0L
        return System.currentTimeMillis() - lastModeSwitchTimestamp
    }
}
