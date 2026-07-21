package com.drishti.detection

import com.drishti.models.Corridor
import com.drishti.models.DetectionResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorridorFilter @Inject constructor() {

    companion object {
        // Configurable corridor constants in the 640x640 model coordinate space
        const val TOP_CORRIDOR_WIDTH = 200f
        const val BOTTOM_CORRIDOR_WIDTH = 520f
        const val VERTICAL_START_POSITION = 280f
        const val VERTICAL_END_POSITION = 640f
        const val MINIMUM_OVERLAP_PERCENTAGE = 30.0f
    }

    val corridor = Corridor(
        topWidth = TOP_CORRIDOR_WIDTH,
        bottomWidth = BOTTOM_CORRIDOR_WIDTH,
        startY = VERTICAL_START_POSITION,
        endY = VERTICAL_END_POSITION
    )

    /**
     * Filters raw detections, returning only those intersecting the walking corridor.
     * Runs in O(n) and allocates zero temporary mathematical collections.
     */
    fun filter(detections: List<DetectionResult>): List<DetectionResult> {
        return detections.filter { detection ->
            corridor.intersects(detection.boundingBox, MINIMUM_OVERLAP_PERCENTAGE)
        }
    }
}
