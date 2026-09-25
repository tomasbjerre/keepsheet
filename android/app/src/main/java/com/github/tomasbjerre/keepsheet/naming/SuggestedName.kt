package com.github.tomasbjerre.keepsheet.naming

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * See specs/file-naming.md#rules. Text recognition
 * (specs/capture-and-processing.md#text-recognition-ocr) isn't implemented
 * yet, so there's no recognized date/type/name to suggest from — every
 * document currently gets the documented fallback: finalize date +
 * "Document" + "Untitled". Once OCR exists, a real suggestion replaces
 * this fallback the same way specs/file-naming.md#rules describes a
 * silent rename once recognition finishes.
 */
fun fallbackDocumentName(
    finalizedAt: Long,
    existingNames: Collection<String>,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    format.timeZone = timeZone
    val base = sanitizeForFileName("${format.format(Date(finalizedAt))}_Document_Untitled")
    return uniqueName(base, existingNames)
}

/** See specs/file-naming.md#rules — "(2)", "(3)", ... appended on collision. */
private fun uniqueName(
    base: String,
    existingNames: Collection<String>,
): String {
    if (base !in existingNames) return base
    var suffix = 2
    while ("$base ($suffix)" in existingNames) {
        suffix++
    }
    return "$base ($suffix)"
}
