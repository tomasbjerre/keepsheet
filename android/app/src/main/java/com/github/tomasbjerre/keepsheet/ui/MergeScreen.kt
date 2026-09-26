package com.github.tomasbjerre.keepsheet.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.tomasbjerre.keepsheet.data.Document
import com.github.tomasbjerre.keepsheet.data.DocumentMerger
import com.github.tomasbjerre.keepsheet.data.DocumentRepository
import com.github.tomasbjerre.keepsheet.pdf.countPdfPages
import com.github.tomasbjerre.keepsheet.pdf.mergePdfs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** One PDF staged for merging — from KeepSheet's own document list or picked from
 * device storage — before the user confirms. See specs/merging.md#selecting-files-and-order. */
private data class MergeSource(
    val id: String,
    val uri: Uri,
    val label: String,
)

/**
 * See specs/ui-flows.md#4-merge and specs/merging.md. The selection list is plain local
 * state, not hoisted like Capture's — per spec "an in-progress selection isn't persisted".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(
    repository: DocumentRepository,
    filesDir: File,
    onMerged: (documentId: Long) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<MergeSource>>(emptyList()) }
    var showKeepSheetPicker by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }

    val devicePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val alreadyAdded = sources.map { it.uri }.toSet()
            sources = sources + uris.filterNot { it in alreadyAdded }.map { it.toMergeSource(context.contentResolver) }
        }

    Scaffold(topBar = { MergeTopBar(onBack = onCancel) }) { innerPadding ->
        MergeScreenBody(
            innerPadding = innerPadding,
            sources = sources,
            onReorder = { sources = it },
            onRemove = { id -> sources = sources.filterNot { it.id == id } },
            onAddFromKeepSheet = { showKeepSheetPicker = true },
            onAddFromDevice = { devicePickerLauncher.launch(arrayOf("application/pdf")) },
            merging = merging,
            onMerge = {
                merging = true
                coroutineScope.launch {
                    val documentId = mergeAndSave(sources.map { it.uri }, repository, filesDir, context.contentResolver)
                    // See PageReviewScreen's saveAsDocument note: explicit, since onMerged()
                    // navigates and NavController requires the main thread for that.
                    withContext(Dispatchers.Main) {
                        merging = false
                        onMerged(documentId)
                    }
                }
            },
        )
    }

    if (showKeepSheetPicker) {
        AddFromKeepSheetDialog(
            repository = repository,
            alreadyAdded = sources.map { it.uri }.toSet(),
            onAdd = { document -> sources = sources + document.toMergeSource() },
            onDismiss = { showKeepSheetPicker = false },
        )
    }
}

private fun Uri.toMergeSource(resolver: ContentResolver) =
    MergeSource(id = UUID.randomUUID().toString(), uri = this, label = displayNameFor(resolver, this))

private fun Document.toMergeSource(): MergeSource {
    val uri = Uri.fromFile(File(pdfPath))
    return MergeSource(id = UUID.randomUUID().toString(), uri = uri, label = name)
}

private fun displayNameFor(
    resolver: ContentResolver,
    uri: Uri,
): String {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)?.let { return it }
        }
    }
    return uri.lastPathSegment ?: "PDF"
}

private suspend fun mergeAndSave(
    sources: List<Uri>,
    repository: DocumentRepository,
    filesDir: File,
    contentResolver: ContentResolver,
): Long {
    val merger =
        DocumentMerger(
            repository = repository,
            documentsDir = File(filesDir, "documents"),
            mergePdfs = { srcs, destination -> mergePdfs(contentResolver, srcs, destination) },
            countPages = { file -> countPdfPages(file) },
        )
    return withContext(Dispatchers.IO) {
        merger.merge(sources = sources, mergedAt = System.currentTimeMillis())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("Merge") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

@Suppress("LongParameterList")
@Composable
private fun MergeScreenBody(
    innerPadding: PaddingValues,
    sources: List<MergeSource>,
    onReorder: (List<MergeSource>) -> Unit,
    onRemove: (String) -> Unit,
    onAddFromKeepSheet: () -> Unit,
    onAddFromDevice: () -> Unit,
    merging: Boolean,
    onMerge: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddFromKeepSheet, modifier = Modifier.weight(1f)) { Text("From KeepSheet") }
            OutlinedButton(onClick = onAddFromDevice, modifier = Modifier.weight(1f)) { Text("From device") }
        }

        if (sources.isEmpty()) {
            Text(
                "No files selected yet — add at least two PDFs to merge.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            MergeSourceList(
                sources = sources,
                onReorder = onReorder,
                onRemove = onRemove,
                modifier = Modifier.weight(1f).padding(top = 16.dp),
            )
        }

        Button(
            onClick = onMerge,
            enabled = sources.size >= 2 && !merging,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag(MERGE_ACTION_TEST_TAG),
        ) {
            if (merging) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Merge")
        }
    }
}

private val ROW_HEIGHT = 64.dp

/** Lets MergeScreenTest count staged sources without depending on their (dynamic) ids. */
const val MERGE_ROW_TEST_TAG = "merge-source-row"

/** Disambiguates the Merge action button from this screen's own "Merge" title. */
const val MERGE_ACTION_TEST_TAG = "merge-action-button"

/** One row in the "Add from KeepSheet" picker dialog — disambiguates from the (same-named)
 * running merge list row underneath, which stays composed while the dialog is open. */
const val MERGE_PICKER_ROW_TEST_TAG = "merge-picker-row"

@Composable
private fun MergeSourceList(
    sources: List<MergeSource>,
    onReorder: (List<MergeSource>) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val itemExtentPx = with(density) { ROW_HEIGHT.toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        itemsIndexed(sources, key = { _, source -> source.id }) { _, source ->
            val isDragging = source.id == draggingId
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .testTag(MERGE_ROW_TEST_TAG)
                        .graphicsLayer { translationY = if (isDragging) dragOffsetPx else 0f }
                        .zIndex(if (isDragging) 1f else 0f)
                        .pointerInput(source.id, sources) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = source.id
                                    dragOffsetPx = 0f
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffsetPx = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffsetPx = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val result = computeRowDrag(dragAmount, dragOffsetPx, source, sources, itemExtentPx)
                                    dragOffsetPx = result.offsetPx
                                    result.reordered?.let(onReorder)
                                },
                            )
                        },
            ) {
                MergeSourceRow(source = source, onRemove = { onRemove(source.id) })
            }
        }
    }
}

private data class RowDragResult(
    val offsetPx: Float,
    val reordered: List<MergeSource>?,
)

/** Pure, for the same reason CaptureScreen's computeThumbnailDrag is — see there. */
private fun computeRowDrag(
    dragAmount: Offset,
    currentOffsetPx: Float,
    source: MergeSource,
    sources: List<MergeSource>,
    itemExtentPx: Float,
): RowDragResult {
    val newOffset = currentOffsetPx + dragAmount.y
    val currentIndex = sources.indexOfFirst { it.id == source.id }
    if (currentIndex == -1) return RowDragResult(newOffset, null)
    val slotShift = (newOffset / itemExtentPx).roundToInt()
    val targetIndex = (currentIndex + slotShift).coerceIn(0, sources.lastIndex)
    if (targetIndex == currentIndex) return RowDragResult(newOffset, null)
    val reordered = sources.toMutableList()
    val moved = reordered.removeAt(currentIndex)
    reordered.add(targetIndex, moved)
    return RowDragResult(newOffset - (targetIndex - currentIndex) * itemExtentPx, reordered)
}

@Composable
private fun MergeSourceRow(
    source: MergeSource,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            source.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Remove")
        }
    }
}

@Composable
private fun AddFromKeepSheetDialog(
    repository: DocumentRepository,
    alreadyAdded: Set<Uri>,
    onAdd: (Document) -> Unit,
    onDismiss: () -> Unit,
) {
    val documents by repository.observeDocuments().collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add from KeepSheet") },
        text = {
            if (documents.isEmpty()) {
                Text("No documents in this session yet.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(documents, key = { it.id }) { document ->
                        KeepSheetDocumentRow(
                            document = document,
                            alreadyAdded = Uri.fromFile(File(document.pdfPath)) in alreadyAdded,
                            onClick = { onAdd(document) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun KeepSheetDocumentRow(
    document: Document,
    alreadyAdded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(MERGE_PICKER_ROW_TEST_TAG)
                .clickable(enabled = !alreadyAdded, onClick = onClick)
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(document.name, modifier = Modifier.weight(1f), maxLines = 1)
        if (alreadyAdded) {
            Icon(Icons.Default.Check, contentDescription = "Already added")
        }
    }
}
