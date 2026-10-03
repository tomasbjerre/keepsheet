package com.github.tomasbjerre.keepsheet.pdf

import com.github.tomasbjerre.keepsheet.data.PageFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** See specs/capture-and-processing.md#document-filters. */
class PageFiltersTest {
    private val red = 0xFFFF0000.toInt()
    private val lightGray = 0xFFC8C8C8.toInt()
    private val darkGray = 0xFF303030.toInt()

    @Test
    fun `color leaves pixels untouched`() {
        val pixels = intArrayOf(red, lightGray)
        applyFilter(pixels, width = 2, height = 1, PageFilter.COLOR)
        assertThat(pixels).containsExactly(red, lightGray)
    }

    @Test
    fun `grayscale makes every channel equal and keeps shading`() {
        val pixels = intArrayOf(red, lightGray)
        applyFilter(pixels, width = 2, height = 1, PageFilter.GRAYSCALE)
        pixels.forEach {
            assertThat((it shr 16) and 0xFF).isEqualTo((it shr 8) and 0xFF).isEqualTo(it and 0xFF)
        }
        assertThat(pixels[1]).isEqualTo(lightGray)
    }

    @Test
    fun `black and white yields only pure black and white`() {
        val pixels = intArrayOf(lightGray, darkGray, lightGray, darkGray, red)
        applyFilter(pixels, width = 5, height = 1, PageFilter.BLACK_AND_WHITE)
        assertThat(pixels.toSet()).isSubsetOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        assertThat(pixels[0]).isEqualTo(0xFFFFFFFF.toInt())
        assertThat(pixels[1]).isEqualTo(0xFF000000.toInt())
    }

    @Test
    fun `black and white of a uniform page does not crash`() {
        val pixels = IntArray(4) { lightGray }
        applyFilter(pixels, width = 2, height = 2, PageFilter.BLACK_AND_WHITE)
        assertThat(pixels.toSet()).hasSize(1)
    }

    @Test
    fun `black and white keeps a uniform dark background white, not a solid black block`() {
        // Regression for keepsheet#51: a real photo often includes some non-page
        // background (an uncropped or under-cropped photo, see PageFilters.kt's doc on
        // sauvolaThresholds) — a uniformly dark area like a desk, with no local contrast
        // of its own, must not become a giant black region just because it's darker
        // than the page. A page with actual dark text still yields plenty of black
        // (see the mixed-content test above) — this is specifically about *uniform*
        // dark regions, which read as "no printed content here" rather than "ink."
        val pixels = IntArray(64 * 64) { darkGray }
        applyFilter(pixels, width = 64, height = 64, PageFilter.BLACK_AND_WHITE)
        assertThat(pixels.toSet()).containsExactly(0xFFFFFFFF.toInt())
    }

    @Test
    fun `black and white does not speckle a noisy near-black region`() {
        // Regression: a near-black but not perfectly uniform region (e.g. sensor/JPEG
        // noise on a dark or lightless camera frame, see PageFilters.kt's doc on the
        // MIN_GAP floor) has *some* local variance, just not from real content. Without
        // a floor on the light/dark gap, that tiny noise straddled the threshold and
        // roughly half of it flipped to black — dense speckle across the whole region,
        // which is what made Tesseract OCR pathologically slow on the emulator's
        // near-black synthetic camera feed (DocumentDetailScreenTest/MergeScreenTest
        // both timed out on an unrelated Compose wait as a result).
        fun gray(level: Int) = (0xFF shl 24) or (level shl 16) or (level shl 8) or level
        val pixels = IntArray(64 * 64) { index -> gray(index % 9) } // noise wobbling 0..8
        applyFilter(pixels, width = 64, height = 64, PageFilter.BLACK_AND_WHITE)
        assertThat(pixels.toSet()).containsExactly(0xFFFFFFFF.toInt())
    }

    @Test
    fun `black and white still renders real text as black against its white page`() {
        // A page mostly like real paper (light) with a thin stroke of genuine dark content
        // (text) should still binarize normally — the fix for keepsheet#51 must not wash out
        // real content along with the uniform-background case above. The stroke is narrower
        // than a threshold block (see BLOCKS_ACROSS in PageFilters.kt) so every block touching
        // it still sees both paper and ink — same as any real photographed page, where text
        // strokes are a handful of pixels wide against a photo hundreds/thousands of pixels
        // across, i.e. always far smaller than a block.
        val width = 200
        val height = 200
        val pixels =
            IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                if (x in 100..102 && y in 40..160) darkGray else lightGray
            }
        applyFilter(pixels, width, height, PageFilter.BLACK_AND_WHITE)
        val textPixel = pixels[100 * width + 101]
        val paperPixel = pixels[10 * width + 10]
        assertThat(textPixel).isEqualTo(0xFF000000.toInt())
        assertThat(paperPixel).isEqualTo(0xFFFFFFFF.toInt())
    }

    @Test
    fun `first page defaults to color, later pages to the last used filter`() {
        assertThat(defaultFilter(null)).isEqualTo(PageFilter.COLOR)
        assertThat(defaultFilter(PageFilter.GRAYSCALE)).isEqualTo(PageFilter.GRAYSCALE)
    }
}
