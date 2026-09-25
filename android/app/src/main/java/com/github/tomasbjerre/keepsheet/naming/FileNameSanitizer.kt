package com.github.tomasbjerre.keepsheet.naming

/**
 * See specs/file-naming.md#rules: "Characters illegal in file names on
 * common platforms are stripped from every field before assembly." Covers
 * Windows' reserved set (the most restrictive of the platforms KeepSheet's
 * suggested names need to survive being shared to/from) plus control
 * characters, and trims the result so stripping never leaves a dangling
 * separator at either end.
 */
fun sanitizeForFileName(input: String): String =
    input
        .replace(ILLEGAL_CHARACTERS, "")
        .trim()

private val ILLEGAL_CHARACTERS = Regex("[<>:\"/\\\\|?*\\x00-\\x1F]")
