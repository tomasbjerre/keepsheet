package com.github.tomasbjerre.keepsheet.data

import com.github.tomasbjerre.keepsheet.naming.sanitizeForFileName
import com.github.tomasbjerre.keepsheet.naming.uniqueName
import kotlinx.coroutines.flow.first

/**
 * See specs/file-naming.md#rules and specs/ui-flows.md#5-document-detail: a user-entered
 * rename is sanitized and de-duplicated against every other document's name the same way an
 * auto-suggested name is — "doing so never affects any other field". A blank/all-illegal
 * name is ignored rather than clearing the document's name to nothing.
 *
 * Not yet handled: specs/file-naming.md#rules also says an automatic (OCR-driven) rename
 * must never overwrite a name the user already chose — there's no such flag on [Document]
 * yet because OCR-driven renaming isn't implemented (see
 * specs/file-naming.md, still to do).
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
