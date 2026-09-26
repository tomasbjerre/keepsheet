package com.github.tomasbjerre.keepsheet.naming

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.TimeZone

/** See specs/file-naming.md#fields. */
class TextFieldsTest {
    private val utc = TimeZone.getTimeZone("UTC")

    // 2026-09-26T12:00:00Z
    private val now = 1790424000000L

    private val invoice =
        """
        Blekinge Bygg AB
        Storgatan 5
        Faktura
        Fakturadatum 2026-09-25
        Förfallodatum 2026-10-25
        """.trimIndent()

    @Test
    fun `builds the full suggestion from an invoice`() {
        assertThat(suggestedDocumentName(invoice, now, emptyList(), utc))
            .isEqualTo("2026-09-25_Invoice_BlekingeBygg")
    }

    @Test
    fun `falls back per field when nothing is recognized`() {
        assertThat(suggestedDocumentName("", now, emptyList(), utc)).isEqualTo("2026-09-26_Document_Untitled")
        assertThat(suggestedDocumentName("1234 5678 9", now, emptyList(), utc))
            .isEqualTo("2026-09-26_Document_Untitled")
    }

    @Test
    fun `adds a numeric suffix on collision`() {
        val taken = listOf("2026-09-25_Invoice_BlekingeBygg")
        assertThat(suggestedDocumentName(invoice, now, taken, utc)).isEqualTo("2026-09-25_Invoice_BlekingeBygg (2)")
    }

    @Test
    fun `finds dates in numeric and worded formats`() {
        assertThat(findDate("Datum: 25.09.2026", now, utc)).isEqualTo("2026-09-25")
        assertThat(findDate("Stockholm den 3 september 2026", now, utc)).isEqualTo("2026-09-03")
        assertThat(findDate("Paid on 12 Jan 2026", now, utc)).isEqualTo("2026-01-12")
    }

    @Test
    fun `ignores impossible dates and dates far in the future`() {
        assertThat(findDate("2026-13-45", now, utc)).isNull()
        assertThat(findDate("Due 2027-01-01", now, utc)).isNull()
    }

    @Test
    fun `picks the type appearing first, matching Swedish and English keywords`() {
        assertThat(findDocumentType("KVITTO\nAvtal om ...")).isEqualTo("Receipt")
        assertThat(findDocumentType("Employment Agreement")).isEqualTo("Contract")
        assertThat(findDocumentType("Just some text")).isNull()
    }

    @Test
    fun `sender name is stripped of spaces, punctuation and company suffix`() {
        assertThat(findSenderName("ICA Maxi AB\nKvitto")).isEqualTo("ICAMaxi")
        assertThat(findSenderName("Kvitto\nCoop Extra")).isEqualTo("CoopExtra")
        assertThat(findSenderName("2026-09-25\n12345")).isNull()
    }
}
