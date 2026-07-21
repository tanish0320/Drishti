package com.drishti.speech

import com.drishti.detection.DecisionEngine
import com.drishti.models.NavigationDecision
import com.drishti.models.ThreatLevel
import com.drishti.models.AppMode
import com.drishti.repository.ControllerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechManager @Inject constructor(
    private val decisionEngine: DecisionEngine,
    private val speechGenerator: NavigationSpeechGenerator,
    private val speechEngine: SpeechEngine,
    private val controllerRepository: ControllerRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var lastSpokenDecision: NavigationDecision? = null
    private var lastSpokenTime = 0L

    companion object {
        // Configurable repeat cooldown for continuous alerts on the same object
        private const val REPEAT_ALERT_COOLDOWN_MS = 6000L
    }

    /**
     * Spawns flow collection on navigation decisions.
     */
    fun startObserving() {
        scope.launch {
            decisionEngine.navigationDecisionState.collect { decision ->
                processDecision(decision)
            }
        }
    }

    private fun processDecision(decision: NavigationDecision) {
        val currentMode = controllerRepository.currentMode.value
        val isReadMode = currentMode == AppMode.READ

        // Rule 5: Pause navigation announcements in READ mode unless danger (WARNING or CRITICAL) appears
        val isDanger = decision.threatLevel == ThreatLevel.WARNING || decision.threatLevel == ThreatLevel.CRITICAL
        if (isReadMode && !isDanger) {
            return
        }

        val currentTime = System.currentTimeMillis()
        val last = lastSpokenDecision

        if (last == null) {
            // Emit first warning if it isn't SAFE
            if (decision.threatLevel != ThreatLevel.SAFE) {
                speakDecision(decision, currentTime)
            }
            return
        }

        var shouldSpeak = false

        when {
            // 1. Clear alert on transition from a warning state to SAFE
            decision.threatLevel == ThreatLevel.SAFE && last.threatLevel != ThreatLevel.SAFE -> {
                shouldSpeak = true
            }
            // 2. New track becomes the priority threat
            decision.trackId != last.trackId && decision.threatLevel != ThreatLevel.SAFE -> {
                shouldSpeak = true
            }
            // 3. Threat level escalates or de-escalates on same obstacle
            decision.trackId == last.trackId && decision.threatLevel != last.threatLevel -> {
                shouldSpeak = true
            }
            // 4. Significant direction change on same obstacle
            decision.trackId == last.trackId && decision.relativeDirection != last.relativeDirection -> {
                shouldSpeak = true
            }
            // 5. Periodic repetition cooldown reached
            decision.trackId == last.trackId && (currentTime - lastSpokenTime > REPEAT_ALERT_COOLDOWN_MS) && decision.threatLevel != ThreatLevel.SAFE -> {
                shouldSpeak = true
            }
        }

        if (shouldSpeak) {
            speakDecision(decision, currentTime)
        }
    }

    private fun speakDecision(decision: NavigationDecision, timestamp: Long) {
        val text = speechGenerator.generateSpeech(decision)
        val isCritical = decision.threatLevel == ThreatLevel.CRITICAL
        
        speechEngine.speakQueueItem(text, isCritical)
        lastSpokenDecision = decision
        lastSpokenTime = timestamp
    }
}
