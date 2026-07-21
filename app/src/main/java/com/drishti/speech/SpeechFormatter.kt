package com.drishti.speech

import com.drishti.models.NavigationDecision
import com.drishti.models.ThreatLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechFormatter @Inject constructor() {

    /**
     * Formats a NavigationDecision into a concise, natural spoken instruction.
     */
    fun format(decision: NavigationDecision): String {
        if (decision.threatLevel == ThreatLevel.SAFE) {
            return "Obstacle cleared."
        }

        val name = decision.className.lowercase()
        
        // Map distance score to natural depth phrases
        val distPhrase = when {
            decision.distanceScore >= 0.8f -> "immediate"
            decision.distanceScore >= 0.6f -> "very close"
            decision.distanceScore >= 0.4f -> "nearby"
            else -> "far"
        }

        // Map direction enum to verbal instructions
        val dirPhrase = when (decision.relativeDirection) {
            "left" -> "on your left"
            "right" -> "on your right"
            else -> "ahead"
        }

        // Prefix urgency commands based on threat levels
        return when (decision.threatLevel) {
            ThreatLevel.CRITICAL -> "Stop. $name is $distPhrase $dirPhrase."
            ThreatLevel.WARNING -> "$name $distPhrase $dirPhrase."
            ThreatLevel.CAUTION -> "Caution. $name $distPhrase $dirPhrase."
            ThreatLevel.SAFE -> "Obstacle cleared."
        }
    }
}
