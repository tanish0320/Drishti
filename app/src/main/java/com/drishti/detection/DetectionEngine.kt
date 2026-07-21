package com.drishti.detection

import com.drishti.models.CameraFrame
import com.drishti.models.DetectionResult
import com.drishti.models.PrioritizedDetection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DetectionEngine {
    val detectionsState: StateFlow<List<DetectionResult>>
    val filteredDetectionsState: StateFlow<List<DetectionResult>>
    val prioritizedDetectionsState: StateFlow<List<PrioritizedDetection>>
    val activeTracksCountState: StateFlow<Int>
    val inferenceDurationState: StateFlow<Long>
    fun startDetection(): Flow<DetectionResult>
    fun stopDetection()
    fun processFrame(frame: CameraFrame): DetectionResult
}
