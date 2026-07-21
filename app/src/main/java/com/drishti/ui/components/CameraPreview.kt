package com.drishti.ui.components

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.drishti.camera.FrameProvider
import com.drishti.controller.ModeControllerInterface
import com.drishti.controller.AutoModeManager
import com.drishti.detection.DecisionEngine
import com.drishti.detection.DetectionEngine
import com.drishti.haptics.HapticEngine
import com.drishti.models.AppMode
import com.drishti.ocr.OCRProcessor
import com.drishti.repository.SettingsRepository
import com.drishti.speech.SpeechEngine
import com.drishti.utils.PerformanceMonitor
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    frameProvider: FrameProvider,
    detectionEngine: DetectionEngine,
    decisionEngine: DecisionEngine,
    speechEngine: SpeechEngine,
    hapticEngine: HapticEngine,
    ocrProcessor: OCRProcessor,
    currentMode: AppMode,
    modeController: ModeControllerInterface,
    autoModeManager: AutoModeManager,
    settingsRepository: SettingsRepository,
    performanceMonitor: PerformanceMonitor,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Keep screen awake while camera is active
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Performance: background executor for the analyzer prevents UI thread blocking
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val settingsState by settingsRepository.settings.collectAsState()

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            // Performance: keep only the latest frame to prevent queuing latency
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            
            // Configure Target Aspect Ratio: 16:9
            previewTargetSize = CameraController.OutputSize(AspectRatio.RATIO_16_9)
            imageAnalysisTargetSize = CameraController.OutputSize(AspectRatio.RATIO_16_9)

            // Wire the FrameProvider analyzer callback
            setImageAnalysisAnalyzer(
                analyzerExecutor,
                frameProvider as ImageAnalysis.Analyzer
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f) // Maintain square UI element ratio from Stitch layout
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    controller = cameraController
                    cameraController.bindToLifecycle(lifecycleOwner)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay matches camera preview size exactly
        if (settingsState.overlayVisible) {
            CameraOverlay(
                detectionEngine = detectionEngine,
                decisionEngine = decisionEngine,
                speechEngine = speechEngine,
                hapticEngine = hapticEngine,
                ocrProcessor = ocrProcessor,
                currentMode = currentMode,
                modeController = modeController,
                autoModeManager = autoModeManager,
                debugMode = settingsState.debugMode,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Diagnostic Gauges Overlay Card (Conditional on debugMode)
        if (settingsState.debugMode) {
            PerformanceOverlay(
                performanceMonitor = performanceMonitor,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}
