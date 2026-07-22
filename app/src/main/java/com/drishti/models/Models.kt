package com.drishti.models

import androidx.camera.core.ImageProxy
import android.graphics.RectF

enum class AppMode {
    WALK,
    READ,
    AUTO
}

data class DetectionResult(
    val classId: Int,
    val className: String,
    val confidence: Float,
    val boundingBox: RectF,
    val timestamp: Long
)

data class OCRBlock(
    val text: String,
    val boundingBox: android.graphics.Rect?,
    val confidence: Float?,
    val timestamp: Long
)

data class OCRResult(
    val blocks: List<OCRBlock> = emptyList(),
    val fullText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val frameWidth: Int = 720,
    val frameHeight: Int = 1280
)

data class SpeechRequest(
    val text: String = "",
    val language: String = "en"
)

data class HapticRequest(
    val intensity: Float = 0.5f,
    val durationMs: Long = 200
)

data class CameraFrame(
    val timestamp: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val imageProxy: ImageProxy
)

data class AppSettings(
    val language: String = "English",
    val theme: String = "Light",
    val hapticIntensity: String = "Medium",
    val speechEnabled: Boolean = true,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val hapticEnabled: Boolean = true,
    val ocrEnabled: Boolean = true,
    val ocrMinTextSize: Float = 12.0f,
    val overlayVisible: Boolean = true,
    val debugMode: Boolean = true,
    val googleMapsNav: Boolean = true,
    val voiceNav: Boolean = true,
    val routeRecalculation: Boolean = true,
    val walkingPreference: String = "Shortest",
    val avoidBusyRoads: Boolean = false,
    val avoidStairs: Boolean = false,
    val navVoiceVolume: Float = 0.8f
)

data class AppState(
    val isSystemReady: Boolean = true
)

data class AppUiState(
    val currentMode: AppMode = AppMode.WALK,
    val effectiveMode: AppMode = AppMode.WALK,
    val statusMessage: String = "SYSTEM STATUS: CAMERA READY",
    val isCameraReady: Boolean = true,
    val isProcessing: Boolean = false,
    val permissionsGranted: Boolean = false,
    val language: String = "English",
    val theme: String = "Light"
)
