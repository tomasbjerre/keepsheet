package com.github.tomasbjerre.keepsheet.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.tomasbjerre.keepsheet.data.Document
import com.github.tomasbjerre.keepsheet.data.DocumentRepository

/**
 * Home screen (see specs/ui-flows.md#1-home). Scan
 * (specs/ui-flows.md#2-capture), Import (specs/ui-flows.md#3-page-review),
 * Merge (specs/ui-flows.md#4-merge), opening a document
 * (specs/ui-flows.md#5-document-detail), and the ⓘ Information dialog
 * (specs/ui-flows.md#feedback-and-support) are all implemented. Each row's
 * own delete action isn't implemented yet — Document Detail's Delete is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: DocumentRepository,
    onScan: () -> Unit,
    onPhotosSelectedForImport: (List<Uri>) -> Unit,
    onDocumentSelected: (Long) -> Unit,
    onMerge: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showInformation by remember { mutableStateOf(false) }

    val documentsFlow =
        remember(query) {
            if (query.isBlank()) repository.observeDocuments() else repository.search(query)
        }
    val documents by documentsFlow.collectAsState(initial = emptyList())

    val photoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) onPhotosSelectedForImport(uris)
        }

    Scaffold(
        topBar = { HomeTopBar(onInfoClick = { showInformation = true }) },
    ) { innerPadding ->
        HomeBody(
            innerPadding = innerPadding,
            query = query,
            onQueryChanged = { query = it },
            documents = documents,
            onScan = onScan,
            onImport = {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onMerge = onMerge,
            onDocumentSelected = onDocumentSelected,
        )
    }

    if (showInformation) {
        InformationDialog(onDismiss = { showInformation = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(onInfoClick: () -> Unit) {
    TopAppBar(
        title = { Text("KeepSheet") },
        actions = {
            IconButton(onClick = onInfoClick) {
                Icon(Icons.Default.Info, contentDescription = "Information")
            }
        },
    )
}

@Suppress("LongParameterList")
@Composable
private fun HomeBody(
    innerPadding: PaddingValues,
    query: String,
    onQueryChanged: (String) -> Unit,
    documents: List<Document>,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onMerge: () -> Unit,
    onDocumentSelected: (Long) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
    ) {
        HomeActionsRow(onScan = onScan, onImport = onImport, onMerge = onMerge)

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = { Text("Search documents") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )

        if (documents.isEmpty()) {
            EmptyState(query)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(documents, key = { it.id }) { document ->
                    DocumentRow(document, onClick = { onDocumentSelected(document.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * See specs/ui-flows.md#feedback-and-support: not a full screen/navigation destination,
 * since a dialog is enough — shows the app version at the top (specs/ui-flows.md#bug_report
 * points people here for exactly that), device model, and Android version, in the same
 * "Model, Android X" format `.github/ISSUE_TEMPLATE/bug_report.yml` asks for, plus links to
 * file an issue and to the user manual.
 */
@Composable
private fun InformationDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull() ?: "unknown"
        }
    val deviceAndAndroidVersion = "${Build.MODEL}, Android ${Build.VERSION.RELEASE}"

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("KeepSheet $versionName") },
        text = {
            Column {
                Text(deviceAndAndroidVersion, style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = { openUrl("https://github.com/tomasbjerre/keepsheet/issues") },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Report a problem or request a feature") }
                TextButton(
                    onClick = {
                        openUrl("https://github.com/tomasbjerre/keepsheet/blob/main/docs/user-manual.md")
                    },
                ) { Text("User manual") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun HomeActionsRow(
    onScan: () -> Unit,
    onImport: () -> Unit,
    onMerge: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onScan, modifier = Modifier.weight(1f)) { Text("Scan") }
        Button(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") }
        Button(onClick = onMerge, modifier = Modifier.weight(1f)) { Text("Merge") }
    }
}

@Composable
private fun EmptyState(query: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (query.isBlank()) {
                "No documents yet — tap Scan to create your first PDF."
            } else {
                "No documents match \"$query\"."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DocumentRow(
    document: Document,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
    ) {
        Text(document.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "${formatDate(document.createdAt)} · ${document.pageCount} pages · ${formatFileSize(document.sizeBytes)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
