package com.drishti.controller

import com.drishti.models.AppMode
import kotlinx.coroutines.flow.StateFlow

interface ModeControllerInterface {
    val userSelectedModeState: StateFlow<AppMode>
    val effectiveModeState: StateFlow<AppMode>
    
    fun setMode(mode: AppMode)
    fun getCurrentMode(): AppMode
    fun switchMode()
}
