package com.drishti.ocr

import com.drishti.models.CameraFrame
import com.drishti.models.OCRResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface OCRProcessor {
    val ocrResultsState: StateFlow<OCRResult>
    val recognizedTextState: StateFlow<String>
    val ocrFpsState: StateFlow<Float>
    val ocrLatencyState: StateFlow<Long>
    val charactersRecognizedState: StateFlow<Int>
    val lastRecognizedStringState: StateFlow<String>

    fun startOcr(): Flow<OCRResult>
    fun stopOcr()
    fun processFrame(frame: CameraFrame): OCRResult
}
