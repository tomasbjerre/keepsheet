package com.github.tomasbjerre.keepsheet.data

import com.github.tomasbjerre.keepsheet.naming.sanitizeForFileName
import com.github.tomasbjerre.keepsheet.naming.suggestedDocumentName
import com.github.tomasbjerre.keepsheet.naming.uniqueName
import kotlinx.coroutines.flow.first

/**
 * See specs/file-naming.md#rules and specs/ui-flows.md#5-document-detail: a user-entered
 * rename is sanitized and de-duplicated against every other document's name the same way an
 * auto-suggested name is — "doing so never affects any other field". A blank/all-illegal
 * name is ignored rather than clearing the document's name to nothing.
 *
 * Marks the document as user-named, so a later automatic (OCR-driven) rename never
 * overwrites it — see [applySuggestedName].
 */
suspend fun renameDocument(
    repository: DocumentRepository,
    documentId: Long,
    desiredName: String,
) {
    val sanitized = sanitizeForFileName(desiredName)
    if (sanitized.isBlank()) return
    val existingNames =
        repository
            .observeDocuments()
            .first()
            .filter { it.id != documentId }
            .map { it.name }
    repository.renameDocument(documentId, uniqueName(sanitized, existingNames))
}

/**
 * See specs/file-naming.md#rules: once OCR has finished, silently replaces the fallback
 * name with one built from the recognized [text] — unless the user already renamed the
 * document. Collisions get a numeric suffix like any other name.
 */
suspend fun applySuggestedName(
    repository: DocumentRepository,
    documentId: Long,
    text: String,
    finalizedAt: Long,
) {
    val existingNames =
        repository
            .observeDocuments()
            .first()
            .filter { it.id != documentId }
            .map { it.name }
    val name = suggestedDocumentName(text, finalizedAt, existingNames)
    repository.applySuggestedName(documentId, name)
}
