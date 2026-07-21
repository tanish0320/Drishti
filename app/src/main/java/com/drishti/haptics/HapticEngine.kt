package com.drishti.haptics

import com.drishti.models.HapticRequest
import com.drishti.models.ThreatLevel
import kotlinx.coroutines.flow.StateFlow

interface HapticEngine {
    val currentPatternState: StateFlow<String>
    val currentIntensityState: StateFlow<String>
    val lastVibrationTimestampState: StateFlow<Long>
    val hapticStatusState: StateFlow<String>

    fun triggerHaptic(request: HapticRequest)
    fun setThreatPattern(threatLevel: ThreatLevel)
    fun cancel()
}
