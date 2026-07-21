package com.drishti.controller

import com.drishti.models.AppMode
import kotlinx.coroutines.flow.StateFlow

interface ModeControllerInterface {
    val currentModeState: StateFlow<AppMode>
    
    fun setMode(mode: AppMode)
    fun getCurrentMode(): AppMode
    fun switchMode()
}
