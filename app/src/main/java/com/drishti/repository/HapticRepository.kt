package com.drishti.repository

import com.drishti.haptics.HapticEngine
import com.drishti.models.HapticRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticRepository @Inject constructor(
    private val hapticEngine: HapticEngine
) {
    fun triggerVibration(intensity: Float, durationMs: Long) {
        // TODO: Invoke hapticEngine in Phase 2
        hapticEngine.triggerHaptic(HapticRequest(intensity, durationMs))
    }

    fun stopVibration() {
        // TODO: Invoke hapticEngine.cancel() in Phase 2
        hapticEngine.cancel()
    }
}
