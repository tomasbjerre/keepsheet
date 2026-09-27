package com.github.tomasbjerre.keepsheet.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** See specs/ui-flows.md#3-page-review: reordering pages by drag. Pure math — the actual
 * gesture (long-press then drag) needs the real pointer input pipeline and is covered by
 * PageReviewScreenTest instead (see android/README.md#testing). */
class PageReviewDragTest {
    private val itemExtentPx = 100f

    private fun drag(
        amountX: Float,
        from: Int,
        pageCount: Int = 3,
    ) = computeReviewDrag(
        dragAmountX = amountX,
        currentOffsetPx = 0f,
        currentIndex = from,
        pageCount = pageCount,
        itemExtentPx = itemExtentPx,
    )

    @Test
    fun `dragging less than half a slot stays put`() {
        val result = drag(amountX = 40f, from = 0)
        assertThat(result.targetIndex).isNull()
        assertThat(result.offsetPx).isEqualTo(40f)
    }

    @Test
    fun `dragging past half a slot moves to the next index and renormalizes the offset`() {
        val result = drag(amountX = 60f, from = 0)
        assertThat(result.targetIndex).isEqualTo(1)
        assertThat(result.offsetPx).isEqualTo(-40f)
    }

    @Test
    fun `dragging left moves to a lower index`() {
        assertThat(drag(amountX = -60f, from = 2).targetIndex).isEqualTo(1)
    }

    @Test
    fun `target index is clamped to the list bounds`() {
        assertThat(drag(amountX = 500f, from = 0).targetIndex).isEqualTo(2)
        assertThat(drag(amountX = -500f, from = 2).targetIndex).isEqualTo(0)
    }

    @Test
    fun `an untracked index (-1) never computes a move`() {
        assertThat(drag(amountX = 500f, from = -1).targetIndex).isNull()
    }

    @Test
    fun `moved removes an item from its old index and inserts it at the new one`() {
        assertThat(listOf("a", "b", "c").moved(0, 1)).containsExactly("b", "a", "c")
        assertThat(listOf("a", "b", "c").moved(2, 0)).containsExactly("c", "a", "b")
        assertThat(listOf("a", "b", "c").moved(1, 1)).containsExactly("a", "b", "c")
    }
}
