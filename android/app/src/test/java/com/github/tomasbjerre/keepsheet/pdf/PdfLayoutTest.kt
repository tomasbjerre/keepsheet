package com.github.tomasbjerre.keepsheet.pdf

import com.github.tomasbjerre.keepsheet.data.PaperFormat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** See specs/capture-and-processing.md#printer-friendly-pages. */
class PdfLayoutTest {
    @Test
    fun `portrait content gets a portrait page`() {
        val (width, height) = PaperFormat.A4.pageSize(contentWidth = 600, contentHeight = 900)
        assertThat(width).isLessThan(height)
    }

    @Test
    fun `landscape content gets a landscape page, not a forced portrait one`() {
        val (width, height) = PaperFormat.A4.pageSize(contentWidth = 900, contentHeight = 600)
        assertThat(width).isGreaterThan(height)
    }

    @Test
    fun `square content defaults to portrait`() {
        val (width, height) = PaperFormat.A4.pageSize(contentWidth = 600, contentHeight = 600)
        assertThat(width).isLessThanOrEqualTo(height)
    }

    @Test
    fun `A4 and Letter portrait pages are the same fixed size regardless of content`() {
        val fromTall = PaperFormat.A4.pageSize(contentWidth = 100, contentHeight = 200)
        val fromTaller = PaperFormat.A4.pageSize(contentWidth = 300, contentHeight = 9000)
        assertThat(fromTall).isEqualTo(fromTaller)
    }

    @Test
    fun `content is centered and scaled to fit inside the margin, preserving aspect ratio`() {
        // A 2:1 content rect in a 200x200 page with a 10pt margin: the 180x180 printable
        // area is width-constrained (height would need 360), so it scales to 180x90 and
        // centers vertically.
        val fit =
            fitContentRect(contentWidth = 200, contentHeight = 100, pageWidth = 200, pageHeight = 200, marginPt = 10)

        assertThat(fit.width).isEqualTo(180f)
        assertThat(fit.height).isEqualTo(90f)
        assertThat(fit.left).isEqualTo(10f)
        assertThat(fit.top).isEqualTo(55f) // 10 margin + (180 - 90) / 2 to center in the printable area
    }

    @Test
    fun `content never gets stretched or cropped to fill the page`() {
        // A tall, narrow content rect in a wide page: height-constrained, so it stays
        // narrower than the full printable width rather than being stretched to fill it.
        val fit =
            fitContentRect(contentWidth = 100, contentHeight = 1000, pageWidth = 1000, pageHeight = 1000, marginPt = 0)

        assertThat(fit.height).isEqualTo(1000f)
        assertThat(fit.width).isEqualTo(100f)
        assertThat(fit.left).isEqualTo(450f) // centered: (1000 - 100) / 2
    }
}
