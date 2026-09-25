package com.github.tomasbjerre.keepsheet.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.tomasbjerre.keepsheet.data.DocumentRepository
import com.github.tomasbjerre.keepsheet.data.DocumentSource
import java.io.File

private const val ROUTE_HOME = "home"
private const val ROUTE_CAPTURE = "capture"
private const val ROUTE_PAGE_REVIEW = "pageReview"

/** See specs/ui-flows.md#navigation. Merge and Document Detail don't exist yet. */
@Composable
fun KeepSheetApp(
    repository: DocumentRepository,
    filesDir: File,
    cacheDir: File,
) {
    val navController = rememberNavController()
    var pendingPages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingSource by remember { mutableStateOf(DocumentSource.IMPORTED) }
    // Hoisted above Capture itself so a Capture session survives navigating to Page
    // Review and back — see specs/ui-flows.md#3-page-review.
    var capturedPages by remember { mutableStateOf<List<CapturedPage>>(emptyList()) }

    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeScreen(
                repository = repository,
                onScan = { navController.navigate(ROUTE_CAPTURE) },
                onPhotosSelectedForImport = { uris ->
                    pendingPages = uris
                    pendingSource = DocumentSource.IMPORTED
                    navController.navigate(ROUTE_PAGE_REVIEW)
                },
            )
        }
        composable(ROUTE_CAPTURE) {
            CaptureScreen(
                pages = capturedPages,
                onPagesChanged = { capturedPages = it },
                cacheDir = cacheDir,
                onDone = { uris ->
                    pendingPages = uris
                    pendingSource = DocumentSource.SCANNED
                    navController.navigate(ROUTE_PAGE_REVIEW)
                },
                onCancel = {
                    capturedPages = emptyList()
                    navController.popBackStack()
                },
            )
        }
        composable(ROUTE_PAGE_REVIEW) {
            PageReviewScreen(
                pages = pendingPages,
                source = pendingSource,
                repository = repository,
                filesDir = filesDir,
                onSaved = {
                    pendingPages = emptyList()
                    capturedPages = emptyList()
                    // Should land on the new document's Document Detail screen (see
                    // specs/ui-flows.md#3-page-review) — not implemented yet, so this
                    // returns to Home instead, where the new document is now visible.
                    navController.popBackStack(ROUTE_HOME, inclusive = false)
                },
                onCancel = {
                    pendingPages = emptyList()
                    navController.popBackStack()
                },
            )
        }
    }
}
