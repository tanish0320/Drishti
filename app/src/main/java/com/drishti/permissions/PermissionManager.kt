package com.drishti.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _permissionsGranted = MutableStateFlow(checkPermissions())
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    fun checkPermissions(): Boolean {
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // We count permissions as granted if both camera and microphone are approved
        return cameraGranted && micGranted
    }

    fun updatePermissionState() {
        _permissionsGranted.value = checkPermissions()
    }
}
