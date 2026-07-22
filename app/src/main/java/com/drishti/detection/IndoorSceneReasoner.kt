package com.drishti.detection

import android.graphics.RectF
import com.drishti.models.DetectionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ObjectCategory {
    DYNAMIC_OBSTACLE,
    STATIC_FURNITURE,
    STRUCTURAL_ELEMENT,
    SAFE_LANDMARK,
    IGNORED
}

data class SceneAnalysis(
    val roomType: String,
    val openPathways: List<String>, // "left", "center", "right"
    val blockedRegions: List<RectF>,
    val bestSideToPass: String, // "left", "right", "center"
    val summary: String
)

@Singleton
class IndoorSceneReasoner @Inject constructor() {

    private val _sceneAnalysisState = MutableStateFlow<SceneAnalysis>(
        SceneAnalysis("Unknown Room", listOf("left", "center", "right"), emptyList(), "center", "Walking corridor is clear.")
    )
    val sceneAnalysisState: StateFlow<SceneAnalysis> = _sceneAnalysisState.asStateFlow()

    fun getCategory(className: String): ObjectCategory {
        return when (className.lowercase()) {
            "person", "dog", "cat", "wheelchair", "walking stick", "bicycle", "scooter" -> ObjectCategory.DYNAMIC_OBSTACLE
            "chair", "sofa", "bed", "dining table", "coffee table", "desk", "cabinet", "bookshelf", "tv stand", "nightstand", "wardrobe", "shelf", "drawer" -> ObjectCategory.STATIC_FURNITURE
            "door", "doorway", "stairs", "handrail", "corridor", "elevator", "escalator", "ramp", "fire exit", "window" -> ObjectCategory.STRUCTURAL_ELEMENT
            else -> ObjectCategory.IGNORED
        }
    }

    fun isSafeLandmark(className: String): Boolean {
        return when (className.lowercase()) {
            "doorway", "corridor", "handrail", "fire exit", "ramp" -> true
            else -> false
        }
    }

    fun analyzeScene(detections: List<DetectionResult>) {
        if (detections.isEmpty()) {
            _sceneAnalysisState.value = SceneAnalysis(
                roomType = "Clear Space",
                openPathways = listOf("left", "center", "right"),
                blockedRegions = emptyList(),
                bestSideToPass = "center",
                summary = "Walking corridor is clear."
            )
            return
        }

        // 1. Infer Room Type
        val classNames = detections.map { it.className.lowercase() }.toSet()
        val roomType = when {
            classNames.contains("bed") || classNames.contains("wardrobe") || classNames.contains("nightstand") -> "Bedroom"
            classNames.contains("sofa") || classNames.contains("tv stand") || classNames.contains("coffee table") -> "Living Room"
            classNames.contains("dining table") -> "Dining Area"
            classNames.contains("refrigerator") || classNames.contains("microwave") || classNames.contains("kitchen counter") || classNames.contains("stove") || classNames.contains("sink") -> "Kitchen"
            classNames.contains("bookshelf") || (classNames.contains("desk") && classNames.contains("cabinet")) -> "Study"
            classNames.contains("stairs") || classNames.contains("handrail") || classNames.contains("elevator") || classNames.contains("escalator") -> "Transition Area"
            else -> "Unknown Room"
        }

        // 2. Free Space / Lane Blockage Analysis
        // Split view horizontally into 3 lanes in normalized space [0.0..640.0]
        val laneWidth = 640f / 3f
        var leftBlocked = false
        var centerBlocked = false
        var rightBlocked = false
        val blockedList = mutableListOf<RectF>()

        // Consider close obstacles: objects blocking bottom half of the image (ymin > 300f)
        val closeDetections = detections.filter { det ->
            val box = det.boundingBox
            (getCategory(det.className) == ObjectCategory.DYNAMIC_OBSTACLE || 
             getCategory(det.className) == ObjectCategory.STATIC_FURNITURE) &&
             box.bottom > 300f
        }

        closeDetections.forEach { det ->
            val box = det.boundingBox
            blockedList.add(box)
            
            // Check lane overlaps
            if (box.left < laneWidth) leftBlocked = true
            if (box.right > 2 * laneWidth) rightBlocked = true
            if (box.left < 2 * laneWidth && box.right > laneWidth) centerBlocked = true
        }

        // 3. Determine best side to pass
        val openLanes = mutableListOf<String>()
        if (!leftBlocked) openLanes.add("left")
        if (!centerBlocked) openLanes.add("center")
        if (!rightBlocked) openLanes.add("right")

        val bestSide = when {
            !centerBlocked -> "center"
            !leftBlocked && !rightBlocked -> "left"
            !leftBlocked -> "left"
            !rightBlocked -> "right"
            else -> "center" // all lanes blocked
        }

        // 4. Summarize intelligently
        val summary = StringBuilder()
        if (roomType != "Unknown Room") {
            summary.append("You are entering a ${roomType.lowercase()}. ")
        } else {
            summary.append("Entering new space. ")
        }

        if (closeDetections.isNotEmpty()) {
            val primaryObstacle = closeDetections.maxByOrNull { it.boundingBox.height() * it.confidence }
            if (primaryObstacle != null) {
                val name = primaryObstacle.className
                summary.append("$name ahead. ")
                when (bestSide) {
                    "left" -> summary.append("Walk slightly left.")
                    "right" -> summary.append("Pass on the right.")
                    else -> summary.append("Navigate carefully.")
                }
            }
        } else {
            val landmarks = detections.filter { isSafeLandmark(it.className) }
            if (landmarks.isNotEmpty()) {
                summary.append("Safe pathway ahead through ${landmarks.first().className}.")
            } else {
                summary.append("The path is clear.")
            }
        }

        _sceneAnalysisState.value = SceneAnalysis(
            roomType = roomType,
            openPathways = openLanes,
            blockedRegions = blockedList,
            bestSideToPass = bestSide,
            summary = summary.toString()
        )
    }
}
