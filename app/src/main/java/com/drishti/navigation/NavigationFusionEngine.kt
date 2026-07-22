package com.drishti.navigation

import android.util.Log
import com.drishti.detection.DecisionEngine
import com.drishti.detection.IndoorSceneReasoner
import com.drishti.detection.SceneAnalysis
import com.drishti.models.NavigationDecision
import com.drishti.models.ThreatLevel
import com.drishti.speech.NavigationSpeechGenerator
import com.drishti.speech.SpeechEngine
import com.drishti.models.SpeechRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationFusionEngine @Inject constructor(
    private val decisionEngine: DecisionEngine,
    private val navigationEngine: NavigationEngine,
    private val routeManager: RouteManager,
    private val speechEngine: SpeechEngine,
    private val speechGenerator: NavigationSpeechGenerator,
    private val indoorSceneReasoner: IndoorSceneReasoner
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var lastSpokenInstruction = ""
    private var lastSpokenDecision: NavigationDecision? = null
    private var wasObstacleAvoided = false
    private var lastDecisionTime = 0L

    companion object {
        private const val DECISION_COOLDOWN_MS = 6000L
    }

    fun startObserving() {
        scope.launch {
            combine(
                decisionEngine.navigationDecisionState,
                navigationEngine.currentInstruction,
                routeManager.activeSession,
                indoorSceneReasoner.sceneAnalysisState
            ) { decision, mapInstruction, session, sceneAnalysis ->
                FlowData(decision, mapInstruction, session != null && session.isActive, sceneAnalysis)
            }.collect { data ->
                fuseAndSpeak(data.decision, data.mapInstruction, data.isNavigating, data.sceneAnalysis)
            }
        }
    }

    private data class FlowData(
        val decision: NavigationDecision,
        val mapInstruction: String,
        val isNavigating: Boolean,
        val sceneAnalysis: SceneAnalysis
    )

    private fun fuseAndSpeak(
        decision: NavigationDecision,
        mapInstruction: String,
        isNavigating: Boolean,
        sceneAnalysis: SceneAnalysis
    ) {
        val currentTime = System.currentTimeMillis()

        // 1. If not actively navigating, fallback to standard obstacle summaries only
        if (!isNavigating) {
            if (shouldSpeakObstacle(decision, currentTime)) {
                val obstacleText = sceneAnalysis.summary
                speechEngine.speakQueueItem(obstacleText, decision.threatLevel == ThreatLevel.CRITICAL)
                lastSpokenDecision = decision
                lastDecisionTime = currentTime
            }
            return
        }

        // 2. Actively Navigating: Priority-based fusion rules
        val isDanger = decision.threatLevel == ThreatLevel.WARNING || decision.threatLevel == ThreatLevel.CRITICAL
        val isCaution = decision.threatLevel == ThreatLevel.CAUTION

        if (isDanger || isCaution) {
            // Safety Priority: pause turn guidance, present contextual avoid instructions
            if (shouldSpeakObstacle(decision, currentTime)) {
                val baseAlert = sceneAnalysis.summary
                
                // Merge target map instruction with avoid command if relevant
                val combinedSpeech = if (mapInstruction.isNotEmpty() && !mapInstruction.startsWith("Tap Voice")) {
                    val cleanedMap = mapInstruction.replace(Regex("(?i)turn"), "turning").trim('.')
                    "$baseAlert Move around it before ${cleanedMap.lowercase()}."
                } else {
                    baseAlert
                }

                Log.d("NavigationFusionEngine", "Merged Safety announcement: $combinedSpeech")
                speechEngine.speakQueueItem(combinedSpeech, decision.threatLevel == ThreatLevel.CRITICAL)
                lastSpokenDecision = decision
                lastDecisionTime = currentTime
                wasObstacleAvoided = true
            }
        } else {
            // Path clear
            if (wasObstacleAvoided) {
                wasObstacleAvoided = false
                Log.d("NavigationFusionEngine", "Obstacle cleared. Continuing path navigation.")
                
                val resumeSpeech = if (mapInstruction.isNotEmpty() && !mapInstruction.startsWith("Tap Voice")) {
                    "Obstacle cleared. Continue. In ten meters, $mapInstruction"
                } else {
                    "Obstacle cleared. Continue."
                }
                speechEngine.speakQueueItem(resumeSpeech, false)
                lastSpokenInstruction = mapInstruction
                lastSpokenDecision = decision
            } else {
                // Standard route progression turns
                if (mapInstruction != lastSpokenInstruction && mapInstruction.isNotEmpty()) {
                    Log.d("NavigationFusionEngine", "Route turn: $mapInstruction")
                    speechEngine.speakQueueItem(mapInstruction, false)
                    lastSpokenInstruction = mapInstruction
                    lastSpokenDecision = decision
                }
            }
        }
    }

    private fun shouldSpeakObstacle(decision: NavigationDecision, currentTime: Long): Boolean {
        val last = lastSpokenDecision ?: return decision.threatLevel != ThreatLevel.SAFE
        
        when {
            decision.threatLevel == ThreatLevel.SAFE && last.threatLevel != ThreatLevel.SAFE -> return true
            decision.trackId != last.trackId && decision.threatLevel != ThreatLevel.SAFE -> return true
            decision.trackId == last.trackId && decision.threatLevel != last.threatLevel -> return true
            decision.trackId == last.trackId && decision.relativeDirection != last.relativeDirection -> return true
            decision.trackId == last.trackId && (currentTime - lastDecisionTime > DECISION_COOLDOWN_MS) && decision.threatLevel != ThreatLevel.SAFE -> return true
        }
        return false
    }
}
