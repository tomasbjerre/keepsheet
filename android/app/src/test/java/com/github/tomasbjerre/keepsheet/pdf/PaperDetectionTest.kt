package com.github.tomasbjerre.keepsheet.pdf

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** See specs/capture-and-processing.md#automatic-cropping-and-straightening. */
class PaperDetectionTest {
    private val size = 200

    /** A dark background with a bright convex quad drawn in it (point-in-polygon fill). */
    private fun photoWithPaper(
        paper: Corners?,
        background: Int = 40,
        paperLevel: Int = 220,
    ): IntArray =
        IntArray(size * size) { index ->
            val x = (index % size + 0.5f) / size
            val y = (index / size + 0.5f) / size
            if (paper != null && inside(paper.toList(), x, y)) paperLevel else background
        }

    private fun inside(
        quad: List<Point>,
        x: Float,
        y: Float,
    ): Boolean {
        val signs =
            quad.indices.map { i ->
                val a = quad[i]
                val b = quad[(i + 1) % quad.size]
                (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x) > 0
            }
        return signs.all { it } || signs.none { it }
    }

    private val tilted =
        Corners(Point(0.2f, 0.15f), Point(0.85f, 0.25f), Point(0.8f, 0.9f), Point(0.12f, 0.8f))

    @Test
    fun `finds the corners of a tilted page`() {
        val found = detectPaperCorners(photoWithPaper(tilted), size, size)!!

        found.toList().zip(tilted.toList()).forEach { (actual, expected) ->
            assertThat(actual.x).isCloseTo(
                expected.x,
                org.assertj.core.data.Offset
                    .offset(0.03f),
            )
            assertThat(actual.y).isCloseTo(
                expected.y,
                org.assertj.core.data.Offset
                    .offset(0.03f),
            )
        }
    }

    @Test
    fun `gives up on a photo with no contrast`() {
        assertThat(detectPaperCorners(IntArray(size * size) { 128 }, size, size)).isNull()
    }

    @Test
    fun `gives up when the page already fills the frame`() {
        val full = Corners(Point(0f, 0f), Point(1f, 0f), Point(1f, 1f), Point(0f, 1f))
        assertThat(detectPaperCorners(photoWithPaper(full), size, size)).isNull()
    }

    @Test
    fun `gives up on a small bright object that is not a page`() {
        val small = Corners(Point(0.4f, 0.4f), Point(0.55f, 0.4f), Point(0.55f, 0.55f), Point(0.4f, 0.55f))
        assertThat(detectPaperCorners(photoWithPaper(small), size, size)).isNull()
    }

    @Test
    fun `gives up on a bright region that is not a quadrilateral`() {
        // An L-shaped bright region: its extreme-point quad would be mostly empty.
        val pixels = IntArray(size * size) { 40 }
        for (y in 20 until 180) {
            for (x in 20 until 180) {
                if (x < 70 || y > 130) pixels[y * size + x] = 220
            }
        }
        assertThat(detectPaperCorners(pixels, size, size)).isNull()
    }

    @Test
    fun `output size follows the corner distances`() {
        val rect = Corners(Point(0.1f, 0.1f), Point(0.6f, 0.1f), Point(0.6f, 0.9f), Point(0.1f, 0.9f))
        assertThat(rect.outputSize(1000, 2000)).isEqualTo(500 to 1600)
    }
}
