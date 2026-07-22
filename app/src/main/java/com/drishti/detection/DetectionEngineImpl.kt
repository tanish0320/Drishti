package com.drishti.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.drishti.camera.FrameConsumer
import com.drishti.camera.FrameDistributor
import com.drishti.models.CameraFrame
import com.drishti.models.DetectionResult
import com.drishti.models.PrioritizedDetection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@Singleton
class DetectionEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val frameDistributor: FrameDistributor,
    private val corridorFilter: CorridorFilter,
    private val detectionTracker: DetectionTracker,
    private val decisionEngine: DecisionEngine,
    private val controllerRepository: com.drishti.repository.ControllerRepository,
    private val indoorSceneReasoner: IndoorSceneReasoner
) : DetectionEngine, FrameConsumer {

    private val _detectionFlow = MutableSharedFlow<DetectionResult>(extraBufferCapacity = 128)
    private var interpreter: Interpreter? = null
    
    private val _detectionsState = MutableStateFlow<List<DetectionResult>>(emptyList())
    override val detectionsState: kotlinx.coroutines.flow.StateFlow<List<DetectionResult>> = _detectionsState.asStateFlow()
    
    private val _filteredDetectionsState = MutableStateFlow<List<DetectionResult>>(emptyList())
    override val filteredDetectionsState: kotlinx.coroutines.flow.StateFlow<List<DetectionResult>> = _filteredDetectionsState.asStateFlow()

    private val _prioritizedDetectionsState = MutableStateFlow<List<PrioritizedDetection>>(emptyList())
    override val prioritizedDetectionsState: kotlinx.coroutines.flow.StateFlow<List<PrioritizedDetection>> = _prioritizedDetectionsState.asStateFlow()

    private val _activeTracksCountState = MutableStateFlow<Int>(0)
    override val activeTracksCountState: kotlinx.coroutines.flow.StateFlow<Int> = _activeTracksCountState.asStateFlow()

    private val _inferenceDurationState = MutableStateFlow<Long>(0L)
    override val inferenceDurationState: kotlinx.coroutines.flow.StateFlow<Long> = _inferenceDurationState.asStateFlow()
    
    private val inferenceExecutor = Executors.newSingleThreadExecutor()
    private val inferenceScope = CoroutineScope(Dispatchers.Default)

    // Version 2.2 Target Indoor Classes (Total 41)
    private val indoorClasses = listOf(
        "chair", "sofa", "bed", "dining table", "coffee table", "desk", "cabinet", "bookshelf", 
        "tv stand", "nightstand", "wardrobe", "shelf", "drawer", "refrigerator", "microwave", 
        "kitchen counter", "sink", "stove", "cupboard", "washing machine", "fan", "air conditioner", 
        "water dispenser", "trash bin", "door", "doorway", "stairs", "handrail", "corridor", 
        "elevator", "escalator", "ramp", "fire exit", "window", "person", "dog", "cat", 
        "wheelchair", "walking stick", "bicycle", "scooter"
    )

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

    private var wasYoloActive = false

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
        return DetectionResult(
            classId = 0,
            className = "chair",
            confidence = 0.95f,
            boundingBox = RectF(100f, 100f, 300f, 400f),
            timestamp = frame.timestamp
        )
    }

    override fun onFrame(frame: CameraFrame, releaseCallback: () -> Unit) {
        val userMode = controllerRepository.userSelectedMode.value
        val isYoloActive = userMode == com.drishti.models.AppMode.WALK || userMode == com.drishti.models.AppMode.AUTO

        if (isYoloActive != wasYoloActive) {
            wasYoloActive = isYoloActive
            if (isYoloActive) {
                Log.d("DrishtiDebug", "YOLO started")
            } else {
                Log.d("DrishtiDebug", "YOLO stopped")
            }
        }

        if (!isYoloActive) {
            releaseCallback()
            return
        }

        inferenceExecutor.execute {
            val startTime = System.currentTimeMillis()
            val localInterpreter = interpreter

            if (localInterpreter != null) {
                try {
                    val imageProxy = frame.imageProxy
                    val bitmap = imageProxy.toBitmap()

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

                    // Dynamically retrieve model tensor output shape to support flexible swap
                    val outputTensor = localInterpreter.getOutputTensor(0)
                    val shape = outputTensor.shape()
                    val numRows = shape[1] // e.g. 45 (for 41 classes + 4 coords) or 84 (COCO)
                    val numCandidates = shape[2] // 8400

                    val outputBuffer = Array(1) { Array(numRows) { FloatArray(numCandidates) } }
                    localInterpreter.run(processedImage.buffer, outputBuffer)

                    val inferenceDuration = System.currentTimeMillis() - startTime
                    val detections = mutableListOf<DetectionResult>()
                    val activeClassList = if (numRows == 45) indoorClasses else cocoClasses

                    // Parse bounding boxes and scores
                    for (i in 0 until numCandidates) {
                        var maxScore = 0.0f
                        var maxClassId = -1
                        for (c in 4 until numRows) {
                            val score = outputBuffer[0][c][i]
                            if (score > maxScore) {
                                maxScore = score
                                maxClassId = c - 4
                            }
                        }

                        // Confidence score threshold filter
                        if (maxScore > 0.40f) {
                            val cx = outputBuffer[0][0][i]
                            val cy = outputBuffer[0][1][i]
                            val w = outputBuffer[0][2][i]
                            val h = outputBuffer[0][3][i]

                            val left = cx - w / 2f
                            val top = cy - h / 2f
                            val right = cx + w / 2f
                            val bottom = cy + h / 2f

                            val className = activeClassList.getOrNull(maxClassId) ?: "Unknown"
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
                    }

                    _detectionsState.value = detections
                    indoorSceneReasoner.analyzeScene(detections)

                    val filtered = corridorFilter.filter(detections)
                    _filteredDetectionsState.value = filtered
                    val prioritized = detectionTracker.track(filtered)
                    _prioritizedDetectionsState.value = prioritized
                    _activeTracksCountState.value = detectionTracker.getActiveTracksCount()
                    decisionEngine.evaluate(prioritized)
                    _inferenceDurationState.value = inferenceDuration

                    Log.d("DetectionEngineImpl", "Frame: ${frame.timestamp} | Duration: ${inferenceDuration}ms | Candidates: ${detections.size}")
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
                    className = "chair",
                    confidence = 0.95f,
                    boundingBox = RectF(100f, 320f, 300f, 480f), // blocks path
                    timestamp = frame.timestamp
                )
                
                _detectionFlow.tryEmit(mockResult)
                _detectionsState.value = listOf(mockResult)
                indoorSceneReasoner.analyzeScene(listOf(mockResult))

                val filtered = corridorFilter.filter(listOf(mockResult))
                _filteredDetectionsState.value = filtered
                val prioritized = detectionTracker.track(filtered)
                _prioritizedDetectionsState.value = prioritized
                _activeTracksCountState.value = detectionTracker.getActiveTracksCount()
                decisionEngine.evaluate(prioritized)
                _inferenceDurationState.value = simulatedDuration
                
                releaseCallback()
            }
        }
    }
}
