package com.drishti.ui.components

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.drishti.controller.ModeControllerImpl
import com.drishti.controller.ModeControllerInterface
import com.drishti.controller.AutoModeManager
import com.drishti.detection.CorridorFilter
import com.drishti.detection.DecisionEngine
import com.drishti.detection.DetectionEngine
import com.drishti.models.AppMode
import com.drishti.models.ThreatLevel
import com.drishti.speech.SpeechEngine
import com.drishti.haptics.HapticEngine
import com.drishti.ocr.OCRProcessor

// Toggle configuration for debug statistics panel
private const val SHOW_DEBUG_OVERLAY = true

@Composable
fun CameraOverlay(
    detectionEngine: DetectionEngine,
    decisionEngine: DecisionEngine,
    speechEngine: SpeechEngine,
    hapticEngine: HapticEngine,
    ocrProcessor: OCRProcessor,
    currentMode: AppMode,
    modeController: ModeControllerInterface,
    autoModeManager: AutoModeManager,
    debugMode: Boolean,
    modifier: Modifier = Modifier
) {
    // Observe Walk mode states
    val prioritizedDetections by detectionEngine.prioritizedDetectionsState.collectAsState()
    val lastInferenceDuration by detectionEngine.inferenceDurationState.collectAsState()
    val navigationDecision by decisionEngine.navigationDecisionState.collectAsState()

    // Observe Speech & Haptic states
    val currentSpokenMessage by speechEngine.currentSpokenMessageState.collectAsState()
    val speechQueueLength by speechEngine.speechQueueLengthState.collectAsState()
    val ttsStatus by speechEngine.ttsStatusState.collectAsState()
    val lastAnnouncementTimestamp by speechEngine.lastAnnouncementTimestampState.collectAsState()

    val hapticPattern by hapticEngine.currentPatternState.collectAsState()
    val hapticIntensity by hapticEngine.currentIntensityState.collectAsState()
    val lastVibrationTimestamp by hapticEngine.lastVibrationTimestampState.collectAsState()
    val hapticStatus by hapticEngine.hapticStatusState.collectAsState()

    // Observe Read mode states
    val ocrResult by ocrProcessor.ocrResultsState.collectAsState()
    val ocrFps by ocrProcessor.ocrFpsState.collectAsState()
    val ocrLatency by ocrProcessor.ocrLatencyState.collectAsState()
    val charactersRecognized by ocrProcessor.charactersRecognizedState.collectAsState()
    val lastRecognizedString by ocrProcessor.lastRecognizedStringState.collectAsState()

    // Observe Auto Mode states
    val controller = modeController as ModeControllerImpl
    val selectedMode by controller.userSelectedModeState.collectAsState()
    val modeReason by controller.modeReasonState.collectAsState()
    val modeHistory by controller.modeHistoryState.collectAsState()

    // Query screen local density to transform stroke widths to native pixels
    val density = LocalDensity.current
    val thinStrokePx = remember(density) { with(density) { 1.5f.dp.toPx() } }
    val thickStrokePx = remember(density) { with(density) { 4f.dp.toPx() } }

    // Reusable Paint resources remembered to prevent memory allocation during draw cycles
    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }
    val textBgPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#99000000") // Black background with 60% opacity
            style = Paint.Style.FILL
        }
    }
    val priorityBgPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#CCFF0000") // Translucent Red for critical alerts
            style = Paint.Style.FILL
        }
    }
    val ocrBgPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#990000FF") // Translucent Blue for OCR tags
            style = Paint.Style.FILL
        }
    }
    val debugTextPaint = remember {
        Paint().apply {
            color = android.graphics.Color.YELLOW
            textSize = 28f
            style = Paint.Style.FILL
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    val debugBgPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#CC000000") // Dark background with 80% opacity
            style = Paint.Style.FILL
        }
    }

    val textBounds = remember { Rect() }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val currentTime = System.currentTimeMillis()
        val panelWidth = 560f
        val panelHeight = 350f

        if (currentMode == AppMode.WALK) {
            // ==========================================
            // WALK MODE OVERLAY RENDERING
            // ==========================================
            val scaleX = size.width / 640f
            val scaleY = size.height / 640f

            // 1. Draw corridor outline
            val topLeftX = (320f - CorridorFilter.TOP_CORRIDOR_WIDTH / 2f) * scaleX
            val topLeftY = CorridorFilter.VERTICAL_START_POSITION * scaleY
            val topRightX = (320f + CorridorFilter.TOP_CORRIDOR_WIDTH / 2f) * scaleX
            val topRightY = CorridorFilter.VERTICAL_START_POSITION * scaleY
            val bottomRightX = (320f + CorridorFilter.BOTTOM_CORRIDOR_WIDTH / 2f) * scaleX
            val bottomRightY = CorridorFilter.VERTICAL_END_POSITION * scaleY
            val bottomLeftX = (320f - CorridorFilter.BOTTOM_CORRIDOR_WIDTH / 2f) * scaleX
            val bottomLeftY = CorridorFilter.VERTICAL_END_POSITION * scaleY

            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(topLeftX, topLeftY)
                lineTo(topRightX, topRightY)
                lineTo(bottomRightX, bottomRightY)
                lineTo(bottomLeftX, bottomLeftY)
                close()
            }

            drawPath(
                path = path,
                color = Color.Cyan.copy(alpha = 0.5f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = dashEffect
                )
            )

            // 2. Draw Walk Detections
            prioritizedDetections.forEach { detection ->
                val left = detection.boundingBox.left * scaleX
                val top = detection.boundingBox.top * scaleY
                val right = detection.boundingBox.right * scaleX
                val bottom = detection.boundingBox.bottom * scaleY

                val isHighestPriority = detection.trackId == navigationDecision.trackId
                val boxColor = if (isHighestPriority) Color.Red else Color.Green
                val strokePx = if (isHighestPriority) thickStrokePx else thinStrokePx

                drawRect(
                    color = boxColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = strokePx)
                )

                drawIntoCanvas { canvas ->
                    val label = "${detection.className} ${(detection.confidence * 100).toInt()}%"
                    textPaint.getTextBounds(label, 0, label.length, textBounds)
                    val textHeight = textBounds.height()
                    val textWidth = textBounds.width()
                    
                    canvas.nativeCanvas.drawRect(
                        left - 2f,
                        top - textHeight - 16f,
                        left + textWidth + 16f,
                        top,
                        if (isHighestPriority) priorityBgPaint else textBgPaint
                    )
                    canvas.nativeCanvas.drawText(label, left + 8f, top - 8f, textPaint)

                    var detailLabel = "ID:${detection.trackId} D:${"%.2f".format(detection.distance)} T:${"%.2f".format(detection.threatScore)}"
                    if (isHighestPriority) {
                        detailLabel += " [${navigationDecision.threatLevel}]"
                    }
                    textPaint.getTextBounds(detailLabel, 0, detailLabel.length, textBounds)
                    val detailHeight = textBounds.height()
                    val detailWidth = textBounds.width()
                    
                    canvas.nativeCanvas.drawRect(
                        left - 2f,
                        bottom,
                        left + detailWidth + 16f,
                        bottom + detailHeight + 16f,
                        if (isHighestPriority) priorityBgPaint else textBgPaint
                    )
                    canvas.nativeCanvas.drawText(detailLabel, left + 8f, bottom + detailHeight + 8f, textPaint)

                    if (isHighestPriority) {
                        val reasonLabel = navigationDecision.reason
                        textPaint.getTextBounds(reasonLabel, 0, reasonLabel.length, textBounds)
                        val reasonHeight = textBounds.height()
                        val reasonWidth = textBounds.width()
                        val reasonTop = bottom + detailHeight + 20f

                        canvas.nativeCanvas.drawRect(
                            left - 2f,
                            reasonTop,
                            left + reasonWidth + 16f,
                            reasonTop + reasonHeight + 16f,
                            priorityBgPaint
                        )
                        canvas.nativeCanvas.drawText(reasonLabel, left + 8f, reasonTop + reasonHeight + 8f, textPaint)
                    }
                }
            }

            // 3. Draw Walk Dashboards
            if (debugMode && SHOW_DEBUG_OVERLAY) {
                // Left: Decision & Auto Mode Debug
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(16f, 16f, 16f + panelWidth, 16f + panelHeight, debugBgPaint)
                    canvas.nativeCanvas.drawText("Drishti Decision Debug", 32f, 50f, debugTextPaint)
                    canvas.nativeCanvas.drawText("Avg Inference   : ${lastInferenceDuration}ms", 32f, 88f, debugTextPaint)
                    canvas.nativeCanvas.drawText("Decision Threat : ${navigationDecision.threatLevel}", 32f, 126f, debugTextPaint)
                    canvas.nativeCanvas.drawText("High Priority ID: ${if (navigationDecision.trackId == -1) "None" else "ID:${navigationDecision.trackId} (${navigationDecision.className})"} ", 32f, 164f, debugTextPaint)
                    
                    // Auto Mode Metrics
                    val prevModeStr = modeHistory.lastOrNull()?.previousMode?.name ?: "None"
                    canvas.nativeCanvas.drawText("User Selected   : $selectedMode (Effective: $currentMode)", 32f, 202f, debugTextPaint)
                    
                    val dispReason = if (modeReason.length > 25) modeReason.take(22) + "..." else modeReason
                    canvas.nativeCanvas.drawText("Prev / Reason   : $prevModeStr | $dispReason", 32f, 240f, debugTextPaint)
                    
                    val textTimer = autoModeManager.getStableTextTimerRemaining()
                    canvas.nativeCanvas.drawText("Stable Text Tmr : ${textTimer}ms", 32f, 278f, debugTextPaint)
                    
                    val elapsedSwitch = autoModeManager.getLastTransitionTimeElapsed() / 1000f
                    canvas.nativeCanvas.drawText("Last Switch     : ${"%.1f".format(elapsedSwitch)}s ago", 32f, 316f, debugTextPaint)
                }

                // Right: Output Debug
                drawIntoCanvas { canvas ->
                    val startX = size.width - panelWidth - 16f
                    canvas.nativeCanvas.drawRect(startX, 16f, startX + panelWidth, 16f + panelHeight, debugBgPaint)
                    canvas.nativeCanvas.drawText("Drishti Output Debug", startX + 16f, 50f, debugTextPaint)
                    
                    val spokenMsg = currentSpokenMessage
                    val displaySpoken = if (spokenMsg.length > 20) spokenMsg.take(17) + "..." else if (spokenMsg.isEmpty()) "Idle" else spokenMsg
                    canvas.nativeCanvas.drawText("TTS  : $ttsStatus | Spoken: $displaySpoken", startX + 16f, 88f, debugTextPaint)
                    canvas.nativeCanvas.drawText("Speech Queue Length : $speechQueueLength", startX + 16f, 126f, debugTextPaint)
                    canvas.nativeCanvas.drawText("Haptic Status       : $hapticStatus ($hapticIntensity)", startX + 16f, 164f, debugTextPaint)
                    canvas.nativeCanvas.drawText("Vibration Pattern   : $hapticPattern", startX + 16f, 202f, debugTextPaint)

                    val msAgoText = if (lastAnnouncementTimestamp == 0L) "Never" else "${(currentTime - lastAnnouncementTimestamp) / 1000f}s ago"
                    canvas.nativeCanvas.drawText("Last Audio Alert    : $msAgoText", startX + 16f, 240f, debugTextPaint)

                    val vibMsAgoText = if (lastVibrationTimestamp == 0L) "Never" else "${(currentTime - lastVibrationTimestamp) / 1000f}s ago"
                    canvas.nativeCanvas.drawText("Last Tactile Alert  : $vibMsAgoText", startX + 16f, 278f, debugTextPaint)
                }
            }
        } else if (currentMode == AppMode.READ) {
            // ==========================================
            // READ MODE OVERLAY RENDERING
            // ==========================================
            
            // Dynamic scaling math that handles all aspect ratios and Crops
            val frameW = ocrResult.frameWidth.toFloat()
            val frameH = ocrResult.frameHeight.toFloat()
            
            val scale = if (frameW / frameH > 1f) size.height / frameH else size.width / frameW
            val offsetX = if (frameW / frameH > 1f) (frameW - frameH) / 2f else 0f
            val offsetY = if (frameW / frameH > 1f) 0f else (frameH - frameW) / 2f

            // 1. Draw blue rectangles for OCR regions
            ocrResult.blocks.forEach { block ->
                val box = block.boundingBox
                if (box != null) {
                    val left = (box.left - offsetX) * scale
                    val top = (box.top - offsetY) * scale
                    val right = (box.right - offsetX) * scale
                    val bottom = (box.bottom - offsetY) * scale

                    drawRect(
                        color = Color.Blue,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = thinStrokePx)
                    )

                    drawIntoCanvas { canvas ->
                        val textStr = block.text
                        val displayText = if (textStr.length > 25) textStr.take(22) + "..." else textStr
                        textPaint.getTextBounds(displayText, 0, displayText.length, textBounds)
                        
                        val textHeight = textBounds.height()
                        val textWidth = textBounds.width()

                        canvas.nativeCanvas.drawRect(
                            left - 2f,
                            top - textHeight - 16f,
                            left + textWidth + 16f,
                            top,
                            ocrBgPaint
                        )
                        canvas.nativeCanvas.drawText(displayText, left + 8f, top - 8f, textPaint)
                    }
                }
            }

            // 2. Draw OCR Dashboards
            if (debugMode && SHOW_DEBUG_OVERLAY) {
                // Left: OCR & Auto Mode Debug
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(16f, 16f, 16f + panelWidth, 16f + panelHeight, debugBgPaint)
                    canvas.nativeCanvas.drawText("Drishti OCR Debug", 32f, 50f, debugTextPaint)
                    canvas.nativeCanvas.drawText("OCR Status      : ACTIVE", 32f, 88f, debugTextPaint)
                    canvas.nativeCanvas.drawText("OCR Speed       : ${"%.1f".format(ocrFps)} FPS", 32f, 126f, debugTextPaint)
                    canvas.nativeCanvas.drawText("Latency         : ${ocrLatency}ms", 32f, 164f, debugTextPaint)
                    canvas.nativeCanvas.drawText("Blocks / Chars  : ${ocrResult.blocks.size} / $charactersRecognized", 32f, 202f, debugTextPaint)
                    
                    val displayString = if (lastRecognizedString.length > 20) lastRecognizedString.take(17) + "..." else if (lastRecognizedString.isEmpty()) "None" else lastRecognizedString
                    canvas.nativeCanvas.drawText("Last String     : $displayString", 32f, 240f, debugTextPaint)

                    // Auto Mode Metrics
                    val prevModeStr = modeHistory.lastOrNull()?.previousMode?.name ?: "None"
                    canvas.nativeCanvas.drawText("User Selected   : $selectedMode (Effective: $currentMode)", 32f, 278f, debugTextPaint)
                    
                    val elapsedSwitch = autoModeManager.getLastTransitionTimeElapsed() / 1000f
                    canvas.nativeCanvas.drawText("Prev / Switch   : $prevModeStr | ${"%.1f".format(elapsedSwitch)}s ago", 32f, 316f, debugTextPaint)
                }

                // Right: Speech Debug (active during OCR readings)
                drawIntoCanvas { canvas ->
                    val startX = size.width - panelWidth - 16f
                    canvas.nativeCanvas.drawRect(startX, 16f, startX + panelWidth, 16f + panelHeight, debugBgPaint)
                    canvas.nativeCanvas.drawText("Drishti Speech Debug", startX + 16f, 50f, debugTextPaint)
                    canvas.nativeCanvas.drawText("TTS Status      : $ttsStatus", startX + 16f, 88f, debugTextPaint)
                    canvas.nativeCanvas.drawText("Queue Length    : $speechQueueLength", startX + 16f, 126f, debugTextPaint)

                    val spokenMsg = currentSpokenMessage
                    val displaySpoken = if (spokenMsg.length > 24) spokenMsg.take(21) + "..." else if (spokenMsg.isEmpty()) "Idle" else spokenMsg
                    canvas.nativeCanvas.drawText("Spoken Msg      : $displaySpoken", startX + 16f, 164f, debugTextPaint)

                    val msAgoText = if (lastAnnouncementTimestamp == 0L) "Never" else "${(currentTime - lastAnnouncementTimestamp) / 1000f}s ago"
                    canvas.nativeCanvas.drawText("Last Reading    : $msAgoText", startX + 16f, 202f, debugTextPaint)
                }
            }
        }
    }
}
