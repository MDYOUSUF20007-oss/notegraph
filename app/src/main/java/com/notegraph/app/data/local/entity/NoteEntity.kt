package com.notegraph.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
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
    val templateType: String = "BLANK"
)