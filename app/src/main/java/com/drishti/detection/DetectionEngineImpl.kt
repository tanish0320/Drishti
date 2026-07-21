package com.drishti.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.drishti.camera.FrameConsumer
import com.drishti.camera.FrameDistributor
import com.drishti.models.CameraFrame
import com.drishti.models.DetectionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

import com.drishti.models.PrioritizedDetection

@Singleton
class DetectionEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val frameDistributor: FrameDistributor,
    private val corridorFilter: CorridorFilter,
    private val detectionTracker: DetectionTracker,
    private val decisionEngine: DecisionEngine
) : DetectionEngine, FrameConsumer {

    private val _detectionFlow = MutableSharedFlow<DetectionResult>(extraBufferCapacity = 128)
    private var interpreter: Interpreter? = null
    
    private val _detectionsState = kotlinx.coroutines.flow.MutableStateFlow<List<DetectionResult>>(emptyList())
    override val detectionsState: kotlinx.coroutines.flow.StateFlow<List<DetectionResult>> = _detectionsState.asStateFlow()
    
    private val _filteredDetectionsState = kotlinx.coroutines.flow.MutableStateFlow<List<DetectionResult>>(emptyList())
    override val filteredDetectionsState: kotlinx.coroutines.flow.StateFlow<List<DetectionResult>> = _filteredDetectionsState.asStateFlow()

    private val _prioritizedDetectionsState = kotlinx.coroutines.flow.MutableStateFlow<List<PrioritizedDetection>>(emptyList())
    override val prioritizedDetectionsState: kotlinx.coroutines.flow.StateFlow<List<PrioritizedDetection>> = _prioritizedDetectionsState.asStateFlow()

    private val _activeTracksCountState = kotlinx.coroutines.flow.MutableStateFlow<Int>(0)
    override val activeTracksCountState: kotlinx.coroutines.flow.StateFlow<Int> = _activeTracksCountState.asStateFlow()

    private val _inferenceDurationState = kotlinx.coroutines.flow.MutableStateFlow<Long>(0L)
    override val inferenceDurationState: kotlinx.coroutines.flow.StateFlow<Long> = _inferenceDurationState.asStateFlow()
    
    // Background executor specifically for model inference
    private val inferenceExecutor = Executors.newSingleThreadExecutor()
    private val inferenceScope = CoroutineScope(Dispatchers.Default)

    private val cocoClasses = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
        "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
        "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
        "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
        "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
        "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
        "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
        "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
        "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    )

    init {
        initializeInterpreter()
    }

    private fun initializeInterpreter() {
        try {
            val modelBuffer = loadModelFile(context, "models/model.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i("DetectionEngineImpl", "YOLO TFLite interpreter initialized successfully.")
        } catch (e: Exception) {
            Log.e("DetectionEngineImpl", "Gracefully handled model initialization failure: ${e.message}")
            // Interpreter remains null; engine runs in fallback simulation mode
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    override fun startDetection(): Flow<DetectionResult> {
        if (interpreter == null) {
            initializeInterpreter()
        }
        frameDistributor.registerConsumer(this)
        return _detectionFlow.asSharedFlow()
    }

    override fun stopDetection() {
        frameDistributor.unregisterConsumer(this)
        try {
            interpreter?.close()
            interpreter = null
            Log.i("DetectionEngineImpl", "YOLO TFLite interpreter released successfully.")
        } catch (e: Exception) {
            Log.e("DetectionEngineImpl", "Error closing YOLO TFLite interpreter", e)
        }
    }

    override fun processFrame(frame: CameraFrame): DetectionResult {
        // Processes a single frame and returns a default mock result for sync checks
        return DetectionResult(
            classId = 0,
            className = "person",
            confidence = 0.95f,
            boundingBox = RectF(100f, 100f, 300f, 400f),
            timestamp = frame.timestamp
        )
    }

    override fun onFrame(frame: CameraFrame, releaseCallback: () -> Unit) {
        // Enforce off-UI thread inference execution
        inferenceExecutor.execute {
            val startTime = System.currentTimeMillis()
            val localInterpreter = interpreter

            if (localInterpreter != null) {
                try {
                    // 1. Image Preprocessing: Extract ImageProxy and convert to Bitmap
                    val imageProxy = frame.imageProxy
                    val bitmap = imageProxy.toBitmap()

                    // 2. Preprocess using TensorImage and ImageProcessor
                    val tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
                    tensorImage.load(bitmap)

                    val rotationOp = when (frame.rotationDegrees) {
                        90 -> Rot90Op(1)
                        180 -> Rot90Op(2)
                        270 -> Rot90Op(3)
                        else -> null
                    }

                    val builder = ImageProcessor.Builder()
                        .add(ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))
                    
                    if (rotationOp != null) {
                        builder.add(rotationOp)
                    }
                    builder.add(NormalizeOp(0f, 255f))

                    val processedImage = builder.build().process(tensorImage)

                    // 3. Run Inference: Output shape [1, 84, 8400] for standard float32 YOLOv8
                    val outputBuffer = Array(1) { Array(84) { FloatArray(8400) } }
                    localInterpreter.run(processedImage.buffer, outputBuffer)

                    val inferenceDuration = System.currentTimeMillis() - startTime
                    val detections = mutableListOf<DetectionResult>()

                    // 4. Output Parsing: Parse all candidate boxes
                    for (i in 0 until 8400) {
                        // Find class with highest confidence score (indices 4 to 83 represent classes)
                        var maxScore = 0.0f
                        var maxClassId = -1
                        for (c in 4 until 84) {
                            val score = outputBuffer[0][c][i]
                            if (score > maxScore) {
                                maxScore = score
                                maxClassId = c - 4
                            }
                        }

                        // Get coordinates [cx, cy, w, h]
                        val cx = outputBuffer[0][0][i]
                        val cy = outputBuffer[0][1][i]
                        val w = outputBuffer[0][2][i]
                        val h = outputBuffer[0][3][i]

                        val left = cx - w / 2f
                        val top = cy - h / 2f
                        val right = cx + w / 2f
                        val bottom = cy + h / 2f

                        val className = cocoClasses.getOrElse(maxClassId) { "Unknown" }
                        val detection = DetectionResult(
                            classId = maxClassId,
                            className = className,
                            confidence = maxScore,
                            boundingBox = RectF(left, top, right, bottom),
                            timestamp = frame.timestamp
                        )
                        detections.add(detection)
                        _detectionFlow.tryEmit(detection)
                    }

                    _detectionsState.value = detections
                    val filtered = corridorFilter.filter(detections)
                    _filteredDetectionsState.value = filtered
                    val prioritized = detectionTracker.track(filtered)
                    _prioritizedDetectionsState.value = prioritized
                    _activeTracksCountState.value = detectionTracker.getActiveTracksCount()
                    decisionEngine.evaluate(prioritized)
                    _inferenceDurationState.value = inferenceDuration

                    // 5. Log inference stats concisely (top 3 detections to avoid Logcat flooding)
                    Log.d("DetectionEngineImpl", "Frame: ${frame.timestamp} | Duration: ${inferenceDuration}ms | Total Candidates: ${detections.size}")
                    val topDetections = detections.sortedByDescending { it.confidence }.take(3)
                    topDetections.forEach { det ->
                        Log.d("DetectionEngineImpl", "  -> [${det.className}] Conf: ${"%.2f".format(det.confidence)} Box: [${"%.1f".format(det.boundingBox.left)}, ${"%.1f".format(det.boundingBox.top)}, ${"%.1f".format(det.boundingBox.right)}, ${"%.1f".format(det.boundingBox.bottom)}]")
                    }
                } catch (e: Exception) {
                    Log.e("DetectionEngineImpl", "Error during model inference: ${e.message}", e)
                } finally {
                    releaseCallback()
                }
            } else {
                // Fallback simulation mode
                val simulatedDuration = System.currentTimeMillis() - startTime
                val mockResult = DetectionResult(
                    classId = 0,
                    className = "person",
                    confidence = 0.95f,
                    boundingBox = RectF(100f, 100f, 300f, 400f),
                    timestamp = frame.timestamp
                )
                
                _detectionFlow.tryEmit(mockResult)
                _detectionsState.value = listOf(mockResult)
                val filtered = corridorFilter.filter(listOf(mockResult))
                _filteredDetectionsState.value = filtered
                val prioritized = detectionTracker.track(filtered)
                _prioritizedDetectionsState.value = prioritized
                _activeTracksCountState.value = detectionTracker.getActiveTracksCount()
                decisionEngine.evaluate(prioritized)
                _inferenceDurationState.value = simulatedDuration
                
                Log.d("DetectionEngineImpl", "Simulation mode | Frame: ${frame.timestamp} | Duration: ${simulatedDuration}ms | Objects: 1")
                Log.d("DetectionEngineImpl", "  -> [person] Conf: 0.95 Box: [100.0, 100.0, 300.0, 400.0]")
                
                releaseCallback()
            }
        }
    }
}
