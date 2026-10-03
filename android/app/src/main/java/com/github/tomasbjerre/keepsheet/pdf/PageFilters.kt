package com.github.tomasbjerre.keepsheet.pdf

import com.github.tomasbjerre.keepsheet.data.PageFilter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Applies a [PageFilter] (specs/capture-and-processing.md#document-filters) to ARGB
 * [pixels] ([width]x[height]) in place. Pure so it can be unit-tested without real
 * Android graphics.
 */
fun applyFilter(
    pixels: IntArray,
    width: Int,
    height: Int,
    filter: PageFilter,
) {
    when (filter) {
        PageFilter.COLOR -> Unit
        PageFilter.GRAYSCALE -> pixels.indices.forEach { pixels[it] = gray(pixels[it]) }
        PageFilter.BLACK_AND_WHITE -> {
            val grays = IntArray(pixels.size) { luminance(pixels[it]) }
            val thresholds = sauvolaThresholds(grays, width, height)
            pixels.indices.forEach { pixels[it] = if (grays[it] > thresholds[it]) WHITE else BLACK }
        }
    }
}

/**
 * The filter a page starts with: the last one used in the current capture session, or
 * color for the first page of a new one (keepsheet#84).
 */
fun defaultFilter(lastUsed: PageFilter?): PageFilter = lastUsed ?: PageFilter.COLOR

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

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

/**
 * Sauvola's method: a threshold from the local mean/stddev of each pixel's neighborhood,
 * rather than one global cutoff (Otsu) for the whole photo.
 *
 * A captured page's photo often includes some non-page background (whatever the page was
 * photographed on) alongside the page itself — either because automatic cropping couldn't
 * confidently find the page's edges and fell back to the original, uncropped photo, or
 * because a manual crop left a sliver of it in (specs/capture-and-processing.md#automatic-
 * cropping-and-straightening). A single global threshold (the previous approach here) binarizes
 * relative to the *whole* photo, so a large, uniformly dark background — common when scanning
 * on a dark desk — got classified as one solid black region: a real photo produced a page
 * that was mostly a black block instead of a legible scan (keepsheet#51). Sauvola's threshold
 * is keyed to a neighborhood around each pixel instead: a uniform area (no local contrast)
 * always resolves to *lighter* than its own neighborhood's threshold regardless of how dark it
 * absolutely is (the formula below shifts below the local mean whenever there's any positive
 * contrast weight `k`), so it reads as white — only actual local contrast (a dark stroke
 * against its immediate light surroundings, i.e. real text) is picked out as black. See
 * `PageFiltersTest` for the uniform-dark-region case.
 *
 * The "neighborhood" here is a coarse grid of blocks (one threshold per block, shared by every
 * pixel in it), not a true per-pixel sliding window: a real photo can be many megapixels, and a
 * sliding window needs either a summed-area table (two extra full-size `Long` arrays — tens of
 * megabytes each on a large photo, competing with the several bitmap copies already alive
 * during finalize) or a stddev per pixel (a `sqrt` for every one of those megapixels). Both were
 * slow enough on a real device/emulator to blow past this app's own Compose UI test timeouts
 * (15s) finalizing a single document. A ~20x20 grid keeps blocks around the same size as the
 * window this used before switching to blocks, at a small, fixed number of `sqrt` calls
 * regardless of photo resolution.
 */
private fun sauvolaThresholds(
    grays: IntArray,
    width: Int,
    height: Int,
): IntArray {
    // The floor keeps a block from collapsing to a single pixel on a small image (every
    // block would then have zero variance by definition — no two pixels to compare —
    // which this algorithm reads as "no local contrast" and resolves to white, i.e. the
    // whole image goes blank). A tiny real photo just ends up with one block covering
    // it (global thresholding), which is a reasonable fallback rather than a bug.
    val blockSize = max(MIN_BLOCK_SIZE, min(width, height) / BLOCKS_ACROSS)
    val blocksX = (width + blockSize - 1) / blockSize
    val blocksY = (height + blockSize - 1) / blockSize

    val sum = LongArray(blocksX * blocksY)
    val sumSquares = LongArray(blocksX * blocksY)
    val count = IntArray(blocksX * blocksY)
    for (y in 0 until height) {
        val blockY = y / blockSize
        for (x in 0 until width) {
            val block = blockY * blocksX + (x / blockSize)
            val value = grays[y * width + x].toLong()
            sum[block] += value
            sumSquares[block] += value * value
            count[block]++
        }
    }

    val thresholds =
        IntArray(blocksX * blocksY) { block ->
            val mean = sum[block].toDouble() / count[block]
            val meanOfSquares = sumSquares[block].toDouble() / count[block]
            val variance = max(0.0, meanOfSquares - mean * mean)
            val stddev = sqrt(variance)
            // mean * (1 + k*(stddev/R - 1)), rearranged as mean minus a gap so the gap
            // itself can be floored below. The gap (mean * k * (1 - stddev/R)) scales
            // with the block's own mean, so a block near black has almost no gap even at
            // k's full weight — e.g. mean=4 gives a ~1-wide gap. That's fine for a truly
            // flat dark block (nothing to threshold either way), but real sensor/JPEG
            // noise on a dark camera frame (say values wobbling 0-8, no real content at
            // all) has a stddev comparable to that whole gap, so the threshold lands
            // *inside* the noise's own range and roughly half of it ends up on each
            // side — dense black/white speckle across the entire block, mistaken for
            // text-like detail by anything reading the result (this is what made
            // Tesseract OCR pathologically slow on the emulator's near-black synthetic
            // camera feed in DocumentDetailScreenTest/MergeScreenTest, timing out
            // otherwise-unrelated Compose waits). A brighter block doesn't have this
            // problem (the gap scales with its mean, comfortably wider than realistic
            // noise), so the floor only ever bites in the dark, low-signal case it's
            // meant for.
            val gap = max(MIN_GAP, mean * SAUVOLA_K * (1 - stddev / SAUVOLA_R))
            (mean - gap).toInt()
        }

    return IntArray(grays.size) { index ->
        val x = index % width
        val y = index / width
        thresholds[(y / blockSize) * blocksX + (x / blockSize)]
    }
}

private const val BLOCKS_ACROSS = 20
private const val MIN_BLOCK_SIZE = 8
private const val SAUVOLA_K = 0.2
private const val SAUVOLA_R = 128.0
private const val MIN_GAP = 12.0
