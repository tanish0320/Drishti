package com.drishti.repository

import com.drishti.ocr.OCRProcessor
import com.drishti.models.CameraFrame
import com.drishti.models.OCRResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OCRRepository @Inject constructor(
    private val ocrProcessor: OCRProcessor
) {
    @Suppress("UNUSED_PARAMETER")
    fun performOcr(frameStream: Flow<CameraFrame>): Flow<OCRResult> {
        return ocrProcessor.startOcr()
    }
}
