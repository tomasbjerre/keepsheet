package com.github.tomasbjerre.keepsheet.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** See specs/capture-and-processing.md#document-filters. */
enum class PageFilter { COLOR, GRAYSCALE, BLACK_AND_WHITE }

/**
 * See specs/data-model.md#page. Not applicable to a `MERGED` document — see
 * specs/merging.md#result.
 */
@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = Document::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class Page(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val sequence: Int,
    val imagePath: String,
    val filter: PageFilter,
    val searchText: String? = null,
)
