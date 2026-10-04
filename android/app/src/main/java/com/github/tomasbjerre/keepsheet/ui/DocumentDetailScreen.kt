package com.github.tomasbjerre.keepsheet.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.github.tomasbjerre.keepsheet.naming.sanitizeForFileName
import com.github.tomasbjerre.keepsheet.pdf.renderPdfPageThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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
    val snackbarHostState = remember { SnackbarHostState() }
    val saveToStorage = rememberSaveToStorage(context, coroutineScope, snackbarHostState)
    val nameEditing = rememberDocumentNameEditing(documentId, document?.name, repository, coroutineScope)

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
                onSave = document?.let { doc -> { saveCurrentlyNamedDocument(nameEditing, saveToStorage, doc) } },
                onDelete = document?.let { { showDeleteConfirm = true } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val currentDocument = document
        if (currentDocument == null) {
            LoadingIndicator(Modifier.fillMaxSize().padding(innerPadding))
        } else {
            DocumentDetailBody(
                document = currentDocument,
                nameInput = nameEditing.nameInput,
                onNameInputChange = nameEditing.onNameInputChange,
                onCommitNameEdit = nameEditing.commit,
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
    onSave: (() -> Unit)?,
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
            IconButton(onClick = onSave ?: {}, enabled = onSave != null) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }
            IconButton(onClick = onDelete ?: {}, enabled = onDelete != null) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        },
    )
}

/** [nameInput] mirrors DocumentNameField's own text — not yet [commit]ted to the database,
 * which happens asynchronously, so it's the only reliable source for "the name as the user
 * currently sees it" (e.g. for Save-to-storage's suggested filename, which a stale
 * `document.name` used to get wrong right after an edit — keepsheet#97). */
private class DocumentNameEditing(
    val nameInput: String,
    val onNameInputChange: (String) -> Unit,
    val commit: () -> Unit,
)

/**
 * See [DocumentNameEditing]. A suggested name arriving from elsewhere (OCR, see
 * specs/file-naming.md) follows into [DocumentNameEditing.nameInput] unless the user is
 * mid-edit — the user's own name wins, and a suggestion never overwrites it anyway.
 */
@Composable
private fun rememberDocumentNameEditing(
    documentId: Long,
    documentName: String?,
    repository: DocumentRepository,
    coroutineScope: CoroutineScope,
): DocumentNameEditing {
    var nameInput by remember(documentId) { mutableStateOf("") }
    var nameEdited by remember(documentId) { mutableStateOf(false) }
    LaunchedEffect(documentName) {
        if (documentName != null && !nameEdited) nameInput = documentName
    }
    return DocumentNameEditing(
        nameInput = nameInput,
        onNameInputChange = {
            nameInput = it
            nameEdited = true
        },
        commit = {
            if (documentName != null && nameInput != documentName) {
                coroutineScope.launch { renameDocument(repository, documentId, nameInput) }
            }
            nameEdited = false
        },
    )
}

/**
 * Suggests [nameEditing]'s live text — sanitized, falling back to [document]'s own name if
 * that sanitizes away to nothing — as the filename, after committing it as a rename too
 * (see [DocumentNameEditing]). Used by Save-to-storage's button (keepsheet#97).
 */
private fun saveCurrentlyNamedDocument(
    nameEditing: DocumentNameEditing,
    saveToStorage: (Document) -> Unit,
    document: Document,
) {
    nameEditing.commit()
    val sanitized = sanitizeForFileName(nameEditing.nameInput)
    saveToStorage(if (sanitized.isNotBlank()) document.copy(name = sanitized) else document)
}

/**
 * Sets up the SAF "Save" picker (see specs/ui-flows.md#5-document-detail) and returns a
 * function that launches it for a given document, suggesting its current name as the
 * filename. The picked [Uri] only comes back through [rememberLauncherForActivityResult]'s
 * callback, by which point `document` (DocumentDetailScreen's own observed state) may have
 * moved on to a rename or a different document entirely — so which document to write is
 * captured here, at launch time, rather than re-read from there.
 */
@Composable
private fun rememberSaveToStorage(
    context: Context,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
): (Document) -> Unit {
    var documentPendingSave by remember { mutableStateOf<Document?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            val doc = documentPendingSave
            if (uri != null && doc != null) {
                coroutineScope.launch {
                    val message =
                        runCatching { saveDocumentToUri(context, doc, uri) }
                            .fold(
                                onSuccess = { "Saved." },
                                onFailure = { "Couldn't save — try again." },
                            )
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    return { doc ->
        documentPendingSave = doc
        launcher.launch("${doc.name}.pdf")
    }
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
    nameInput: String,
    onNameInputChange: (String) -> Unit,
    onCommitNameEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        DocumentNameField(
            nameInput = nameInput,
            onNameInputChange = onNameInputChange,
            onCommitNameEdit = onCommitNameEdit,
        )
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
    nameInput: String,
    onNameInputChange: (String) -> Unit,
    onCommitNameEdit: () -> Unit,
) {
    TextField(
        value = nameInput,
        onValueChange = onNameInputChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(DOCUMENT_NAME_FIELD_TEST_TAG)
                .onFocusChanged { if (!it.isFocused) onCommitNameEdit() },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommitNameEdit() }),
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

/**
 * Writes the PDF to [uri] — a location the user picked via the platform's document picker
 * (see specs/ui-flows.md#5-document-detail's Save) — rather than handing it to another app
 * like [shareDocument] does. [uri] may be on removable/SD-card storage, so this always goes
 * through [android.content.ContentResolver] rather than java.io.File, the only API that
 * resolves such a destination correctly.
 */
private suspend fun saveDocumentToUri(
    context: Context,
    document: Document,
    uri: Uri,
) {
    withContext(Dispatchers.IO) {
        val output =
            context.contentResolver.openOutputStream(uri)
                ?: throw IOException("contentResolver couldn't open an output stream for $uri")
        output.use { File(document.pdfPath).inputStream().use { input -> input.copyTo(it) } }
    }
}
