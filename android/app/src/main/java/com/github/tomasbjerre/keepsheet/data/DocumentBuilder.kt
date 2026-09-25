package com.github.tomasbjerre.keepsheet.data

import com.github.tomasbjerre.keepsheet.naming.fallbackDocumentName
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Orchestrates specs/capture-and-processing.md#multi-page-capture and
 * specs/file-naming.md for turning a set of page images into one
 * [Document]: picks a name, creates the document, appends its pages in
 * order, builds the PDF, and finalizes it.
 *
 * The actual image decoding/PDF writing is injected ([importPage] /
 * [buildPdf]) rather than called directly, so this ordering/naming/
 * persistence logic can be unit-tested with fakes, without real Android
 * graphics APIs — see android/README.md#testing. The real implementations
 * live in the `pdf` package.
 */
class DocumentBuilder(
    private val repository: DocumentRepository,
    private val pagesDir: File,
    private val documentsDir: File,
    private val importPage: (sourceIndex: Int, destination: File) -> Unit,
    private val buildPdf: (pageImagePaths: List<String>, destination: File) -> Long,
) {
    suspend fun build(
        pageCount: Int,
        source: DocumentSource,
        finalizedAt: Long,
    ): Long {
        require(pageCount > 0) { "A document needs at least one page." }
        pagesDir.mkdirs()
        documentsDir.mkdirs()

        val existingNames = repository.observeDocuments().first().map { it.name }
        val name = fallbackDocumentName(finalizedAt, existingNames)
        val pdfFile = File(documentsDir, "$name.pdf")

        val documentId = repository.createDocument(finalizedAt, name, pdfFile.absolutePath, source)

        val pageImagePaths = mutableListOf<String>()
        for (index in 0 until pageCount) {
            val pageFile = File(pagesDir, "${documentId}_$index.jpg")
            importPage(index, pageFile)
            repository.appendPage(documentId, index, pageFile.absolutePath, PageFilter.COLOR)
            pageImagePaths += pageFile.absolutePath
        }

        val sizeBytes = buildPdf(pageImagePaths, pdfFile)
        repository.finalizeDocument(documentId, pageCount, sizeBytes)
        return documentId
    }
}
