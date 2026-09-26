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

/** See specs/file-naming.md#rules — "(2)", "(3)", ... appended on collision. Also used to
 * de-duplicate a user-entered rename (see [com.github.tomasbjerre.keepsheet.data.renameDocument]),
 * not just an auto-suggested name. */
fun uniqueName(
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

/**
 * See specs/file-naming.md: `<date>_<type>_<name>` from recognized page [text], each field
 * falling back independently (finalize date / `Document` / `Untitled`) when nothing usable
 * is found. [existingNames] excludes the document being named.
 */
fun suggestedDocumentName(
    text: String,
    finalizedAt: Long,
    existingNames: Collection<String>,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    format.timeZone = timeZone
    val date = findDate(text, finalizedAt, timeZone) ?: format.format(Date(finalizedAt))
    val type = findDocumentType(text) ?: "Document"
    val name = findSenderName(text) ?: "Untitled"
    return uniqueName(sanitizeForFileName("${date}_${type}_$name"), existingNames)
}
