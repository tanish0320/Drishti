package com.drishti.di

import com.drishti.camera.FrameProvider
import com.drishti.camera.FrameProviderImpl
import com.drishti.controller.ModeControllerImpl
import com.drishti.controller.ModeControllerInterface
import com.drishti.detection.DetectionEngine
import com.drishti.detection.DetectionEngineImpl
import com.drishti.haptics.HapticEngine
import com.drishti.haptics.HapticEngineImpl
import com.drishti.ocr.OCRProcessor
import com.drishti.ocr.OCRProcessorImpl
import com.drishti.speech.SpeechEngine
import com.drishti.speech.SpeechEngineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AppModule {

    @Binds
    @Singleton
    fun bindFrameProvider(impl: FrameProviderImpl): FrameProvider

    @Binds
    @Singleton
    fun bindModeController(impl: ModeControllerImpl): ModeControllerInterface

    @Binds
    @Singleton
    fun bindDetectionEngine(impl: DetectionEngineImpl): DetectionEngine

    @Binds
    @Singleton
    fun bindOCRProcessor(impl: OCRProcessorImpl): OCRProcessor

    @Binds
    @Singleton
    fun bindSpeechEngine(impl: SpeechEngineImpl): SpeechEngine

    @Binds
    @Singleton
    fun bindHapticEngine(impl: HapticEngineImpl): HapticEngine
}
