package com.github.tomasbjerre.keepsheet.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.tomasbjerre.keepsheet.data.DocumentRepository
import com.github.tomasbjerre.keepsheet.data.DocumentSource
import java.io.File

private const val ROUTE_HOME = "home"
private const val ROUTE_CAPTURE = "capture"
private const val ROUTE_PAGE_REVIEW = "pageReview"
private const val ARG_DOCUMENT_ID = "documentId"
private const val ROUTE_DOCUMENT_DETAIL = "documentDetail/{$ARG_DOCUMENT_ID}"

private fun documentDetailRoute(documentId: Long) = "documentDetail/$documentId"

/** See specs/ui-flows.md#navigation. Merge doesn't exist yet. */
@Composable
fun KeepSheetApp(
    repository: DocumentRepository,
    filesDir: File,
    cacheDir: File,
) {
    val navController = rememberNavController()
    val pending = remember { PendingCapture() }

    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) { HomeRoute(navController, repository, pending) }
        composable(ROUTE_CAPTURE) { CaptureRoute(navController, cacheDir, pending) }
        composable(ROUTE_PAGE_REVIEW) { PageReviewRoute(navController, repository, filesDir, pending) }
        composable(
            ROUTE_DOCUMENT_DETAIL,
            arguments = listOf(navArgument(ARG_DOCUMENT_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong(ARG_DOCUMENT_ID) ?: return@composable
            DocumentDetailScreen(
                documentId = documentId,
                repository = repository,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack(ROUTE_HOME, inclusive = false) },
            )
        }
    }
}

/**
 * Carries a session's pages from Capture/Import through to Page Review. [capturedPages] is
 * hoisted above Capture itself (rather than Capture's own local state) so a session survives
 * navigating to Page Review and back — see specs/ui-flows.md#3-page-review ("returns to
 * Capture with the session's pages intact").
 */
private class PendingCapture {
    var pages by mutableStateOf<List<Uri>>(emptyList())
    var source by mutableStateOf(DocumentSource.IMPORTED)
    var capturedPages by mutableStateOf<List<CapturedPage>>(emptyList())
}

@Composable
private fun HomeRoute(
    navController: NavHostController,
    repository: DocumentRepository,
    pending: PendingCapture,
) {
    HomeScreen(
        repository = repository,
        onScan = { navController.navigate(ROUTE_CAPTURE) },
        onPhotosSelectedForImport = { uris ->
            pending.pages = uris
            pending.source = DocumentSource.IMPORTED
            navController.navigate(ROUTE_PAGE_REVIEW)
        },
        onDocumentSelected = { documentId -> navController.navigate(documentDetailRoute(documentId)) },
    )
}

@Composable
private fun CaptureRoute(
    navController: NavHostController,
    cacheDir: File,
    pending: PendingCapture,
) {
    CaptureScreen(
        pages = pending.capturedPages,
        onPagesChanged = { pending.capturedPages = it },
        cacheDir = cacheDir,
        onDone = { uris ->
            pending.pages = uris
            pending.source = DocumentSource.SCANNED
            navController.navigate(ROUTE_PAGE_REVIEW)
        },
        onCancel = {
            pending.capturedPages = emptyList()
            navController.popBackStack()
        },
    )
}

@Composable
private fun PageReviewRoute(
    navController: NavHostController,
    repository: DocumentRepository,
    filesDir: File,
    pending: PendingCapture,
) {
    PageReviewScreen(
        pages = pending.pages,
        source = pending.source,
        repository = repository,
        filesDir = filesDir,
        onSaved = { documentId ->
            pending.pages = emptyList()
            pending.capturedPages = emptyList()
            navController.navigate(documentDetailRoute(documentId)) {
                popUpTo(ROUTE_HOME) { inclusive = false }
            }
        },
        onCancel = {
            pending.pages = emptyList()
            navController.popBackStack()
        },
    )
}
