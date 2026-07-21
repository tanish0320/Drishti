package com.drishti.repository

import com.drishti.detection.DetectionEngine
import com.drishti.models.CameraFrame
import com.drishti.models.DetectionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetectionRepository @Inject constructor(
    private val detectionEngine: DetectionEngine
) {
    fun getDetections(frameStream: Flow<CameraFrame>): Flow<DetectionResult> {
        // TODO: Wire frameStream to detectionEngine and filter detections in Phase 2
        return flow { }
    }
}
