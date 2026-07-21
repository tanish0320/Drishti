package com.drishti.ocr

import com.drishti.models.OCRBlock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingOrderResolver @Inject constructor() {

    /**
     * Resolves the natural reading sequence of text blocks.
     * Sorts primarily top-to-bottom, and secondarily left-to-right.
     */
    fun resolve(blocks: List<OCRBlock>): List<OCRBlock> {
        return blocks.sortedWith(
            compareBy<OCRBlock> { it.boundingBox?.top ?: 0 }
                .thenBy { it.boundingBox?.left ?: 0 }
        )
    }
}
