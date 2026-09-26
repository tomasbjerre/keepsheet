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
        applyFilter(pixels, PageFilter.COLOR)
        assertThat(pixels).containsExactly(red, lightGray)
    }

    @Test
    fun `grayscale makes every channel equal and keeps shading`() {
        val pixels = intArrayOf(red, lightGray)
        applyFilter(pixels, PageFilter.GRAYSCALE)
        pixels.forEach {
            assertThat((it shr 16) and 0xFF).isEqualTo((it shr 8) and 0xFF).isEqualTo(it and 0xFF)
        }
        assertThat(pixels[1]).isEqualTo(lightGray)
    }

    @Test
    fun `black and white yields only pure black and white`() {
        val pixels = intArrayOf(lightGray, darkGray, lightGray, darkGray, red)
        applyFilter(pixels, PageFilter.BLACK_AND_WHITE)
        assertThat(pixels.toSet()).isSubsetOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        assertThat(pixels[0]).isEqualTo(0xFFFFFFFF.toInt())
        assertThat(pixels[1]).isEqualTo(0xFF000000.toInt())
    }

    @Test
    fun `black and white of a uniform page does not crash`() {
        val pixels = IntArray(4) { lightGray }
        applyFilter(pixels, PageFilter.BLACK_AND_WHITE)
        assertThat(pixels.toSet()).hasSize(1)
    }

    @Test
    fun `first page defaults to black and white, later pages to the last used filter`() {
        assertThat(defaultFilter(null)).isEqualTo(PageFilter.BLACK_AND_WHITE)
        assertThat(defaultFilter(PageFilter.GRAYSCALE)).isEqualTo(PageFilter.GRAYSCALE)
    }
}
