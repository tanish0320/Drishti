package com.drishti.detection

import com.drishti.models.NavigationDecision
import com.drishti.models.PrioritizedDetection
import com.drishti.models.ThreatLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DecisionEngine @Inject constructor(
    private val threatClassifier: ThreatClassifier
) {

    companion object {
        // Cooldown period (in milliseconds) for de-escalations to prevent threat score flickering
        private const val DE_ESCALATION_COOLDOWN_MS = 1500L

        // Configurable weights influencing obstacle class priority ranking
        private val CLASS_WEIGHTS = mapOf(
            "person" to 1.5f,
            "car" to 1.4f,
            "bus" to 1.4f,
            "truck" to 1.4f,
            "motorcycle" to 1.3f,
            "bicycle" to 1.2f,
            "traffic cone" to 1.1f,
            "bench" to 1.0f,
            "chair" to 1.0f
        )
    }

    private val defaultSafeDecision = NavigationDecision(
        trackId = -1,
        className = "None",
        threatLevel = ThreatLevel.SAFE,
        threatScore = 0f,
        distanceScore = 0f,
        relativeDirection = "center",
        reason = "No obstacles in walking corridor.",
        timestamp = System.currentTimeMillis()
    )

    private val _navigationDecisionState = MutableStateFlow<NavigationDecision>(defaultSafeDecision)
    val navigationDecisionState: StateFlow<NavigationDecision> = _navigationDecisionState.asStateFlow()

    private var lastEmittedDecision: NavigationDecision = defaultSafeDecision
    private var lastDecisionChangeTimestamp = 0L

    var currentCooldownRemainingMs = 0L
        private set

    /**
     * Evaluates prioritized tracked objects and selects the single most critical obstacle 
     * based on threat score and object class weights. Applies hysteresis and repeated decision suppression.
     */
    @Synchronized
    fun evaluate(prioritized: List<PrioritizedDetection>): NavigationDecision {
        val currentTime = System.currentTimeMillis()

        // 1. If empty, fallback to SAFE state
        if (prioritized.isEmpty()) {
            val candidate = defaultSafeDecision.copy(timestamp = currentTime)
            applyDecisionWithHysteresis(candidate, currentTime)
            return _navigationDecisionState.value
        }

        // 2. Select the highest priority object using class weights
        val candidateObject = prioritized.maxByOrNull { det ->
            val weight = CLASS_WEIGHTS.getOrDefault(det.className.lowercase(), 1.0f)
            det.threatScore * weight
        }

        if (candidateObject == null) {
            val candidate = defaultSafeDecision.copy(timestamp = currentTime)
            applyDecisionWithHysteresis(candidate, currentTime)
            return _navigationDecisionState.value
        }

        // 3. Classify continuous threat score into discrete ThreatLevel
        val level = threatClassifier.classify(candidateObject.threatScore)

        // 4. Create decision details
        val reason = when (level) {
            ThreatLevel.CRITICAL -> "Urgent: approaching ${candidateObject.className} detected directly ahead."
            ThreatLevel.WARNING -> "Warning: ${candidateObject.className} blocking the walking corridor."
            ThreatLevel.CAUTION -> "Caution: ${candidateObject.className} present in the path."
            ThreatLevel.SAFE -> "Walking corridor is clear."
        }

        val cx = candidateObject.boundingBox.centerX()
        val direction = when {
            cx < 240f -> "left"
            cx > 400f -> "right"
            else -> "center"
        }

        val candidateDecision = NavigationDecision(
            trackId = candidateObject.trackId,
            className = candidateObject.className,
            threatLevel = level,
            threatScore = candidateObject.threatScore,
            distanceScore = candidateObject.distance,
            relativeDirection = direction,
            reason = reason,
            timestamp = currentTime
        )

        // 5. Apply hysteresis and redundant warning suppression
        applyDecisionWithHysteresis(candidateDecision, currentTime)
        return _navigationDecisionState.value
    }

    private fun applyDecisionWithHysteresis(
        candidate: NavigationDecision,
        currentTime: Long
    ) {
        val lastLevelOrdinal = lastEmittedDecision.threatLevel.ordinal
        val candidateLevelOrdinal = candidate.threatLevel.ordinal

        val isEscalation = candidateLevelOrdinal > lastLevelOrdinal
        val timeSinceLastChange = currentTime - lastDecisionChangeTimestamp

        val deEscalationTimeRemaining = (DE_ESCALATION_COOLDOWN_MS - timeSinceLastChange).coerceAtLeast(0L)
        currentCooldownRemainingMs = if (isEscalation) 0L else deEscalationTimeRemaining

        // Check stability timer for de-escalations (escalations trigger immediately for safety)
        if (!isEscalation && deEscalationTimeRemaining > 0L) {
            if (lastEmittedDecision.trackId == candidate.trackId) {
                val updatedPrevious = lastEmittedDecision.copy(
                    threatScore = candidate.threatScore,
                    distanceScore = candidate.distanceScore,
                    timestamp = currentTime
                )
                _navigationDecisionState.value = updatedPrevious
                lastEmittedDecision = updatedPrevious
            }
            return
        }

        // Apply repeated warning suppression
        val isNewObject = candidate.trackId != lastEmittedDecision.trackId
        val isNewLevel = candidate.threatLevel != lastEmittedDecision.threatLevel
        
        if (isNewObject || isNewLevel || lastEmittedDecision == defaultSafeDecision) {
            android.util.Log.d("DrishtiDebug", "Navigation decision: ${candidate.threatLevel} | Reason: ${candidate.reason}")
            _navigationDecisionState.value = candidate
            lastEmittedDecision = candidate
            lastDecisionChangeTimestamp = currentTime
        } else {
            // Update live telemetry values without re-broadcasting decision alert state
            val updatedDecision = lastEmittedDecision.copy(
                threatScore = candidate.threatScore,
                distanceScore = candidate.distanceScore,
                timestamp = currentTime
            )
            _navigationDecisionState.value = updatedDecision
            lastEmittedDecision = updatedDecision
        }
    }
}
