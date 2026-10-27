package com.notegraph.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "links")
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceNoteId: String,
    val targetNoteTitle: String,
    val targetNoteId: String?,
    val isResolved: Boolean
)