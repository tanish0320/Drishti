package com.drishti.utils

data class PerformanceSnapshot(
    val yoloFps: Float,
    val ocrFps: Float,
    val inferenceLatencyMs: Long,
    val frameProcessingLatencyMs: Long,
    val droppedFrames: Int,
    val memoryUsageMb: Long,
    val cpuUsagePercent: Float,
    val batteryPercent: Int
)
