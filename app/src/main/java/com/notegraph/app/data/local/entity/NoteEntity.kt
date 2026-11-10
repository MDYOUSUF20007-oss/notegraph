package com.notegraph.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["folderId"], name = "index_notes_folderId"),
        Index(value = ["isTrash"], name = "index_notes_isTrash"),
        Index(value = ["updatedAt"], name = "index_notes_updatedAt")
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val folderId: String?,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isLocked: Boolean = false,
    val isTrash: Boolean = false,
    val trashTimestamp: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val templateType: String = "BLANK",
    val blocksJson: String = "[]"
)
