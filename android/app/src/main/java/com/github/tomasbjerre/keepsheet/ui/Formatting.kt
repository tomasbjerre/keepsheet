package com.github.tomasbjerre.keepsheet.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** See specs/ui-flows.md#1-home — each document row shows a human-readable size. */
fun formatFileSize(bytes: Long): String {
    val kilobytes = bytes / KILOBYTE.toDouble()
    val megabytes = kilobytes / KILOBYTE.toDouble()
    return when {
        bytes < KILOBYTE -> "$bytes B"
        kilobytes < KILOBYTE -> String.format(Locale.US, "%.0f KB", kilobytes)
        else -> String.format(Locale.US, "%.1f MB", megabytes)
    }
}

/**
 * See specs/ui-flows.md#1-home — each document row shows its date, in the
 * device's local time zone (like specs/export.md's on-screen timestamps,
 * as opposed to the UTC times inside an exported CSV).
 *
 * A fresh SimpleDateFormat per call, not a shared instance: SimpleDateFormat
 * isn't thread-safe, and this can be called from multiple coroutines/recompositions.
 */
fun formatDate(
    epochMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    format.timeZone = timeZone
    return format.format(Date(epochMillis))
}

private const val KILOBYTE = 1024L
