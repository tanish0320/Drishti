package com.drishti.ocr

import android.graphics.Rect
import com.drishti.models.OCRBlock
import com.google.mlkit.vision.text.Text
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextBlockAnalyzer @Inject constructor() {

    /**
     * Extracts and filters text blocks from the raw ML Kit Text result.
     * Removes empty strings and text blocks smaller than the configurable minTextSizePx.
     */
    fun analyze(
        textResult: Text,
        minTextSizePx: Float,
        timestamp: Long
    ): List<OCRBlock> {
        val blocksList = mutableListOf<OCRBlock>()

        for (block in textResult.textBlocks) {
            val text = block.text.trim()
            if (text.isEmpty()) continue

            val box = block.boundingBox
            if (box != null) {
                // Filter blocks based on minimum physical dimensions in frame pixels
                if (box.height() < minTextSizePx || box.width() < minTextSizePx) {
                    continue
                }
            }

            blocksList.add(
                OCRBlock(
                    text = text,
                    boundingBox = box,
                    confidence = 1.0f,
                    timestamp = timestamp
                )
            )
        }

        return blocksList
    }
}
