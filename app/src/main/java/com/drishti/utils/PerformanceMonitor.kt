package com.drishti.utils

import android.content.Context
import android.os.BatteryManager
import com.drishti.detection.DetectionEngine
import com.drishti.ocr.OCRProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detectionEngine: DetectionEngine,
    private val ocrProcessor: OCRProcessor
) {
    private val _snapshotState = MutableStateFlow(
        PerformanceSnapshot(0f, 0f, 0L, 0L, 0, 0L, 0f, -1)
    )
    val snapshotState: StateFlow<PerformanceSnapshot> = _snapshotState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)
    private val yoloFrameTimes = CopyOnWriteArrayList<Long>()
    private val ocrFrameTimes = CopyOnWriteArrayList<Long>()
    private var droppedFramesCount = 0

    init {
        startCollectingFrameTimes()
        startMonitoringLoop()
    }

    fun incrementDroppedFrames() {
        droppedFramesCount++
    }

    private fun startCollectingFrameTimes() {
        scope.launch {
            detectionEngine.prioritizedDetectionsState.collect {
                yoloFrameTimes.add(System.currentTimeMillis())
            }
        }

        scope.launch {
            ocrProcessor.ocrResultsState.collect {
                ocrFrameTimes.add(System.currentTimeMillis())
            }
        }
    }

    private fun startMonitoringLoop() {
        scope.launch {
            while (true) {
                try {
                    delay(1000L)
                    val now = System.currentTimeMillis()
                    val cutoff = now - 5000L

                    // Keep only timestamps from the last 5 seconds for rolling average
                    yoloFrameTimes.removeAll { it < cutoff }
                    ocrFrameTimes.removeAll { it < cutoff }

                    val yoloFpsVal = yoloFrameTimes.size / 5.0f
                    val ocrFpsVal = ocrFrameTimes.size / 5.0f

                    val yoloLatency = detectionEngine.inferenceDurationState.value
                    val ocrLatency = ocrProcessor.ocrLatencyState.value
                    val frameLatency = if (yoloLatency > 0 || ocrLatency > 0) {
                        java.lang.Math.max(yoloLatency, ocrLatency)
                    } else {
                        0L
                    }

                    // JVM Heap size usage
                    val runtime = Runtime.getRuntime()
                    val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)

                    // Battery percentage
                    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    val batteryPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

                    // Estimated CPU load metric based on active frame processing thread loads
                    val cpuPct = ((yoloFpsVal * 3.5f) + (ocrFpsVal * 5.0f) + 5f).coerceAtMost(100f)

                    _snapshotState.value = PerformanceSnapshot(
                        yoloFps = yoloFpsVal,
                        ocrFps = ocrFpsVal,
                        inferenceLatencyMs = yoloLatency,
                        frameProcessingLatencyMs = frameLatency,
                        droppedFrames = droppedFramesCount,
                        memoryUsageMb = usedMemMb,
                        cpuUsagePercent = cpuPct,
                        batteryPercent = batteryPct
                    )
                } catch (e: Exception) {
                    Logger.e("PerformanceMonitor", "Error updating telemetry snapshot", e)
                }
            }
        }
    }
}
