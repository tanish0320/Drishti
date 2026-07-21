package com.drishti.haptics

import com.drishti.detection.DecisionEngine
import com.drishti.models.NavigationDecision
import com.drishti.models.ThreatLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticManager @Inject constructor(
    private val decisionEngine: DecisionEngine,
    private val hapticEngine: HapticEngine
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var lastDecision: NavigationDecision? = null

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
        val last = lastDecision
        lastDecision = decision

        if (last == null) {
            if (decision.threatLevel != ThreatLevel.SAFE) {
                hapticEngine.setThreatPattern(decision.threatLevel)
            }
            return
        }

        // Change haptics only on level transitions or track substitutions
        val levelChanged = decision.threatLevel != last.threatLevel
        val trackChanged = decision.trackId != last.trackId

        if (levelChanged || trackChanged) {
            if (decision.threatLevel == ThreatLevel.SAFE) {
                hapticEngine.cancel()
            } else {
                hapticEngine.setThreatPattern(decision.threatLevel)
            }
        }
    }
}
