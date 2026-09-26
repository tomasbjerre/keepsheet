package com.github.tomasbjerre.keepsheet.ocr

/**
 * On-device text recognition for one page image — see
 * specs/capture-and-processing.md#text-recognition-ocr. Returns null when nothing (or
 * nothing usable) was recognized; never throws for "no text found".
 */
fun interface TextRecognizer {
    fun recognize(imagePath: String): String?
}
