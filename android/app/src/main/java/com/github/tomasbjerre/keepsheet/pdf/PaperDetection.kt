package com.github.tomasbjerre.keepsheet.pdf

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A point in normalized image coordinates: 0..1 on both axes, origin top-left. */
data class Point(
    val x: Float,
    val y: Float,
)

/**
 * The four corners of a page within a photo, in normalized coordinates — see
 * specs/capture-and-processing.md#automatic-cropping-and-straightening.
 */
data class Corners(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point,
) {
    fun toList(): List<Point> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun with(
        index: Int,
        point: Point,
    ): Corners {
        val points = toList().toMutableList()
        points[index] = point
        return fromList(points)
    }

    /** Size in pixels of the flat page these corners map to, in a photo of [width]×[height]. */
    fun outputSize(
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        fun length(
            a: Point,
            b: Point,
        ) = hypot((a.x - b.x) * width, (a.y - b.y) * height)
        val w = (length(topLeft, topRight) + length(bottomLeft, bottomRight)) / 2
        val h = (length(topLeft, bottomLeft) + length(topRight, bottomRight)) / 2
        return w.roundToInt().coerceAtLeast(1) to h.roundToInt().coerceAtLeast(1)
    }

    companion object {
        fun fromList(points: List<Point>) = Corners(points[0], points[1], points[2], points[3])

        /** A starting quad for manual adjustment when nothing was detected. */
        fun inset(fraction: Float = MANUAL_INSET) =
            Corners(
                Point(fraction, fraction),
                Point(1 - fraction, fraction),
                Point(1 - fraction, 1 - fraction),
                Point(fraction, 1 - fraction),
            )

        private const val MANUAL_INSET = 0.1f
    }
}

private const val MIN_PAGE_AREA = 0.25
private const val MAX_PAGE_AREA = 0.97
private const val MIN_FILL = 0.75
private const val EDGE_TOLERANCE = 0.03
private val EDGE_SAMPLES = listOf(0.25f, 0.5f, 0.75f)
private const val LEVELS = 256

/**
 * Finds the paper's corners in a photo given as luminance values ([luminance], row-major,
 * [width]×[height], 0..255), or null when it can't do so confidently — the caller then keeps
 * the original, uncropped photo (never guess a wrong crop). Assumes the page is lighter than
 * its surroundings: Otsu-thresholds the photo, takes the largest bright region, and uses its
 * extreme points as corners. Rejected unless that region covers a meaningful part of the
 * frame, is close to a filled quadrilateral (so a hand or clutter doesn't pass for a page),
 * and isn't already the whole frame (nothing to crop).
 */
fun detectPaperCorners(
    luminance: IntArray,
    width: Int,
    height: Int,
): Corners? {
    require(luminance.size == width * height) { "luminance must be width*height" }
    val threshold = otsu(luminance)
    val bright = BooleanArray(luminance.size) { luminance[it] > threshold }
    val region = largestRegion(bright, width, height)
    val corners = region?.let { extremeCorners(it, width, height) }
    val frameFraction = corners?.let { quadArea(it) }
    val plausible =
        region != null &&
            frameFraction != null &&
            frameFraction in MIN_PAGE_AREA..MAX_PAGE_AREA &&
            region.size / (frameFraction * width * height) >= MIN_FILL &&
            edgesFollowRegion(corners, region, width, height)
    return if (plausible) corners else null
}

private fun otsu(values: IntArray): Int {
    val histogram = IntArray(LEVELS)
    values.forEach { histogram[it.coerceIn(0, LEVELS - 1)]++ }
    val total = values.size.toLong()
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

/** Pixel indices of the biggest 4-connected `true` region. */
private fun largestRegion(
    mask: BooleanArray,
    width: Int,
    height: Int,
): IntArray? {
    val visited = BooleanArray(mask.size)
    val queue = IntArray(mask.size)
    var best: IntArray? = null
    for (start in mask.indices) {
        if (mask[start] && !visited[start]) {
            val region = floodFill(start, mask, visited, queue, width, height)
            if (best == null || region.size > best.size) best = region
        }
    }
    return best
}

private fun floodFill(
    start: Int,
    mask: BooleanArray,
    visited: BooleanArray,
    queue: IntArray,
    width: Int,
    height: Int,
): IntArray {
    var head = 0
    var tail = 0
    queue[tail++] = start
    visited[start] = true
    while (head < tail) {
        val index = queue[head++]
        val x = index % width
        val y = index / width
        val neighbours =
            intArrayOf(
                if (x > 0) index - 1 else -1,
                if (x < width - 1) index + 1 else -1,
                if (y > 0) index - width else -1,
                if (y < height - 1) index + width else -1,
            )
        for (n in neighbours) {
            if (n >= 0 && mask[n] && !visited[n]) {
                visited[n] = true
                queue[tail++] = n
            }
        }
    }
    return queue.copyOfRange(0, tail)
}

private fun extremeCorners(
    region: IntArray,
    width: Int,
    height: Int,
): Corners {
    var tl = region[0]
    var tr = region[0]
    var br = region[0]
    var bl = region[0]

    fun sum(i: Int) = i % width + i / width

    fun diff(i: Int) = i % width - i / width
    for (i in region) {
        if (sum(i) < sum(tl)) tl = i
        if (sum(i) > sum(br)) br = i
        if (diff(i) > diff(tr)) tr = i
        if (diff(i) < diff(bl)) bl = i
    }

    fun point(i: Int) = Point((i % width + 0.5f) / width, (i / width + 0.5f) / height)
    return Corners(point(tl), point(tr), point(br), point(bl))
}

/** Shoelace area of the quad as a fraction of the whole frame (the corners are normalized). */
private fun quadArea(corners: Corners): Double {
    val p = corners.toList()
    var sum = 0.0
    for (i in p.indices) {
        val a = p[i]
        val b = p[(i + 1) % p.size]
        sum += a.x.toDouble() * b.y - b.x.toDouble() * a.y
    }
    return abs(sum) / 2
}

/**
 * True when the region actually reaches each of the quad's four sides (sampled at a few
 * points along them) — a blob whose extreme-point quad is mostly empty (an L shape, a hand
 * holding a page) isn't a page.
 */
private fun edgesFollowRegion(
    corners: Corners,
    region: IntArray,
    width: Int,
    height: Int,
): Boolean {
    val inRegion = BooleanArray(width * height)
    region.forEach { inRegion[it] = true }
    val radius = (EDGE_TOLERANCE * minOf(width, height)).toInt().coerceAtLeast(2)
    val points = corners.toList()
    return points.indices.all { i ->
        val a = points[i]
        val b = points[(i + 1) % points.size]
        EDGE_SAMPLES.all { t ->
            val cx = ((a.x + (b.x - a.x) * t) * width).toInt()
            val cy = ((a.y + (b.y - a.y) * t) * height).toInt()
            hasRegionNear(inRegion, cx, cy, radius, width, height)
        }
    }
}

private fun hasRegionNear(
    inRegion: BooleanArray,
    cx: Int,
    cy: Int,
    radius: Int,
    width: Int,
    height: Int,
): Boolean {
    for (y in (cy - radius).coerceAtLeast(0)..(cy + radius).coerceAtMost(height - 1)) {
        for (x in (cx - radius).coerceAtLeast(0)..(cx + radius).coerceAtMost(width - 1)) {
            if (inRegion[y * width + x]) return true
        }
    }
    return false
}
