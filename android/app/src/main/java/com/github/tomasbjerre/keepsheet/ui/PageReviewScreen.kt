package com.github.tomasbjerre.keepsheet.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.github.tomasbjerre.keepsheet.data.DocumentBuilder
import com.github.tomasbjerre.keepsheet.data.DocumentRepository
import com.github.tomasbjerre.keepsheet.data.DocumentSource
import com.github.tomasbjerre.keepsheet.data.PageFilter
import com.github.tomasbjerre.keepsheet.data.PaperFormat
import com.github.tomasbjerre.keepsheet.data.PaperFormatPreference
import com.github.tomasbjerre.keepsheet.pdf.Corners
import com.github.tomasbjerre.keepsheet.pdf.applyFilter
import com.github.tomasbjerre.keepsheet.pdf.applyRotation
import com.github.tomasbjerre.keepsheet.pdf.buildPdfFromImages
import com.github.tomasbjerre.keepsheet.pdf.copyImageForPage
import com.github.tomasbjerre.keepsheet.pdf.decodeScaled
import com.github.tomasbjerre.keepsheet.pdf.defaultFilter
import com.github.tomasbjerre.keepsheet.pdf.detectCornersInImage
import com.github.tomasbjerre.keepsheet.pdf.rotatedClockwise
import com.github.tomasbjerre.keepsheet.pdf.rotatedCounterClockwise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Reached from Capture's Done action or Home's Import (see
 * specs/ui-flows.md#3-page-review). Filter picking, crop adjustment, and reorder are
 * here; retake isn't (Capture's own thumbnail strip offers retake for a page before it
 * ever reaches this screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod") // Compose screen: state hoisting keeps this one flat function readable.
@Composable
fun PageReviewScreen(
    pages: List<Uri>,
    source: DocumentSource,
    repository: DocumentRepository,
    filesDir: File,
    onSaved: (documentId: Long) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val paperFormatPreference = remember { PaperFormatPreference(context) }
    var paperFormat by remember { mutableStateOf(paperFormatPreference.format) }
    var saving by remember { mutableStateOf(false) }
    // Reorderable copy of [pages] — permuted in lockstep with filters/corners/rotations
    // (all four stay index-aligned to the same page) whenever the thumbnail strip below
    // is dragged, so a page's filter/crop/rotation choices travel with it.
    var pageOrder by remember(pages) { mutableStateOf(pages) }
    var filters by remember(pages) { mutableStateOf(List(pages.size) { defaultFilter(null) }) }
    // Tracked by uri rather than index so the selected page stays selected across a
    // reorder, without having to shift an index by hand as pages move past it.
    var selectedUri by remember(pages) { mutableStateOf(pages.firstOrNull()) }
    val selected = pageOrder.indexOf(selectedUri).coerceAtLeast(0)
    var corners by remember(pages) { mutableStateOf<List<Corners?>>(List(pages.size) { null }) }
    var rotations by remember(pages) { mutableStateOf(List(pages.size) { 0 }) }
    var detecting by remember { mutableStateOf(true) }
    LaunchedEffect(pages) {
        corners = detectAll(context.contentResolver, pages)
        detecting = false
    }

    fun reorder(
        fromIndex: Int,
        toIndex: Int,
    ) {
        pageOrder = pageOrder.moved(fromIndex, toIndex)
        filters = filters.moved(fromIndex, toIndex)
        corners = corners.moved(fromIndex, toIndex)
        rotations = rotations.moved(fromIndex, toIndex)
    }

    Scaffold(
        topBar = { PageReviewTopBar(enabled = !saving, onBack = onCancel) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            SaveButton(
                enabled = pageOrder.isNotEmpty() && !saving && !detecting,
                saving = saving,
                onClick = {
                    saving = true
                    coroutineScope.launch {
                        val documentId =
                            saveAsDocument(
                                pageOrder,
                                filters,
                                rotations,
                                corners,
                                source,
                                repository,
                                filesDir,
                                context.contentResolver,
                                paperFormat,
                            )
                        // Explicit, rather than relying on withContext(Dispatchers.IO) above
                        // to hand back to whatever dispatched this coroutine: onSaved()
                        // navigates, and NavController requires the main thread for that.
                        withContext(Dispatchers.Main) {
                            saving = false
                            onSaved(documentId)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            Text("${pageOrder.size} page(s)")
            PageSizePicker(
                format = paperFormat,
                onPick = {
                    paperFormat = it
                    paperFormatPreference.format = it
                },
            )
            PageThumbnails(
                pages = pageOrder,
                selected = selected,
                onSelect = { index -> selectedUri = pageOrder[index] },
                onReorder = ::reorder,
            )
            if (pageOrder.isNotEmpty()) {
                CropSection(
                    uri = pageOrder[selected],
                    corners = corners[selected],
                    rotationDegrees = rotations[selected],
                    filter = filters[selected],
                    detecting = detecting,
                    onCornersChange = { corners = corners.toMutableList().also { list -> list[selected] = it } },
                    onRedetect = {
                        val uri = pageOrder[selected]
                        redetect(coroutineScope, context.contentResolver, uri, snackbarHostState) { found ->
                            corners = corners.toMutableList().also { list -> list[selected] = found }
                        }
                    },
                    onRotate = { degrees ->
                        rotations = rotations.toMutableList().also { it[selected] = degrees }
                        // The crop the user drew (or that detection found) was chosen against the
                        // page's old orientation — a rectangle fit to a portrait photo makes no
                        // sense once that photo is rotated 90°. Rather than silently keep a corner
                        // quad that now crops the wrong region, drop back to "full photo" and let
                        // the user re-detect or re-crop against the new orientation.
                        corners = corners.toMutableList().also { it[selected] = null }
                    },
                )
                FilterPicker(
                    uri = pageOrder[selected],
                    rotationDegrees = rotations[selected],
                    current = filters[selected],
                    onPick = { picked -> filters = filters.toMutableList().also { it[selected] = picked } },
                    onApplyToAll = { filters = List(pageOrder.size) { filters[selected] } },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageReviewTopBar(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text("Review pages") },
        navigationIcon = {
            IconButton(onClick = onBack, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

/**
 * See specs/capture-and-processing.md#printer-friendly-pages: the page size (A4 or
 * Letter) every page of this document is built to fit, remembered (via
 * [PaperFormatPreference]) across documents until changed again.
 */
@Composable
private fun PageSizePicker(
    format: PaperFormat,
    onPick: (PaperFormat) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("Page size: ${format.label()}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PaperFormat.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = {
                        onPick(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun PaperFormat.label() =
    when (this) {
        PaperFormat.A4 -> "A4"
        PaperFormat.LETTER -> "Letter"
    }

private val REVIEW_THUMBNAIL_SIZE = 96.dp
private val REVIEW_THUMBNAIL_SPACING = 8.dp

/** Lets PageReviewScreenTest count/target page thumbnails without depending on their
 * (dynamic) content. */
const val REVIEW_THUMBNAIL_TEST_TAG = "review-thumbnail"

/**
 * See specs/ui-flows.md#3-page-review: reorder (drag), same interaction as Capture's own
 * thumbnail strip (long-press then drag, so a plain tap still selects a page for the crop/
 * filter controls below).
 */
@Composable
private fun PageThumbnails(
    pages: List<Uri>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    val density = LocalDensity.current
    val itemExtentPx = with(density) { (REVIEW_THUMBNAIL_SIZE + REVIEW_THUMBNAIL_SPACING).toPx() }
    var draggingUri by remember { mutableStateOf<Uri?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(REVIEW_THUMBNAIL_SPACING),
    ) {
        itemsIndexed(pages, key = { _, uri -> uri }) { index, uri ->
            val isDragging = uri == draggingUri
            AsyncImage(
                model = uri,
                contentDescription = "Page ${index + 1}",
                modifier =
                    Modifier
                        .size(REVIEW_THUMBNAIL_SIZE)
                        .testTag(REVIEW_THUMBNAIL_TEST_TAG)
                        .graphicsLayer { translationX = if (isDragging) dragOffsetPx else 0f }
                        .zIndex(if (isDragging) 1f else 0f)
                        .border(if (index == selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary)
                        // Ahead of .clickable below so this drag detector sees (and, once a
                        // drag actually starts, consumes) touch events first — otherwise
                        // clickable's own gesture recognizer claims them and a long-press
                        // drag never starts.
                        .pointerInput(uri, pages) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingUri = uri
                                    dragOffsetPx = 0f
                                },
                                onDragEnd = {
                                    draggingUri = null
                                    dragOffsetPx = 0f
                                },
                                onDragCancel = {
                                    draggingUri = null
                                    dragOffsetPx = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val currentIndex = pages.indexOf(uri)
                                    val result =
                                        computeReviewDrag(
                                            dragAmount.x,
                                            dragOffsetPx,
                                            currentIndex,
                                            pages.size,
                                            itemExtentPx,
                                        )
                                    dragOffsetPx = result.offsetPx
                                    result.targetIndex?.let { onReorder(currentIndex, it) }
                                },
                            )
                        }.clickable { onSelect(index) },
            )
        }
    }
}

internal data class ReviewDragResult(
    val offsetPx: Float,
    val targetIndex: Int?,
)

/** Pure so it's easy to reason about (and unit test — see PageReviewDragTest) — same
 * shape as Capture's own computeThumbnailDrag. */
internal fun computeReviewDrag(
    dragAmountX: Float,
    currentOffsetPx: Float,
    currentIndex: Int,
    pageCount: Int,
    itemExtentPx: Float,
): ReviewDragResult {
    val newOffset = currentOffsetPx + dragAmountX
    if (currentIndex == -1) return ReviewDragResult(newOffset, null)
    val slotShift = (newOffset / itemExtentPx).roundToInt()
    val targetIndex = (currentIndex + slotShift).coerceIn(0, pageCount - 1)
    if (targetIndex == currentIndex) return ReviewDragResult(newOffset, null)
    return ReviewDragResult(newOffset - (targetIndex - currentIndex) * itemExtentPx, targetIndex)
}

internal fun <T> List<T>.moved(
    fromIndex: Int,
    toIndex: Int,
): List<T> = toMutableList().also { it.add(toIndex, it.removeAt(fromIndex)) }

/**
 * See specs/capture-and-processing.md#automatic-cropping-and-straightening: the detected
 * crop is only a starting point the user reviews (and adjusts) in Page Review.
 */
private suspend fun detectAll(
    resolver: ContentResolver,
    pages: List<Uri>,
): List<Corners?> = withContext(Dispatchers.IO) { pages.map { detectCornersInImage(resolver, it) } }

/** See specs/capture-and-processing.md#automatic-cropping-and-straightening (keepsheet#68):
 * always says what happened, even when the outcome doesn't change the crop preview at
 * all — a preview that looks the same before and after tapping "Detect edges" is
 * otherwise indistinguishable from the tap having done nothing. */
private fun redetect(
    scope: CoroutineScope,
    resolver: ContentResolver,
    uri: Uri,
    snackbarHostState: SnackbarHostState,
    onFound: (Corners?) -> Unit,
) {
    scope.launch {
        val found = withContext(Dispatchers.IO) { detectCornersInImage(resolver, uri) }
        onFound(found)
        val message =
            if (found != null) "Found the page edges." else "Detection found nothing — still using the full photo."
        snackbarHostState.showSnackbar(message)
    }
}

@Suppress("LongParameterList")
@Composable
private fun CropSection(
    uri: Uri,
    corners: Corners?,
    rotationDegrees: Int,
    filter: PageFilter,
    detecting: Boolean,
    onCornersChange: (Corners?) -> Unit,
    onRedetect: () -> Unit,
    onRotate: (Int) -> Unit,
) {
    var showViewer by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        CropEditor(
            uri = uri,
            corners = corners,
            rotationDegrees = rotationDegrees,
            filter = filter,
            onCornersChange = { onCornersChange(it) },
        )
        Text(
            when {
                detecting -> "Looking for the page edges…"
                corners == null -> "No page edges found — using the full photo."
                else -> "Drag the corners to adjust the crop."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "Pinch to zoom in for a closer look, drag to pan.",
            style = MaterialTheme.typography.bodySmall,
        )
        // Wraps rather than overflowing: four labelled buttons don't fit in one line on a
        // narrow (or large-font) screen, which used to push "View full size" off the edge.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRedetect, enabled = !detecting) { Text("Detect edges") }
            TextButton(onClick = { onCornersChange(Corners.inset()) }, enabled = !detecting && corners == null) {
                Text("Crop manually")
            }
            TextButton(onClick = { onCornersChange(null) }, enabled = corners != null) { Text("Full photo") }
            TextButton(onClick = { showViewer = true }) { Text("View full size") }
        }
        RotateControls(rotationDegrees = rotationDegrees, onRotate = onRotate)
    }
    if (showViewer) {
        PhotoViewerDialog(
            uri = uri,
            rotationDegrees = rotationDegrees,
            filter = filter,
            onDismiss = { showViewer = false },
        )
    }
}

/**
 * See specs/capture-and-processing.md#page-rotation: rotating shows its effect immediately in
 * [CropEditor]'s preview above (this same screen), so what will be applied to the saved page
 * is never a guess — the label states the pending rotation in degrees explicitly for the
 * same reason.
 */
@Composable
private fun RotateControls(
    rotationDegrees: Int,
    onRotate: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onRotate(rotatedCounterClockwise(rotationDegrees)) }) { Text("Rotate left") }
        TextButton(onClick = { onRotate(rotatedClockwise(rotationDegrees)) }) { Text("Rotate right") }
        Text("Rotation: $rotationDegrees°", style = MaterialTheme.typography.bodySmall)
    }
}

const val FILTER_PREVIEW_TEST_TAG = "filterPreview"
private const val FILTER_PREVIEW_MAX_SIDE = 96

/**
 * See specs/capture-and-processing.md#document-filters: grayscale and black-and-white read
 * as similar words, but produce very different results on a page with any shading or photo
 * content — grayscale keeps it, black-and-white doesn't. Rather than make a user infer that
 * from the label, each chip previews what its filter actually does to THIS page (keepsheet#49)
 * — a generic icon can't show that, since the difference depends on what's on the page.
 */
@Composable
private fun FilterPicker(
    uri: Uri,
    rotationDegrees: Int,
    current: PageFilter,
    onPick: (PageFilter) -> Unit,
    onApplyToAll: () -> Unit,
) {
    val previews = filterPreviews(uri, rotationDegrees)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == current,
                onClick = { onPick(filter) },
                label = { Text(filter.label()) },
                leadingIcon =
                    previews?.get(filter)?.let { preview ->
                        {
                            Image(
                                bitmap = preview,
                                contentDescription = "${filter.label()} preview",
                                modifier = Modifier.size(24.dp).testTag(FILTER_PREVIEW_TEST_TAG),
                            )
                        }
                    },
            )
        }
    }
    TextButton(onClick = onApplyToAll) { Text("Apply to all pages") }
}

/**
 * A small (see [FILTER_PREVIEW_MAX_SIDE]) copy of [uri], rotated like the live preview above
 * (keepsheet#52) so what a chip shows matches what will actually be saved, filtered three
 * ways with the same pure [applyFilter] the real export uses — not a stand-in approximation.
 */
@Composable
private fun filterPreviews(
    uri: Uri,
    rotationDegrees: Int,
): Map<PageFilter, ImageBitmap>? {
    val resolver = LocalContext.current.contentResolver
    val previews by
        produceState<Map<PageFilter, ImageBitmap>?>(null, uri, rotationDegrees) {
            value =
                withContext(Dispatchers.Default) {
                    val source =
                        decodeScaled(resolver, uri, FILTER_PREVIEW_MAX_SIDE)
                            ?.let { applyRotation(it, rotationDegrees) }
                            ?: return@withContext null
                    val width = source.width
                    val height = source.height
                    val basePixels = IntArray(width * height)
                    source.getPixels(basePixels, 0, width, 0, 0, width, height)
                    source.recycle()
                    PageFilter.entries.associateWith { filter ->
                        val pixels = basePixels.copyOf()
                        applyFilter(pixels, width, height, filter)
                        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
                    }
                }
        }
    return previews
}

private fun PageFilter.label() =
    when (this) {
        PageFilter.COLOR -> "Color"
        PageFilter.GRAYSCALE -> "Grayscale"
        PageFilter.BLACK_AND_WHITE -> "Black & white"
    }

@Composable
private fun SaveButton(
    enabled: Boolean,
    saving: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .fillMaxWidth()
                // The app runs edge-to-edge (see enableEdgeToEdge() in MainActivity), and
                // Scaffold's bottomBar slot isn't padded for system bars on its own the way
                // its content slot is via innerPadding — without this, the gesture/nav bar
                // overlaps (or on some devices fully covers) this button (#48).
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (saving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Text("Save")
        }
    }
}

@Suppress("LongParameterList")
private suspend fun saveAsDocument(
    pages: List<Uri>,
    filters: List<PageFilter>,
    rotations: List<Int>,
    corners: List<Corners?>,
    source: DocumentSource,
    repository: DocumentRepository,
    filesDir: File,
    contentResolver: ContentResolver,
    paperFormat: PaperFormat,
): Long {
    val builder =
        DocumentBuilder(
            repository = repository,
            pagesDir = File(filesDir, "pages"),
            documentsDir = File(filesDir, "documents"),
            importPage = { index, filter, rotationDegrees, destination ->
                copyImageForPage(contentResolver, pages[index], destination, filter, rotationDegrees, corners[index])
            },
            buildPdf = { imagePaths, destination -> buildPdfFromImages(imagePaths, destination, paperFormat) },
        )
    return withContext(Dispatchers.IO) {
        builder.build(
            pageCount = pages.size,
            filters = filters,
            rotations = rotations,
            source = source,
            finalizedAt = System.currentTimeMillis(),
        )
    }
}
