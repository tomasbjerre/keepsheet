package com.github.tomasbjerre.keepsheet.data

import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Storage-facing operations required by specs/data-model.md#required-queries.
 * Wraps the DAOs so the rest of the app never talks to Room directly.
 */
class DocumentRepository(
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
) {
    fun observeDocuments(): Flow<List<Document>> = documentDao.observeAll()

    fun observeDocument(id: Long): Flow<Document?> = documentDao.observeById(id)

    /** See specs/data-model.md#required-queries and specs/ui-flows.md#1-home. */
    fun search(term: String): Flow<List<Document>> = documentDao.search(term)

    suspend fun getPages(documentId: Long): List<Page> = pageDao.getForDocument(documentId)

    suspend fun createDocument(
        createdAt: Long,
        name: String,
        pdfPath: String,
        source: DocumentSource,
    ): Long =
        documentDao.insert(
            Document(createdAt = createdAt, name = name, pdfPath = pdfPath, source = source),
        )

    /** Appended incrementally during capture — see specs/capture-and-processing.md#multi-page-capture. */
    suspend fun appendPage(
        documentId: Long,
        sequence: Int,
        imagePath: String,
        filter: PageFilter,
    ): Long = pageDao.insert(Page(documentId = documentId, sequence = sequence, imagePath = imagePath, filter = filter))

    /** Called once the PDF is actually built — see specs/ui-flows.md#3-page-review. */
    suspend fun finalizeDocument(
        documentId: Long,
        pageCount: Int,
        sizeBytes: Long,
    ) {
        val document = documentDao.getById(documentId) ?: return
        documentDao.update(document.copy(pageCount = pageCount, sizeBytes = sizeBytes))
    }

    /**
     * Best-effort, set once OCR finishes in the background — see
     * specs/capture-and-processing.md#text-recognition-ocr. Never overwrites a name the
     * user already chose themselves; see specs/file-naming.md#rules — that check is the
     * caller's responsibility (this always sets what it's given).
     */
    suspend fun updateSearchText(
        documentId: Long,
        searchText: String,
    ) {
        val document = documentDao.getById(documentId) ?: return
        documentDao.update(document.copy(searchText = searchText))
    }

    /** See specs/ui-flows.md#5-document-detail — editable in place at any time. */
    suspend fun renameDocument(
        documentId: Long,
        name: String,
    ) {
        val document = documentDao.getById(documentId) ?: return
        documentDao.update(document.copy(name = name))
    }

    /**
     * Deletes the document's row (cascading to its pages' rows) and every
     * underlying file on disk — see specs/data-model.md#required-queries
     * and specs/permissions-and-privacy.md#data-handling: "no orphaned data
     * left behind".
     */
    suspend fun deleteDocument(document: Document) {
        val pages = pageDao.getForDocument(document.id)
        documentDao.delete(document)
        pages.forEach { File(it.imagePath).delete() }
        File(document.pdfPath).delete()
    }

    suspend fun deleteDocumentById(documentId: Long) {
        val document = documentDao.getById(documentId) ?: return
        deleteDocument(document)
    }
}
