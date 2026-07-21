package com.drishti.models

enum class ThreatLevel {
    SAFE,
    CAUTION,
    WARNING,
    CRITICAL
}

data class NavigationDecision(
    val trackId: Int,
    val className: String,
    val threatLevel: ThreatLevel,
    val threatScore: Float,
    val distanceScore: Float,
    val relativeDirection: String, // "left", "center", "right"
    val reason: String,
    val timestamp: Long
)
