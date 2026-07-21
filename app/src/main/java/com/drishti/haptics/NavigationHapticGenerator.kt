package com.drishti.haptics

import com.drishti.models.ThreatLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationHapticGenerator @Inject constructor(
    private val patternGenerator: HapticPatternGenerator
) {
    /**
     * Resolves the haptic feedback vibration timings and amplitude map.
     */
    fun generatePattern(
        threatLevel: ThreatLevel, 
        intensity: String
    ): HapticPatternGenerator.HapticPattern {
        return patternGenerator.generate(threatLevel, intensity)
    }
}
