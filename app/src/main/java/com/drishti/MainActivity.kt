package com.drishti

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.drishti.camera.DebugFrameConsumer
import com.drishti.camera.FrameDistributor
import com.drishti.detection.DetectionEngine
import com.drishti.permissions.PermissionManager
import com.drishti.ui.navigation.DrishtiNavGraph
import com.drishti.ui.theme.DrishtiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var permissionManager: PermissionManager

    @Inject
    lateinit var frameDistributor: FrameDistributor

    @Inject
    lateinit var detectionEngine: DetectionEngine

    @Inject
    lateinit var speechManager: com.drishti.speech.SpeechManager

    @Inject
    lateinit var hapticManager: com.drishti.haptics.HapticManager

    @Inject
    lateinit var ocrProcessor: com.drishti.ocr.OCRProcessor

    @Inject
    lateinit var ocrManager: com.drishti.ocr.OCRManager

    @Inject
    lateinit var autoModeManager: com.drishti.controller.AutoModeManager

    @Inject
    lateinit var speechEngine: com.drishti.speech.SpeechEngine

    @Inject
    lateinit var hapticEngine: com.drishti.haptics.HapticEngine

    @Inject
    lateinit var navigationFusionEngine: com.drishti.navigation.NavigationFusionEngine

    private val debugConsumer = DebugFrameConsumer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        frameDistributor.registerConsumer(debugConsumer)
        detectionEngine.startDetection()
        speechManager.startObserving()
        hapticManager.startObserving()
        navigationFusionEngine.startObserving()
        ocrProcessor.startOcr()
        ocrManager.startObserving()
        autoModeManager.startObserving()
        
        setContent {
            DrishtiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    DrishtiNavGraph(
                        navController = navController,
                        permissionManager = permissionManager
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        frameDistributor.unregisterConsumer(debugConsumer)
        detectionEngine.stopDetection()
        ocrProcessor.stopOcr()
        speechEngine.shutdown()
        hapticEngine.cancel()
    }
}
