package com.drishti.detection

import com.drishti.models.ThreatLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreatClassifier @Inject constructor() {

    companion object {
        // Configurable continuous threat score classification thresholds
        const val THRESHOLD_CAUTION = 0.25f
        const val THRESHOLD_WARNING = 0.50f
        const val THRESHOLD_CRITICAL = 0.75f
    }

    /**
     * Classifies a continuous threat score [0.0..1.0] into a discrete [ThreatLevel].
     */
    fun classify(threatScore: Float): ThreatLevel {
        return when {
            threatScore >= THRESHOLD_CRITICAL -> ThreatLevel.CRITICAL
            threatScore >= THRESHOLD_WARNING -> ThreatLevel.WARNING
            threatScore >= THRESHOLD_CAUTION -> ThreatLevel.CAUTION
            else -> ThreatLevel.SAFE
        }
    }
}
