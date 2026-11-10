package com.notegraph.app.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Cross-OS Sync v2 wire / file payload shared identically by Android, Windows, and macOS.
 */
@Serializable
data class SyncPackageV2(
    val schemaVersion: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val deviceName: String = "",
    val platform: String = "android",
    val folders: List<SyncFolder> = emptyList(),
    val notes: List<SyncNote> = emptyList(),
    val links: List<SyncLink> = emptyList(),
    val tombstones: List<SyncTombstone> = emptyList()
)

@Serializable
data class SyncFolder(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val icon: String = "📁",
    val colorHex: String = "#A855F7",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class SyncNote(
    val id: String,
    val title: String = "Untitled Note",
    val content: String = "",
    val folderId: String? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isLocked: Boolean = false,
    val isTrash: Boolean = false,
    val trashTimestamp: Long? = null,
    val followUpAt: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val templateType: String = "BLANK",
    val blocksJson: String = "[]",
    val board: JsonObject? = null
)

@Serializable
data class SyncLink(
    val sourceId: String,
    val targetId: String? = null,
    val targetTitle: String,
    val isResolved: Boolean = false
)

@Serializable
data class SyncTombstone(
    val id: String,
    val kind: String = "note", // "note" or "folder"
    val deletedAt: Long = 0L
)

/**
 * Per-note Desktop metadata sidecar persisted alongside Room so Desktop board layout,
 * slides, connectors, and CRM follow-up dates survive Android round-trips lossless.
 */
@Serializable
data class DesktopNoteSidecar(
    val followUpAt: String? = null,
    val boardUpdatedAt: Long = 0L,
    val board: JsonObject? = null
)

@Serializable
data class SyncSummary(
    val foldersSynced: Int = 0,
    val notesMerged: Int = 0,
    val newNotesAdded: Int = 0,
    val linksRebuilt: Int = 0,
    val deletionsApplied: Int = 0,
    val remoteDevice: String = ""
)
