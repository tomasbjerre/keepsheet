package com.github.tomasbjerre.keepsheet.naming

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.TimeZone

class SuggestedNameTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `falls back to finalize date, Document, Untitled`() {
        // 2026-09-25T12:00:00Z
        assertThat(fallbackDocumentName(1790337600000L, existingNames = emptyList(), timeZone = utc))
            .isEqualTo("2026-09-25_Document_Untitled")
    }

    @Test
    fun `appends a numeric suffix when the name already exists`() {
        val existing = listOf("2026-09-25_Document_Untitled")

        assertThat(fallbackDocumentName(1790337600000L, existing, utc)).isEqualTo("2026-09-25_Document_Untitled (2)")
    }

    @Test
    fun `keeps incrementing the suffix until it finds a free name`() {
        val existing =
            listOf(
                "2026-09-25_Document_Untitled",
                "2026-09-25_Document_Untitled (2)",
                "2026-09-25_Document_Untitled (3)",
            )

        assertThat(fallbackDocumentName(1790337600000L, existing, utc)).isEqualTo("2026-09-25_Document_Untitled (4)")
    }
}
