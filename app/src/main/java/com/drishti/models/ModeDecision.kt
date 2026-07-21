package com.drishti.models

data class ModeDecision(
    val recommendedMode: AppMode,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ModeHistory(
    val previousMode: AppMode,
    val currentMode: AppMode,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)
