package com.github.tomasbjerre.keepsheet.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.github.tomasbjerre.keepsheet.data.Document
import com.github.tomasbjerre.keepsheet.data.DocumentRepository
import com.github.tomasbjerre.keepsheet.data.renameDocument
import com.github.tomasbjerre.keepsheet.pdf.renderPdfPageThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * See specs/ui-flows.md#5-document-detail. Reached from Home's document rows and, once a
 * document is finalized, straight from Page Review's Save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    documentId: Long,
    repository: DocumentRepository,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val document by repository.observeDocument(documentId).collectAsState(initial = null)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // The Flow emits null both before its first load and after the document is deleted
    // (e.g. from Home, once that has its own delete action) — only the latter should
    // navigate away, and only when *this* screen didn't already trigger it below (isDeleting):
    // otherwise the reactive onBack() here would race the explicit onDeleted() call, popping
    // the back stack twice.
    var hasLoaded by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    LaunchedEffect(document) {
        if (document != null) {
            hasLoaded = true
        } else if (hasLoaded && !isDeleting) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            DocumentDetailTopBar(
                onBack = onBack,
                onShare = document?.let { doc -> { coroutineScope.launch { shareDocument(context, doc) } } },
                onDelete = document?.let { { showDeleteConfirm = true } },
            )
        },
    ) { innerPadding ->
        val currentDocument = document
        if (currentDocument == null) {
            LoadingIndicator(Modifier.fillMaxSize().padding(innerPadding))
        } else {
            DocumentDetailBody(
                document = currentDocument,
                repository = repository,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            onConfirm = {
                showDeleteConfirm = false
                isDeleting = true
                coroutineScope.launch {
                    document?.let { repository.deleteDocument(it) }
                    // Explicit, rather than relying on deleteDocument's own suspend calls
                    // to hand back to whatever dispatched this coroutine: onDeleted()
                    // navigates, and NavController requires the main thread for that.
                    withContext(Dispatchers.Main) { onDeleted() }
                }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentDetailTopBar(
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    TopAppBar(
        title = { Text("Document") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onShare ?: {}, enabled = onShare != null) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
            IconButton(onClick = onDelete ?: {}, enabled = onDelete != null) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        },
    )
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(modifier = Modifier.padding(32.dp))
    }
}

@Composable
private fun DocumentDetailBody(
    document: Document,
    repository: DocumentRepository,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        DocumentNameField(document = document, repository = repository)
        Text(
            "${formatDate(document.createdAt)} · ${document.pageCount} pages · ${formatFileSize(document.sizeBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        PagePreviews(pdfPath = document.pdfPath, pageCount = document.pageCount)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentNameField(
    document: Document,
    repository: DocumentRepository,
) {
    val coroutineScope = rememberCoroutineScope()
    // Reseeded only when a different document loads, or when a rename lands from elsewhere
    // (e.g. a future OCR-driven rename) — not on every recomposition, or it would fight
    // with what the user is currently typing.
    var nameInput by remember(document.id, document.name) { mutableStateOf(document.name) }

    fun save() {
        if (nameInput != document.name) {
            coroutineScope.launch { renameDocument(repository, document.id, nameInput) }
        }
    }

    TextField(
        value = nameInput,
        onValueChange = { nameInput = it },
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(DOCUMENT_NAME_FIELD_TEST_TAG)
                .onFocusChanged { if (!it.isFocused) save() },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
    )
}

@Composable
private fun PagePreviews(
    pdfPath: String,
    pageCount: Int,
) {
    val density = LocalDensity.current
    var thumbnails by remember(pdfPath, pageCount) { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(pdfPath, pageCount) {
        val maxDimensionPx = with(density) { PAGE_PREVIEW_SIZE.roundToPx() }
        thumbnails =
            withContext(Dispatchers.IO) {
                runCatching { renderPdfPageThumbnails(File(pdfPath), maxDimensionPx) }.getOrDefault(emptyList())
            }
    }

    if (thumbnails.isEmpty()) {
        LoadingIndicator(Modifier.fillMaxWidth().height(PAGE_PREVIEW_SIZE))
        return
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(thumbnails) { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.height(PAGE_PREVIEW_SIZE).testTag(PAGE_PREVIEW_TEST_TAG),
            )
        }
    }
}

private val PAGE_PREVIEW_SIZE = 200.dp

/** Lets DocumentDetailScreenTest find these without depending on document-specific text. */
const val DOCUMENT_NAME_FIELD_TEST_TAG = "document-name-field"
const val PAGE_PREVIEW_TEST_TAG = "document-page-preview"

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this document?") },
        text = { Text("This can't be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Copies the PDF to a share-friendly location under the app's own cache (declared in
 * res/xml/file_paths.xml) using the document's *current* display name — not whatever its
 * pdfPath happened to be called at finalize time, per specs/data-model.md#document, so a
 * share after a rename uses the new name — then hands it to the platform's share sheet.
 */
private suspend fun shareDocument(
    context: Context,
    document: Document,
) {
    val uri =
        withContext(Dispatchers.IO) {
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val exportFile = File(exportsDir, "${document.name}.pdf")
            File(document.pdfPath).copyTo(exportFile, overwrite = true)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile)
        }
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    // See the same withContext(Dispatchers.Main) note on Save/Delete above — explicit
    // rather than relying on the withContext(Dispatchers.IO) above to hand back correctly.
    withContext(Dispatchers.Main) { context.startActivity(Intent.createChooser(intent, null)) }
}
