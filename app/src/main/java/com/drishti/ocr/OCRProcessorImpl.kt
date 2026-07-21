package com.drishti.ocr

import android.content.Context
import android.util.Log
import com.drishti.camera.FrameConsumer
import com.drishti.camera.FrameDistributor
import com.drishti.models.AppMode
import com.drishti.models.CameraFrame
import com.drishti.models.OCRBlock
import com.drishti.models.OCRResult
import com.drishti.repository.ControllerRepository
import com.drishti.repository.SettingsRepository
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OCRProcessorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val frameDistributor: FrameDistributor,
    private val controllerRepository: ControllerRepository,
    private val settingsRepository: SettingsRepository,
    private val textBlockAnalyzer: TextBlockAnalyzer,
    private val readingOrderResolver: ReadingOrderResolver
) : OCRProcessor, FrameConsumer {

    companion object {
        // Throttled interval to process frames at ~5-6 FPS to prevent heating/battery drain
        private const val OCR_INTERVAL_MS = 180L
    }

    private val _ocrResultsState = MutableStateFlow<OCRResult>(OCRResult())
    override val ocrResultsState: StateFlow<OCRResult> = _ocrResultsState.asStateFlow()

    private val _recognizedTextState = MutableStateFlow<String>("")
    override val recognizedTextState: StateFlow<String> = _recognizedTextState.asStateFlow()

    private val _ocrFpsState = MutableStateFlow<Float>(0f)
    override val ocrFpsState: StateFlow<Float> = _ocrFpsState.asStateFlow()

    private val _ocrLatencyState = MutableStateFlow<Long>(0L)
    override val ocrLatencyState: StateFlow<Long> = _ocrLatencyState.asStateFlow()

    private val _charactersRecognizedState = MutableStateFlow<Int>(0)
    override val charactersRecognizedState: StateFlow<Int> = _charactersRecognizedState.asStateFlow()

    private val _lastRecognizedStringState = MutableStateFlow<String>("")
    override val lastRecognizedStringState: StateFlow<String> = _lastRecognizedStringState.asStateFlow()

    // Latin Text Recognizer Client
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var lastProcessedFrameTimeMs = 0L
    private var ocrFrameCount = 0
    private var fpsLastUpdateTime = 0L
    private var lastRecognizedText = ""
    private var isRegistered = false

    override fun startOcr(): Flow<OCRResult> {
        if (!isRegistered) {
            frameDistributor.registerConsumer(this)
            isRegistered = true
            Log.d("OCRProcessorImpl", "OCR registered as frame consumer.")
        }
        return kotlinx.coroutines.flow.flow {
            ocrResultsState.collect {
                emit(it)
            }
        }
    }

    override fun stopOcr() {
        if (isRegistered) {
            frameDistributor.unregisterConsumer(this)
            isRegistered = false
            Log.d("OCRProcessorImpl", "OCR unregistered as frame consumer.")
        }
        _ocrResultsState.value = OCRResult()
        _recognizedTextState.value = ""
        _lastRecognizedStringState.value = ""
        _charactersRecognizedState.value = 0
        lastRecognizedText = ""
    }

    override fun processFrame(frame: CameraFrame): OCRResult {
        return _ocrResultsState.value
    }

    override fun onFrame(frame: CameraFrame, releaseCallback: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        val isReadMode = controllerRepository.currentMode.value == AppMode.READ
        val settings = settingsRepository.settings.value

        // Throttle check
        if (!isReadMode || !settings.ocrEnabled || (currentTime - lastProcessedFrameTimeMs) < OCR_INTERVAL_MS) {
            releaseCallback()
            return
        }

        lastProcessedFrameTimeMs = currentTime

        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                val mediaImage = frame.imageProxy.image
                if (mediaImage != null) {
                    val inputImage = InputImage.fromMediaImage(mediaImage, frame.rotationDegrees)
                    
                    // Perform recognition synchronously on our worker thread
                    val textResult = Tasks.await(recognizer.process(inputImage))
                    val timestamp = frame.timestamp

                    // Extract and filter blocks
                    val analyzedBlocks = textBlockAnalyzer.analyze(
                        textResult = textResult,
                        minTextSizePx = settings.ocrMinTextSize,
                        timestamp = timestamp
                    )

                    // Sort in natural reading order
                    val sortedBlocks = readingOrderResolver.resolve(analyzedBlocks)
                    val fullText = sortedBlocks.joinToString(" ") { it.text }.trim()

                    // Post results
                    val rotatedWidth = if (frame.rotationDegrees == 90 || frame.rotationDegrees == 270) frame.height else frame.width
                    val rotatedHeight = if (frame.rotationDegrees == 90 || frame.rotationDegrees == 270) frame.width else frame.height
                    val result = OCRResult(
                        blocks = sortedBlocks,
                        fullText = fullText,
                        timestamp = timestamp,
                        frameWidth = rotatedWidth,
                        frameHeight = rotatedHeight
                    )
                    _ocrResultsState.value = result

                    // Collapse repeated detections over consecutive frames
                    if (fullText.isNotEmpty() && fullText != lastRecognizedText) {
                        lastRecognizedText = fullText
                        _recognizedTextState.value = fullText
                        _lastRecognizedStringState.value = fullText
                        _charactersRecognizedState.value = fullText.length
                    }

                    // Telemetry
                    val elapsed = System.currentTimeMillis() - startTime
                    _ocrLatencyState.value = elapsed
                    updateFpsMetrics(System.currentTimeMillis())
                }
            } catch (e: Exception) {
                Log.e("OCRProcessorImpl", "Error executing ML Kit text recognition: ${e.message}", e)
            } finally {
                // Return proxy lease
                releaseCallback()
            }
        }
    }

    private fun updateFpsMetrics(currentTime: Long) {
        ocrFrameCount++
        val delta = currentTime - fpsLastUpdateTime
        if (delta >= 1000L) {
            val calculatedFps = (ocrFrameCount * 1000f) / delta
            _ocrFpsState.value = calculatedFps
            ocrFrameCount = 0
            fpsLastUpdateTime = currentTime
        }
    }
}
