package com.drishti.utils

sealed class AppError {
    object CameraUnavailable : AppError()
    object PermissionDenied : AppError()
    object ModelLoadFailed : AppError()
    object OCRUnavailable : AppError()
    object Unknown : AppError()
}
