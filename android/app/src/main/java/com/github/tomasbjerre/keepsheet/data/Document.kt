package com.github.tomasbjerre.keepsheet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** See specs/data-model.md#document — how a document was produced. */
enum class DocumentSource { SCANNED, IMPORTED, MERGED }

/**
 * See specs/data-model.md#document. `pageCount`/`sizeBytes` are stored (not
 * recomputed on read) so the document list stays cheap to render.
 */
@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val name: String,
    val pdfPath: String,
    val pageCount: Int = 0,
    val sizeBytes: Long = 0,
    val searchText: String? = null,
    val source: DocumentSource,
)
