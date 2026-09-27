package com.github.tomasbjerre.keepsheet.data

import android.content.Context

private const val PREFS_NAME = "keepsheet_prefs"
private const val KEY_PAPER_FORMAT = "paper_format"

/**
 * Persists the chosen page size (specs/capture-and-processing.md#printer-friendly-pages)
 * across a fresh app start — the one piece of state KeepSheet keeps that isn't cleared the
 * way documents are (see specs/data-model.md#document-lifetime): a UI preference, not
 * document content (specs/permissions-and-privacy.md#data-handling).
 */
class PaperFormatPreference(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var format: PaperFormat
        get() = PaperFormat.entries.firstOrNull { it.name == prefs.getString(KEY_PAPER_FORMAT, null) } ?: PaperFormat.A4
        set(value) = prefs.edit().putString(KEY_PAPER_FORMAT, value.name).apply()
}
