package com.drishti.detection

import android.graphics.RectF
import com.drishti.models.DetectionResult
import com.drishti.models.PrioritizedDetection
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

@Singleton
class DetectionTracker @Inject constructor() {

    companion object {
        private const val EMA_ALPHA = 0.25f // Smoothing factor: higher = responsive, lower = smoother
        private const val MAX_MISSED_FRAMES = 5 // Expiration threshold (frames)
        private const val MAX_ASSOCIATION_DISTANCE = 120f // Center distance threshold in model space

        // Configurable threat scoring weights
        private const val WEIGHT_DISTANCE = 0.5f
        private const val WEIGHT_AREA = 0.2f
        private const val WEIGHT_CONFIDENCE = 0.1f
        private const val WEIGHT_MOTION = 0.2f
    }

    private val activeTracks = mutableListOf<TrackedObject>()
    private var nextTrackId = 1

    /**
     * Associates detections with active tracks, computes distance, threat score, 
     * and sorts objects by threat score descending.
     */
    @Synchronized
    fun track(detections: List<DetectionResult>): List<PrioritizedDetection> {
        val matchedTracks = mutableSetOf<TrackedObject>()
        val prioritizedList = mutableListOf<PrioritizedDetection>()

        // 1. Associate each detection to an active track
        detections.forEach { detection ->
            val rect = detection.boundingBox
            val cx = rect.centerX()
            val cy = rect.centerY()

            var bestTrack: TrackedObject? = null
            var minDistance = Float.MAX_VALUE

            activeTracks.forEach { track ->
                if (track.classId == detection.classId && track !in matchedTracks) {
                    val trackCx = track.lastBoundingBox.centerX()
                    val trackCy = track.lastBoundingBox.centerY()
                    val dist = sqrt((cx - trackCx).pow(2) + (cy - trackCy).pow(2))
                    if (dist < minDistance && dist < MAX_ASSOCIATION_DISTANCE) {
                        minDistance = dist
                        bestTrack = track
                    }
                }
            }

            val rawDistance = estimateDistanceScore(rect)

            val track = bestTrack
            if (track != null) {
                matchedTracks.add(track)
                track.missedFramesCount = 0
                track.lastBoundingBox = rect
                track.lastConfidence = detection.confidence

                // Approaching rate (positive indicates object is moving closer)
                val currentDistance = rawDistance
                val approachingRate = currentDistance - track.smoothedDistance
                track.approachingRate = approachingRate

                // Apply Exponential Moving Average (EMA) to smooth distance
                track.smoothedDistance = (EMA_ALPHA * currentDistance) + ((1f - EMA_ALPHA) * track.smoothedDistance)
                track.previousDistance = currentDistance
            } else {
                // Instantiate a new track sequence
                val newTrack = TrackedObject(
                    trackId = nextTrackId++,
                    classId = detection.classId,
                    className = detection.className,
                    lastBoundingBox = rect,
                    smoothedDistance = rawDistance,
                    previousDistance = rawDistance,
                    lastConfidence = detection.confidence
                )
                activeTracks.add(newTrack)
                matchedTracks.add(newTrack)
            }
        }

        // 2. Age out unmatched tracks and purge expired ones
        val iterator = activeTracks.iterator()
        while (iterator.hasNext()) {
            val track = iterator.next()
            if (track !in matchedTracks) {
                track.missedFramesCount++
                if (track.missedFramesCount > MAX_MISSED_FRAMES) {
                    iterator.remove()
                } else {
                    track.approachingRate = 0f
                }
            }
        }

        // 3. Compute threat score and create prioritized result
        activeTracks.forEach { track ->
            if (track in matchedTracks) {
                val threat = computeThreat(track)
                prioritizedList.add(
                    PrioritizedDetection(
                        trackId = track.trackId,
                        classId = track.classId,
                        className = track.className,
                        confidence = track.lastConfidence,
                        boundingBox = track.lastBoundingBox,
                        distance = track.smoothedDistance,
                        threatScore = threat,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        // 4. Sort: Highest threat score first
        prioritizedList.sortByDescending { it.threatScore }
        return prioritizedList
    }

    /**
     * Estimates distance geometrically using vertical screen coordinates and box scale.
     * Scale goes from 0.0 (extremely far) to 1.0 (extremely near).
     */
    private fun estimateDistanceScore(rect: RectF): Float {
        // Vertical corridor span is [280..640]
        val cy = rect.centerY().coerceIn(280f, 640f)
        val yDistance = (cy - 280f) / (640f - 280f)

        // Box scale relative to 40% of standard model input area
        val area = rect.width() * rect.height()
        val maxExpectedArea = 640f * 640f * 0.4f
        val areaDistance = (area / maxExpectedArea).coerceIn(0f, 1f)

        // Cues weight split: Y-height (60%) and box size (40%)
        return (0.6f * yDistance) + (0.4f * areaDistance)
    }

    /**
     * Computes a continuous threat score based on distance, area, confidence, and motion.
     */
    private fun computeThreat(track: TrackedObject): Float {
        val dist = track.smoothedDistance
        
        val area = track.lastBoundingBox.width() * track.lastBoundingBox.height()
        val maxExpectedArea = 640f * 640f * 0.4f
        val areaDistance = (area / maxExpectedArea).coerceIn(0f, 1f)

        val conf = track.lastConfidence
        
        val motionFactor = track.approachingRate.coerceAtLeast(0f) * 12f
        val motionScore = motionFactor.coerceIn(0f, 1f)

        val rawScore = (WEIGHT_DISTANCE * dist) +
                       (WEIGHT_AREA * areaDistance) +
                       (WEIGHT_CONFIDENCE * conf) +
                       (WEIGHT_MOTION * motionScore)

        return rawScore.coerceIn(0f, 1f)
    }

    @Synchronized
    fun getActiveTracksCount(): Int = activeTracks.size

    @Synchronized
    fun getAverageDistance(): Float {
        val visibleTracks = activeTracks.filter { it.missedFramesCount == 0 }
        if (visibleTracks.isEmpty()) return 0f
        return visibleTracks.map { it.smoothedDistance }.average().toFloat()
    }

    @Synchronized
    fun getHighestThreatScore(prioritized: List<PrioritizedDetection>): Float {
        if (prioritized.isEmpty()) return 0f
        return prioritized.maxOf { it.threatScore }
    }

    private data class TrackedObject(
        val trackId: Int,
        val classId: Int,
        val className: String,
        var lastBoundingBox: RectF,
        var smoothedDistance: Float,
        var previousDistance: Float,
        var lastConfidence: Float,
        var approachingRate: Float = 0f,
        var missedFramesCount: Int = 0
    )
}
