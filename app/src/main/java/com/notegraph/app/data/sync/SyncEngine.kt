package com.notegraph.app.data.sync

import com.notegraph.app.data.local.entity.ChecklistItem
import com.notegraph.app.data.local.entity.FolderEntity
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.LinkEntity
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.data.local.entity.PdfPageAnnotation
import com.notegraph.app.data.local.entity.StrokePoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Pure JVM/Kotlin Cross-OS Sync Engine implementing deterministic Last-Write-Wins (LWW)
 * conflict resolution, deletion tombstones, bidirectional Desktop board <-> Android block
 * translation, and automatic Knowledge Graph link rebuilding.
 */
object SyncEngine {

    val codec: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    private const val TOMBSTONE_TTL_MS = 90L * 24L * 60L * 60L * 1000L
    private const val MAX_TOMBSTONES = 500
    private val WIKI_LINK_REGEX = """\[\[(.*?)]]""".toRegex()

    data class MergeResult(
        val folders: List<FolderEntity>,
        val notes: List<NoteEntity>,
        val links: List<LinkEntity>,
        val tombstones: List<SyncTombstone>,
        val sidecar: Map<String, DesktopNoteSidecar>,
        val deletedFolderIds: Set<String>,
        val deletedNoteIds: Set<String>,
        val summary: SyncSummary
    )

    fun parsePackageOrLegacy(rawJson: String): SyncPackageV2 {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return SyncPackageV2()
        if (trimmed.startsWith("[")) {
            val legacyNotes = codec.decodeFromString<List<NoteEntity>>(trimmed)
            return SyncPackageV2(
                schemaVersion = 1,
                platform = "legacy",
                notes = legacyNotes.map { it.toSyncNote(sidecar = null) }
            )
        }
        return codec.decodeFromString<SyncPackageV2>(trimmed)
    }

    fun mergePackages(
        localFolders: List<FolderEntity>,
        localNotes: List<NoteEntity>,
        localTombstones: List<SyncTombstone>,
        localSidecar: Map<String, DesktopNoteSidecar>,
        remote: SyncPackageV2,
        nowMs: Long = System.currentTimeMillis()
    ): MergeResult {
        // 1. Merge tombstones (max deletedAt per kind::id)
        val mergedTombstoneMap = LinkedHashMap<String, SyncTombstone>()
        fun recordTomb(t: SyncTombstone) {
            val normKind = if (t.kind == "folder") "folder" else "note"
            if (t.id.isBlank()) return
            val key = "$normKind::${t.id}"
            val existing = mergedTombstoneMap[key]
            if (existing == null || t.deletedAt > existing.deletedAt) {
                mergedTombstoneMap[key] = t.copy(kind = normKind)
            }
        }
        localTombstones.forEach(::recordTomb)
        remote.tombstones.forEach(::recordTomb)

        // 2. Merge folders with LWW + tombstone dominance
        val localFolderMap = localFolders.associateBy { it.id }
        val remoteFolderMap = remote.folders.associateBy { it.id }
        val allFolderIds = LinkedHashSet<String>().apply {
            addAll(localFolderMap.keys)
            addAll(remoteFolderMap.keys)
        }

        val mergedFoldersList = mutableListOf<FolderEntity>()
        val deletedFolderIds = mutableSetOf<String>()

        for (fid in allFolderIds) {
            val lf = localFolderMap[fid]?.toSyncFolder()
            val rf = remoteFolderMap[fid]
            val winner = pickSyncFolderWinner(lf, rf) ?: continue
            val winTs = maxOf(winner.updatedAt, winner.createdAt)
            val tomb = mergedTombstoneMap["folder::$fid"]
            if (tomb != null && tomb.deletedAt >= winTs) {
                if (lf != null) deletedFolderIds.add(fid)
                continue
            }
            if (tomb != null && winTs > tomb.deletedAt) {
                // Recreated after tombstone -> supersede tombstone
                mergedTombstoneMap.remove("folder::$fid")
            }
            mergedFoldersList.add(
                FolderEntity(
                    id = winner.id,
                    name = winner.name.ifBlank { "Folder" },
                    parentId = winner.parentId,
                    icon = winner.icon.ifBlank { "📁" },
                    colorHex = winner.colorHex.ifBlank { "#A855F7" },
                    createdAt = winner.createdAt,
                    updatedAt = winner.updatedAt
                )
            )
        }

        val validFolderIds = mergedFoldersList.map { it.id }.toHashSet()
        val normalizedFolders = mergedFoldersList.map { f ->
            if (f.parentId != null && !validFolderIds.contains(f.parentId)) {
                f.copy(parentId = null)
            } else {
                f
            }
        }.sortedWith(compareBy<FolderEntity> { it.createdAt }.thenBy { it.name })

        // 3. Merge notes with LWW + tombstone dominance + Desktop board/followUp sidecar
        val localNoteMap = localNotes.associateBy { it.id }
        val remoteNoteMap = remote.notes.associateBy { it.id }
        val allNoteIds = LinkedHashSet<String>().apply {
            addAll(localNoteMap.keys)
            addAll(remoteNoteMap.keys)
        }

        val mergedNotesList = mutableListOf<NoteEntity>()
        val nextSidecar = LinkedHashMap<String, DesktopNoteSidecar>()
        val deletedNoteIds = mutableSetOf<String>()
        var newNotesAdded = 0
        var notesMerged = 0

        for (nid in allNoteIds) {
            val localEntity = localNoteMap[nid]
            val lSidecar = localSidecar[nid]
            val ln = localEntity?.toSyncNote(lSidecar)
            val rn = remoteNoteMap[nid]

            val (winner, remoteWon) = pickSyncNoteWinner(ln, rn) ?: continue
            val winTs = maxOf(winner.updatedAt, winner.createdAt)
            val tomb = mergedTombstoneMap["note::$nid"]
            if (tomb != null && tomb.deletedAt >= winTs) {
                if (localEntity != null) deletedNoteIds.add(nid)
                continue
            }
            if (tomb != null && winTs > tomb.deletedAt) {
                mergedTombstoneMap.remove("note::$nid")
            }

            if (localEntity == null && rn != null) {
                newNotesAdded++
            } else if (localEntity != null && rn != null) {
                notesMerged++
            }

            val resolvedFolderId = winner.folderId?.takeIf { validFolderIds.contains(it) }
            val resolvedBlocksJson = resolveMergedBlocksJson(winner)

            val entity = NoteEntity(
                id = winner.id,
                title = winner.title.ifBlank { "Untitled Note" },
                content = winner.content,
                folderId = resolvedFolderId,
                isPinned = winner.isPinned,
                isFavorite = winner.isFavorite,
                isLocked = winner.isLocked,
                isTrash = winner.isTrash,
                trashTimestamp = winner.trashTimestamp,
                createdAt = if (winner.createdAt > 0L) winner.createdAt else nowMs,
                updatedAt = if (winner.updatedAt > 0L) winner.updatedAt else nowMs,
                templateType = winner.templateType.ifBlank { "BLANK" },
                blocksJson = resolvedBlocksJson
            )
            mergedNotesList.add(entity)

            // Maintain Desktop sidecar (board + followUpAt)
            val otherNote = if (remoteWon) ln else rn
            val followUp = winner.followUpAt ?: otherNote?.followUpAt
            val chosenBoard = when {
                winner.board != null -> winner.board
                // Only reuse loser board if Android hasn't edited newer blocks after that board
                otherNote?.board != null && winner.updatedAt <= maxOf(otherNote.updatedAt, otherNote.createdAt) -> otherNote.board
                else -> null
            }
            if (chosenBoard != null || !followUp.isNullOrBlank()) {
                val boardUpdatedAt = if (winner.board != null) winTs else (lSidecar?.boardUpdatedAt ?: winTs)
                nextSidecar[nid] = DesktopNoteSidecar(
                    followUpAt = followUp,
                    boardUpdatedAt = boardUpdatedAt,
                    board = chosenBoard
                )
            }
        }

        val sortedNotes = mergedNotesList.sortedWith(
            compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.updatedAt }
        )

        // 4. Rebuild Knowledge Graph links across active (non-trash) merged notes
        val activeNotes = sortedNotes.filter { !it.isTrash }
        val rebuiltLinks = extractLinks(activeNotes)

        // 5. Retain bounded recent tombstones
        val cutoff = nowMs - TOMBSTONE_TTL_MS
        val boundedTombstones = mergedTombstoneMap.values
            .filter { it.deletedAt >= cutoff }
            .sortedByDescending { it.deletedAt }
            .take(MAX_TOMBSTONES)

        return MergeResult(
            folders = normalizedFolders,
            notes = sortedNotes,
            links = rebuiltLinks,
            tombstones = boundedTombstones,
            sidecar = nextSidecar,
            deletedFolderIds = deletedFolderIds,
            deletedNoteIds = deletedNoteIds,
            summary = SyncSummary(
                foldersSynced = normalizedFolders.size,
                notesMerged = notesMerged,
                newNotesAdded = newNotesAdded,
                linksRebuilt = rebuiltLinks.size,
                deletionsApplied = deletedFolderIds.size + deletedNoteIds.size,
                remoteDevice = remote.deviceName.ifBlank { remote.platform }
            )
        )
    }

    fun FolderEntity.toSyncFolder(): SyncFolder = SyncFolder(
        id = id,
        name = name,
        parentId = parentId,
        icon = icon,
        colorHex = colorHex,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun NoteEntity.toSyncNote(sidecar: DesktopNoteSidecar?): SyncNote {
        val androidEditedAfterBoard = sidecar != null && updatedAt > sidecar.boardUpdatedAt
        val boardToSend = if (sidecar?.board != null && !androidEditedAfterBoard) sidecar.board else null
        return SyncNote(
            id = id,
            title = title,
            content = content,
            folderId = folderId,
            isPinned = isPinned,
            isFavorite = isFavorite,
            isLocked = isLocked,
            isTrash = isTrash,
            trashTimestamp = trashTimestamp,
            followUpAt = sidecar?.followUpAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            templateType = templateType,
            blocksJson = blocksJson,
            board = boardToSend
        )
    }

    fun extractLinks(activeNotes: List<NoteEntity>): List<LinkEntity> {
        if (activeNotes.isEmpty()) return emptyList()
        val noteByLowerTitle = HashMap<String, NoteEntity>(activeNotes.size * 2)
        for (n in activeNotes) {
            val clean = n.title.trim().lowercase()
            if (clean.isNotEmpty() && !noteByLowerTitle.containsKey(clean)) {
                noteByLowerTitle[clean] = n
            }
        }

        val mentionRegexes = ArrayList<Pair<NoteEntity, Regex>>()
        for (other in activeNotes) {
            val t = other.title.trim()
            if (t.length >= 2 &&
                !t.equals("Untitled", ignoreCase = true) &&
                !t.equals("Untitled Note", ignoreCase = true) &&
                !t.equals("Untitled Board", ignoreCase = true)
            ) {
                mentionRegexes.add(other to Regex("""(?i)\b${Regex.escape(t)}\b"""))
            }
        }

        val result = mutableListOf<LinkEntity>()
        for (src in activeNotes) {
            val fullText = buildCombinedText(src)
            if (fullText.isBlank()) continue
            val seenTargets = HashSet<String>()

            // 1. [[Wiki-Links]]
            WIKI_LINK_REGEX.findAll(fullText).forEach { match ->
                val rawTarget = match.groupValues[1].trim()
                if (rawTarget.isNotBlank()) {
                    val key = rawTarget.lowercase()
                    if (seenTargets.add(key)) {
                        val resolved = noteByLowerTitle[key]
                        if (resolved == null || resolved.id != src.id) {
                            result.add(
                                LinkEntity(
                                    sourceNoteId = src.id,
                                    targetNoteTitle = resolved?.title ?: rawTarget,
                                    targetNoteId = resolved?.id,
                                    isResolved = resolved != null
                                )
                            )
                        }
                    }
                }
            }

            // 2. Title mentions
            for ((other, regex) in mentionRegexes) {
                if (other.id == src.id) continue
                val clean = other.title.trim()
                val key = clean.lowercase()
                if (!seenTargets.contains(key) &&
                    fullText.contains(clean, ignoreCase = true) &&
                    regex.containsMatchIn(fullText)
                ) {
                    seenTargets.add(key)
                    result.add(
                        LinkEntity(
                            sourceNoteId = src.id,
                            targetNoteTitle = other.title,
                            targetNoteId = other.id,
                            isResolved = true
                        )
                    )
                }
            }
        }
        return result
    }

    private fun buildCombinedText(note: NoteEntity): String {
        val sb = StringBuilder(note.content.length + 128)
        sb.append(note.content)
        val blocks = decodeBlocksSafe(note.blocksJson)
        for (blk in blocks) {
            when (blk) {
                is NoteBlock.TextBlock -> if (blk.text.isNotBlank()) sb.append('\n').append(blk.text)
                is NoteBlock.ImageBlock -> if (!blk.extractedOcrText.isNullOrBlank()) sb.append('\n').append(blk.extractedOcrText)
                is NoteBlock.ChecklistBlock -> {
                    if (blk.title.isNotBlank()) sb.append('\n').append(blk.title)
                    for (item in blk.items) {
                        if (item.text.isNotBlank()) sb.append('\n').append(item.text)
                    }
                }
                is NoteBlock.PdfBlock -> {
                    if (blk.fileName.isNotBlank()) sb.append('\n').append(blk.fileName)
                    val anns = try {
                        codec.decodeFromString<List<PdfPageAnnotation>>(blk.annotationsJson)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    for (ann in anns) {
                        if (ann.pageNote.isNotBlank()) sb.append('\n').append(ann.pageNote)
                    }
                }
                else -> Unit
            }
        }
        return sb.toString()
    }

    private fun pickSyncFolderWinner(local: SyncFolder?, remote: SyncFolder?): SyncFolder? {
        if (local == null) return remote
        if (remote == null) return local
        val lTs = maxOf(local.updatedAt, local.createdAt)
        val rTs = maxOf(remote.updatedAt, remote.createdAt)
        if (rTs > lTs) return remote
        if (lTs > rTs) return local
        // Symmetric deterministic tie-breaker on identical timestamp
        val lSig = "${local.name}|${local.icon}|${local.colorHex}|${local.parentId.orEmpty()}"
        val rSig = "${remote.name}|${remote.icon}|${remote.colorHex}|${remote.parentId.orEmpty()}"
        return if (rSig > lSig) remote else local
    }

    private fun pickSyncNoteWinner(local: SyncNote?, remote: SyncNote?): Pair<SyncNote, Boolean>? {
        if (local == null && remote == null) return null
        if (local == null) return remote!! to true
        if (remote == null) return local to false
        val lTs = maxOf(local.updatedAt, local.createdAt)
        val rTs = maxOf(remote.updatedAt, remote.createdAt)
        if (rTs > lTs) return remote to true
        if (lTs > rTs) return local to false
        // Symmetric deterministic tie-breaker on identical timestamp
        val lSig = "${local.title}|${local.content}|${local.blocksJson}|${local.isTrash}|${local.isPinned}"
        val rSig = "${remote.title}|${remote.content}|${remote.blocksJson}|${remote.isTrash}|${remote.isPinned}"
        return if (rSig > lSig) (remote to true) else (local to false)
    }

    private fun resolveMergedBlocksJson(note: SyncNote): String {
        val decoded = decodeBlocksSafe(note.blocksJson)
        if (decoded.isNotEmpty()) {
            return codec.encodeToString(decoded)
        }
        val board = note.board ?: return "[]"
        val converted = boardJsonToBlocks(note.id, board)
        return codec.encodeToString(converted)
    }

    fun boardJsonToBlocks(noteId: String, board: JsonObject): List<NoteBlock> {
        val pagesElement: JsonElement = board["pages"] ?: return emptyList()
        val pages = (pagesElement as? JsonArray) ?: return emptyList()
        val blocks = mutableListOf<NoteBlock>()
        val inkStrokes = mutableListOf<InkStroke>()

        for (pageEl in pages) {
            val pageObj = pageEl as? JsonObject ?: continue
            val cards = (pageObj["cards"] as? JsonArray).orEmpty()
            for (cardEl in cards) {
                val card = cardEl as? JsonObject ?: continue
                val kind = card["kind"]?.jsonPrimitive?.contentOrNull ?: continue
                val cardId = card["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString()
                when (kind) {
                    "checklist" -> {
                        val title = card["title"]?.jsonPrimitive?.contentOrNull ?: "To-Do Checklist"
                        val items = (card["items"] as? JsonArray).orEmpty().mapNotNull { itEl ->
                            val itObj = itEl as? JsonObject ?: return@mapNotNull null
                            ChecklistItem(
                                id = itObj["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString(),
                                text = itObj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                isChecked = itObj["isChecked"]?.jsonPrimitive?.booleanOrNull ?: false
                            )
                        }
                        blocks.add(NoteBlock.ChecklistBlock(id = cardId, title = title, items = items))
                    }
                    "note" -> {
                        if (cardId != "main_$noteId") {
                            val cTitle = card["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val cBody = card["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val combined = if (cTitle.isNotBlank() && cTitle != "Text Block") {
                                if (cBody.isNotBlank()) "$cTitle\n$cBody" else cTitle
                            } else {
                                cBody
                            }
                            if (combined.isNotBlank()) {
                                blocks.add(NoteBlock.TextBlock(id = cardId, text = combined))
                            }
                        }
                    }
                }
            }

            val strokesArr = (pageObj["strokes"] as? JsonArray).orEmpty()
            for (sEl in strokesArr) {
                val sObj = sEl as? JsonObject ?: continue
                val pts = (sObj["points"] as? JsonArray).orEmpty().mapNotNull { ptEl ->
                    val pt = ptEl as? JsonObject ?: return@mapNotNull null
                    val x = pt["x"]?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null
                    val y = pt["y"]?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null
                    StrokePoint(x = x, y = y)
                }
                if (pts.size >= 2) {
                    inkStrokes.add(
                        InkStroke(
                            points = pts,
                            colorHex = sObj["colorHex"]?.jsonPrimitive?.contentOrNull ?: "#FFFFFF",
                            strokeWidth = sObj["strokeWidth"]?.jsonPrimitive?.floatOrNull ?: 4f,
                            isHighlighter = sObj["isHighlighter"]?.jsonPrimitive?.booleanOrNull ?: false,
                            isEraser = sObj["isEraser"]?.jsonPrimitive?.booleanOrNull ?: false
                        )
                    )
                }
            }
        }

        if (inkStrokes.isNotEmpty()) {
            blocks.add(
                NoteBlock.InkBlock(
                    id = "ink_$noteId",
                    strokesJson = codec.encodeToString(inkStrokes),
                    heightDp = 320f
                )
            )
        }
        return blocks
    }

    private fun decodeBlocksSafe(blocksJson: String): List<NoteBlock> {
        if (blocksJson.isBlank() || blocksJson == "[]") return emptyList()
        return try {
            codec.decodeFromString<List<NoteBlock>>(blocksJson)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
