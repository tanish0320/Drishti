package com.drishti.ui

import androidx.lifecycle.ViewModel
import com.drishti.permissions.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val permissionManager: PermissionManager
) : ViewModel() {

    val permissionsGranted: StateFlow<Boolean> = permissionManager.permissionsGranted

    fun checkPermissions() {
        permissionManager.updatePermissionState()
    }
}
