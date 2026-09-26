package com.github.tomasbjerre.keepsheet.ocr

/** Languages with trained data bundled under `assets/tessdata` (ISO 639-1 → Tesseract code). */
private val BUNDLED = mapOf("en" to "eng", "sv" to "swe")

/**
 * See specs/capture-and-processing.md#text-recognition-ocr: recognize the device's
 * configured language(s) where supported. Unsupported languages are skipped; if none of
 * the device languages is supported, English is used so a document still gets a best-effort
 * attempt (Latin script mostly recognizes anyway).
 */
fun tesseractLanguages(deviceLanguages: List<String>): String {
    val codes = deviceLanguages.mapNotNull { BUNDLED[it.lowercase().take(2)] }.distinct()
    return codes.ifEmpty { listOf("eng") }.joinToString("+")
}

/** The bundled traineddata files a [tesseractLanguages] result needs. */
fun bundledLanguageCodes(): Collection<String> = BUNDLED.values
