package com.drishti.repository

import com.drishti.controller.ModeControllerInterface
import com.drishti.models.AppMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ControllerRepository @Inject constructor(
    private val modeController: ModeControllerInterface
) {
    val currentMode: StateFlow<AppMode> = modeController.currentModeState

    fun setAppMode(mode: AppMode) {
        modeController.setMode(mode)
    }

    fun toggleMode() {
        modeController.switchMode()
    }
}
