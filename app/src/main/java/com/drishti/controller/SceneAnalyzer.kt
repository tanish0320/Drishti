package com.drishti.controller

import com.drishti.models.AppMode
import com.drishti.models.ModeDecision
import com.drishti.models.ThreatLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SceneAnalyzer @Inject constructor() {

    /**
     * Examines navigation threat levels and detected OCR text blocks to recommend the next mode.
     */
    fun analyze(
        threatLevel: ThreatLevel,
        hasText: Boolean
    ): ModeDecision {
        return when {
            // Rule 1 & 5: Dangerous obstacle always overrides reading
            threatLevel == ThreatLevel.CRITICAL || threatLevel == ThreatLevel.WARNING -> {
                ModeDecision(
                    recommendedMode = AppMode.WALK,
                    reason = "Dangerous obstacle ($threatLevel) detected in walking path."
                )
            }
            // Rule 2: If path is safe and readable text is found
            hasText -> {
                ModeDecision(
                    recommendedMode = AppMode.READ,
                    reason = "Readable text detected in camera view."
                )
            }
            // Rule 3: No threat and no text -> Default to Walk Mode
            else -> {
                ModeDecision(
                    recommendedMode = AppMode.WALK,
                    reason = "No text found. Defaulting to navigation guidance."
                )
            }
        }
    }
}
