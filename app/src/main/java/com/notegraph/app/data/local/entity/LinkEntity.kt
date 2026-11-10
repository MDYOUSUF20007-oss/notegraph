package com.notegraph.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "links",
    indices = [
        Index(value = ["sourceNoteId"], name = "index_links_sourceNoteId"),
        Index(value = ["targetNoteId"], name = "index_links_targetNoteId"),
        Index(value = ["targetNoteTitle"], name = "index_links_targetNoteTitle")
    ]
)
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceNoteId: String,
    val targetNoteTitle: String,
    val targetNoteId: String?,
    val isResolved: Boolean
)