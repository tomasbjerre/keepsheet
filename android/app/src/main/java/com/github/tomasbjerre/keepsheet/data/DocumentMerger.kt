package com.github.tomasbjerre.keepsheet.data

import android.net.Uri
import com.github.tomasbjerre.keepsheet.naming.fallbackDocumentName
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Orchestrates specs/merging.md and specs/file-naming.md for turning a set of existing
 * PDFs, in a chosen order, into one `MERGED` [Document]: picks a name, merges the source
 * PDFs, creates the document, and finalizes it. No [Page] rows are created — see
 * specs/data-model.md#page ("merging combines existing PDFs' pages directly, without
 * re-deriving this per-page record").
 *
 * The actual PDF merging/page-counting is injected ([mergePdfs] / [countPages]) rather
 * than called directly, so this naming/persistence logic can be unit-tested with fakes,
 * without real Android graphics APIs — see android/README.md#testing and
 * DocumentBuilder, which does the same for capture. The real implementations live in
 * the `pdf` package.
 */
class DocumentMerger(
    private val repository: DocumentRepository,
    private val documentsDir: File,
    private val mergePdfs: (sources: List<Uri>, destination: File) -> Long,
    private val countPages: (destination: File) -> Int,
) {
    suspend fun merge(
        sources: List<Uri>,
        mergedAt: Long,
    ): Long {
        require(sources.size >= 2) { "Merging needs at least two files." }
        documentsDir.mkdirs()

        val existingNames = repository.observeDocuments().first().map { it.name }
        val name = fallbackDocumentName(mergedAt, existingNames)
        val pdfFile = File(documentsDir, "$name.pdf")

        val sizeBytes = mergePdfs(sources, pdfFile)
        val documentId = repository.createDocument(mergedAt, name, pdfFile.absolutePath, DocumentSource.MERGED)
        repository.finalizeDocument(documentId, countPages(pdfFile), sizeBytes)
        return documentId
    }
}
