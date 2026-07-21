package com.drishti.haptics

import com.drishti.models.ThreatLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticPatternGenerator @Inject constructor() {

    data class HapticPattern(
        val timings: LongArray,
        val amplitudes: IntArray,
        val repeatIndex: Int
    )

    /**
     * Maps ThreatLevel and intensity string (Low, Medium, High) to a HapticPattern.
     */
    fun generate(threatLevel: ThreatLevel, intensity: String): HapticPattern {
        // Map verbal intensity configuration to raw Android amplitude (0..255)
        val amplitudeVal = when (intensity.lowercase()) {
            "low" -> 85
            "high" -> 255
            else -> 170 // Medium default
        }

        return when (threatLevel) {
            ThreatLevel.CRITICAL -> {
                // Rapid repeating pulses: vibrate 120ms, pause 80ms
                HapticPattern(
                    timings = longArrayOf(0L, 120L, 80L),
                    amplitudes = intArrayOf(0, amplitudeVal, 0),
                    repeatIndex = 0 // Infinite repeat loop
                )
            }
            ThreatLevel.WARNING -> {
                // Two short pulses every 0.8 seconds
                HapticPattern(
                    timings = longArrayOf(0L, 100L, 100L, 100L, 500L),
                    amplitudes = intArrayOf(0, amplitudeVal, 0, amplitudeVal, 0),
                    repeatIndex = 0 // Loop cycle
                )
            }
            ThreatLevel.CAUTION -> {
                // Single short pulse every 1.5 seconds
                HapticPattern(
                    timings = longArrayOf(0L, 100L, 1400L),
                    amplitudes = intArrayOf(0, amplitudeVal, 0),
                    repeatIndex = 0 // Loop cycle
                )
            }
            ThreatLevel.SAFE -> {
                // Squelched / silent
                HapticPattern(
                    timings = longArrayOf(0L),
                    amplitudes = intArrayOf(0),
                    repeatIndex = -1 // No repeat
                )
            }
        }
    }
}
