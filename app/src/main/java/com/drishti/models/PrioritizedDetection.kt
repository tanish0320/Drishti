package com.drishti.models

import android.graphics.RectF

data class PrioritizedDetection(
    val trackId: Int,
    val classId: Int,
    val className: String,
    val confidence: Float,
    val boundingBox: RectF,
    val distance: Float, // Estimated distance score: 0.0 (far) to 1.0 (near)
    val threatScore: Float, // Computed threat score: 0.0 (low) to 1.0 (high)
    val timestamp: Long
)
