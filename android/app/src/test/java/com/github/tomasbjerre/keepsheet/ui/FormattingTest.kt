package com.github.tomasbjerre.keepsheet.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.TimeZone

class FormattingTest {
    @Test
    fun `formats bytes under one kilobyte as bytes`() {
        assertThat(formatFileSize(512)).isEqualTo("512 B")
    }

    @Test
    fun `formats kilobytes with no decimal`() {
        assertThat(formatFileSize(150 * 1024L)).isEqualTo("150 KB")
    }

    @Test
    fun `formats megabytes with one decimal`() {
        assertThat(formatFileSize(2 * 1024 * 1024L + 512 * 1024L)).isEqualTo("2.5 MB")
    }

    @Test
    fun `formats an epoch millis timestamp as yyyy-MM-dd in the given time zone`() {
        // 2026-09-25T12:00:00Z
        assertThat(formatDate(1790337600000L, TimeZone.getTimeZone("UTC"))).isEqualTo("2026-09-25")
    }
}
