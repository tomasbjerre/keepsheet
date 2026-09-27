package com.github.tomasbjerre.keepsheet.pdf

import com.github.tomasbjerre.keepsheet.data.PaperFormat
import kotlin.math.min

/**
 * Pure PDF page layout for specs/capture-and-processing.md#printer-friendly-pages — kept
 * free of Android/PdfDocument types so it's a plain unit test, not an instrumented one.
 * Sizes are in PDF points (1/72 inch), the unit PdfDocument.PageInfo itself uses.
 */
private const val A4_WIDTH_PT = 595
private const val A4_HEIGHT_PT = 842
private const val LETTER_WIDTH_PT = 612
private const val LETTER_HEIGHT_PT = 792

/** A 0.5in margin on every side — comfortably inside the unprintable border most consumer
 * printers have, while still leaving nearly all of the page for content. */
const val PAGE_MARGIN_PT = 36

private fun PaperFormat.portraitSize(): Pair<Int, Int> =
    when (this) {
        PaperFormat.A4 -> A4_WIDTH_PT to A4_HEIGHT_PT
        PaperFormat.LETTER -> LETTER_WIDTH_PT to LETTER_HEIGHT_PT
    }

/**
 * The PDF page size (width, height) for a page whose own content is
 * [contentWidth]x[contentHeight] — landscape content gets a landscape page, portrait
 * content a portrait page, rather than always forcing one orientation regardless of the
 * photo's own shape.
 */
fun PaperFormat.pageSize(
    contentWidth: Int,
    contentHeight: Int,
): Pair<Int, Int> {
    val (portraitWidth, portraitHeight) = portraitSize()
    return if (contentWidth > contentHeight) portraitHeight to portraitWidth else portraitWidth to portraitHeight
}

/** Where to draw [contentWidth]x[contentHeight] content within a [pageWidth]x[pageHeight]
 * page, inset by [marginPt] on every side. */
data class FitRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * Scales [contentWidth]x[contentHeight] to fit within a [pageWidth]x[pageHeight] page
 * minus [marginPt] on every side, preserving its own aspect ratio (never stretched or
 * cropped to fill the page) and centered within that printable area.
 */
fun fitContentRect(
    contentWidth: Int,
    contentHeight: Int,
    pageWidth: Int,
    pageHeight: Int,
    marginPt: Int = PAGE_MARGIN_PT,
): FitRect {
    val areaWidth = (pageWidth - 2 * marginPt).toFloat()
    val areaHeight = (pageHeight - 2 * marginPt).toFloat()
    val scale = min(areaWidth / contentWidth, areaHeight / contentHeight)
    val drawWidth = contentWidth * scale
    val drawHeight = contentHeight * scale
    val left = marginPt + (areaWidth - drawWidth) / 2
    val top = marginPt + (areaHeight - drawHeight) / 2
    return FitRect(left, top, drawWidth, drawHeight)
}
