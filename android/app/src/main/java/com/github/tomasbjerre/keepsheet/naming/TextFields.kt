package com.github.tomasbjerre.keepsheet.naming

import java.util.Calendar
import java.util.TimeZone

/** Keyword (lowercase, Swedish + English) → category word — see specs/file-naming.md#fields. */
private val TYPE_KEYWORDS =
    listOf(
        "Invoice" to listOf("faktura", "invoice"),
        "Receipt" to listOf("kvitto", "receipt"),
        "Contract" to listOf("avtal", "kontrakt", "contract", "agreement"),
        "Letter" to listOf("brev", "letter", "dear "),
    )

/** The category whose keyword appears earliest in [text] (headings come first), if any. */
fun findDocumentType(text: String): String? {
    val lower = text.lowercase()
    return TYPE_KEYWORDS
        .mapNotNull { (type, words) ->
            val position = words.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
            position?.let { type to it }
        }.minByOrNull { it.second }
        ?.first
}

private val MONTHS =
    mapOf(
        "januari" to 1,
        "january" to 1,
        "jan" to 1,
        "februari" to 2,
        "february" to 2,
        "feb" to 2,
        "mars" to 3,
        "march" to 3,
        "mar" to 3,
        "april" to 4,
        "apr" to 4,
        "maj" to 5,
        "may" to 5,
        "juni" to 6,
        "june" to 6,
        "jun" to 6,
        "juli" to 7,
        "july" to 7,
        "jul" to 7,
        "augusti" to 8,
        "august" to 8,
        "aug" to 8,
        "september" to 9,
        "sep" to 9,
        "sept" to 9,
        "oktober" to 10,
        "october" to 10,
        "okt" to 10,
        "oct" to 10,
        "november" to 11,
        "nov" to 11,
        "december" to 12,
        "dec" to 12,
    )

private val ISO_DATE = Regex("""\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b""")
private val DAY_FIRST_DATE = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](20\d{2})\b""")
private val WORD_DATE = Regex("""\b(\d{1,2})\s+([A-Za-zåäöÅÄÖ]{3,9})\.?,?\s+(20\d{2})\b""")

/**
 * The first plausible `yyyy-MM-dd` found in [text] (ISO, day-first numeric, or
 * "25 september 2026" style). "Reasonable confidence": a real calendar date, and not more
 * than a day after [finalizedAt] — a due date in the future isn't the document's date.
 */
fun findDate(
    text: String,
    finalizedAt: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): String? {
    val candidates = mutableListOf<DateCandidate>()
    ISO_DATE.findAll(text).forEach { candidates += DateCandidate(it.range.first, it.g(1), it.g(2), it.g(3)) }
    DAY_FIRST_DATE.findAll(text).forEach { candidates += DateCandidate(it.range.first, it.g(3), it.g(2), it.g(1)) }
    WORD_DATE.findAll(text).forEach {
        val month = MONTHS[it.groupValues[2].lowercase()]
        if (month != null) candidates += DateCandidate(it.range.first, it.g(3), month, it.g(1))
    }
    return candidates
        .sortedBy { it.position }
        .firstNotNullOfOrNull { validDate(it.year, it.month, it.day, finalizedAt, timeZone) }
}

private data class DateCandidate(
    val position: Int,
    val year: Int,
    val month: Int,
    val day: Int,
)

private fun MatchResult.g(index: Int) = groupValues[index].toInt()

private fun validDate(
    year: Int,
    month: Int,
    day: Int,
    finalizedAt: Long,
    timeZone: TimeZone,
): String? {
    val calendar = Calendar.getInstance(timeZone).apply { isLenient = false }
    val valid =
        runCatching {
            calendar.clear()
            calendar.set(year, month - 1, day)
            calendar.timeInMillis
        }.getOrNull()
    val plausible = valid != null && valid <= finalizedAt + DAY_MILLIS
    return if (plausible) "%04d-%02d-%02d".format(year, month, day) else null
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
private const val MAX_NAME_LENGTH = 30
private const val LINES_TO_SCAN = 10

private val COMPANY_SUFFIX = Regex("""\s+(AB|HB|KB|Ltd|Inc|GmbH|LLC|Oy|AS|ApS)\.?$""", RegexOption.IGNORE_CASE)
private val LONG_DIGITS = Regex("""\d{3,}""")
private val NAME_LINE = Regex("""^[\p{L}][\p{L}\p{N} &.\-']{1,60}$""")

/**
 * A best-effort sender/company label: the first of the top lines that looks like a name
 * (letters, no digits-heavy noise, not merely a document-type word), preferring one with a
 * company suffix. Spaces/punctuation are stripped and any company suffix dropped, so
 * "Blekinge Bygg AB" → "BlekingeBygg".
 */
fun findSenderName(text: String): String? {
    val lines =
        text
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(LINES_TO_SCAN)
            .filter { NAME_LINE.matches(it) && !LONG_DIGITS.containsMatchIn(it) }
    val chosen =
        lines.firstOrNull { COMPANY_SUFFIX.containsMatchIn(it) }
            ?: lines.firstOrNull { !mentionsDocumentType(it) }
    val cleaned =
        chosen
            ?.replace(COMPANY_SUFFIX, "")
            ?.filter { it.isLetterOrDigit() }
            ?.take(MAX_NAME_LENGTH)
    return cleaned?.ifEmpty { null }
}

private fun mentionsDocumentType(line: String): Boolean {
    val lower = line.lowercase()
    return TYPE_KEYWORDS.any { (_, words) -> words.any { lower.contains(it.trim()) } }
}
