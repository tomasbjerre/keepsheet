package com.github.tomasbjerre.keepsheet.pdf

import com.github.tomasbjerre.keepsheet.data.PageFilter

/**
 * Applies a [PageFilter] (specs/capture-and-processing.md#document-filters) to ARGB
 * [pixels] in place. Pure so it can be unit-tested without real Android graphics.
 */
fun applyFilter(
    pixels: IntArray,
    filter: PageFilter,
) {
    when (filter) {
        PageFilter.COLOR -> Unit
        PageFilter.GRAYSCALE -> pixels.indices.forEach { pixels[it] = gray(pixels[it]) }
        PageFilter.BLACK_AND_WHITE -> {
            val grays = IntArray(pixels.size) { luminance(pixels[it]) }
            val threshold = otsuThreshold(grays)
            pixels.indices.forEach { pixels[it] = if (grays[it] > threshold) WHITE else BLACK }
        }
    }
}

/**
 * The filter a page starts with: the last one used in the current capture session, or
 * black-and-white for the first page of a new one.
 */
fun defaultFilter(lastUsed: PageFilter?): PageFilter = lastUsed ?: PageFilter.BLACK_AND_WHITE

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val LEVELS = 256

private fun luminance(argb: Int): Int {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (r * 299 + g * 587 + b * 114) / 1000
}

private fun gray(argb: Int): Int {
    val l = luminance(argb)
    return (argb and 0xFF000000.toInt()) or (l shl 16) or (l shl 8) or l
}

/** Otsu's method: the threshold maximizing between-class variance of the histogram. */
private fun otsuThreshold(grays: IntArray): Int {
    val histogram = IntArray(LEVELS)
    grays.forEach { histogram[it]++ }
    val total = grays.size.toLong()
    val sumAll = histogram.indices.sumOf { it.toLong() * histogram[it] }
    var sumBackground = 0L
    var weightBackground = 0L
    var best = 0.0
    var threshold = LEVELS / 2
    for (level in 0 until LEVELS) {
        weightBackground += histogram[level]
        sumBackground += level.toLong() * histogram[level]
        val weightForeground = total - weightBackground
        if (weightBackground > 0 && weightForeground > 0) {
            val meanBackground = sumBackground.toDouble() / weightBackground
            val meanForeground = (sumAll - sumBackground).toDouble() / weightForeground
            val diff = meanBackground - meanForeground
            val variance = weightBackground.toDouble() * weightForeground * diff * diff
            if (variance > best) {
                best = variance
                threshold = level
            }
        }
    }
    return threshold
}
