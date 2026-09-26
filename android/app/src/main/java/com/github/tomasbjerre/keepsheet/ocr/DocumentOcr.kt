package com.github.tomasbjerre.keepsheet.ocr

import com.github.tomasbjerre.keepsheet.data.DocumentRepository

/**
 * See specs/capture-and-processing.md#text-recognition-ocr: runs after a document is
 * finalized, page by page, storing each page's recognized text and then the document's
 * text (all pages concatenated). Best-effort — a page that fails or has no text just keeps
 * a null `searchText`, and nothing here is ever surfaced to the user as an error.
 *
 * Returns the document's concatenated text, or null if nothing was recognized.
 */
suspend fun recognizeDocument(
    repository: DocumentRepository,
    recognizer: TextRecognizer,
    documentId: Long,
): String? {
    val texts = mutableListOf<String>()
    for (page in repository.getPages(documentId)) {
        val text = runCatching { recognizer.recognize(page.imagePath) }.getOrNull()?.trim()?.ifEmpty { null }
        if (text != null) {
            repository.updatePageSearchText(page.id, text)
            texts += text
        }
    }
    val documentText = texts.joinToString("\n").ifEmpty { null }
    if (documentText != null) {
        repository.updateSearchText(documentId, documentText)
    }
    return documentText
}
