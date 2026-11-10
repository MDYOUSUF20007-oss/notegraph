package com.notegraph.app.data.sync

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.notegraph.app.data.local.NoteGraphDatabase
import com.notegraph.app.data.local.dao.FolderDao
import com.notegraph.app.data.local.dao.LinkDao
import com.notegraph.app.data.local.dao.NoteDao
import com.notegraph.app.data.local.entity.FolderEntity
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.data.sync.SyncEngine.toSyncFolder
import com.notegraph.app.data.sync.SyncEngine.toSyncNote
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class SyncPreferencesSnapshot(
    val deviceId: String = "",
    val deviceName: String = "Android",
    val savedFolderUri: String? = null,
    val autoSyncOnOpen: Boolean = true,
    val lastSyncAt: Long = 0L,
    val lastSyncMessage: String = "Not synced yet"
)

/**
 * Coordinates Cross-OS Sync (Android <-> Windows <-> macOS) over:
 * 1. Shared Cloud Folder (SAF DocumentsContract — Drive / Syncthing / OneDrive / Dropbox / iCloud)
 * 2. Portable .zip Sync Archive (vault-sync-v2.json + backward-compatible notes.json + media assets)
 * 3. Direct Wi-Fi LAN Sync (consumed by [LanSyncManager])
 */
@Singleton
class VaultSyncManager @Inject constructor(
    private val database: NoteGraphDatabase,
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val linkDao: LinkDao,
    @ApplicationContext private val appContext: Context
) {
    private val syncMutex = Mutex()
    private val prefs = appContext.getSharedPreferences("notegraph_sync_prefs", Context.MODE_PRIVATE)
    private val tombstonesFile = File(appContext.filesDir, "sync_tombstones.json")
    private val sidecarFile = File(appContext.filesDir, "sync_sidecar.json")

    private val _prefsState = MutableStateFlow(loadPrefsSnapshot())
    val prefsState: StateFlow<SyncPreferencesSnapshot> = _prefsState.asStateFlow()

    companion object {
        private const val KEY_DEVICE_ID = "sync_device_id"
        private const val KEY_FOLDER_URI = "sync_saf_folder_uri"
        private const val KEY_AUTO_SYNC = "sync_auto_on_open"
        private const val KEY_LAST_SYNC_AT = "sync_last_sync_at"
        private const val KEY_LAST_SYNC_MSG = "sync_last_sync_msg"
        private const val SYNC_FILENAME_V2 = "notegraph-sync-v2.json"
        private const val ZIP_FILENAME_V2 = "vault-sync-v2.json"
        private const val ZIP_FILENAME_LEGACY = "notes.json"
    }

    private fun loadPrefsSnapshot(): SyncPreferencesSnapshot {
        val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
        val model = Build.MODEL.orEmpty().trim()
        val mfr = Build.MANUFACTURER.orEmpty().trim()
        val deviceName = if (model.startsWith(mfr, ignoreCase = true)) model else "$mfr $model".trim()
        return SyncPreferencesSnapshot(
            deviceId = deviceId,
            deviceName = deviceName.ifBlank { "Android Device" },
            savedFolderUri = prefs.getString(KEY_FOLDER_URI, null),
            autoSyncOnOpen = prefs.getBoolean(KEY_AUTO_SYNC, true),
            lastSyncAt = prefs.getLong(KEY_LAST_SYNC_AT, 0L),
            lastSyncMessage = prefs.getString(KEY_LAST_SYNC_MSG, null) ?: "Not synced yet"
        )
    }

    private fun refreshPrefsState(message: String? = null, syncedAt: Long? = null) {
        if (message != null || syncedAt != null) {
            prefs.edit().apply {
                if (syncedAt != null) putLong(KEY_LAST_SYNC_AT, syncedAt)
                if (message != null) putString(KEY_LAST_SYNC_MSG, message)
            }.apply()
        }
        _prefsState.value = loadPrefsSnapshot()
    }

    fun setSavedFolderUri(uri: Uri?) {
        prefs.edit().apply {
            if (uri == null) remove(KEY_FOLDER_URI) else putString(KEY_FOLDER_URI, uri.toString())
        }.apply()
        refreshPrefsState()
    }

    fun setAutoSyncOnOpen(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
        refreshPrefsState()
    }

    suspend fun recordTombstone(id: String, kind: String) = withContext(Dispatchers.IO) {
        if (id.isBlank()) return@withContext
        syncMutex.withLock {
            val current = readTombstonesUnsafe().toMutableList()
            val normKind = if (kind == "folder") "folder" else "note"
            current.removeAll { it.id == id && it.kind == normKind }
            current.add(SyncTombstone(id = id, kind = normKind, deletedAt = System.currentTimeMillis()))
            writeTombstonesUnsafe(current.takeLast(500))
        }
    }

    suspend fun buildLocalPackage(): SyncPackageV2 = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            buildLocalPackageUnsafe()
        }
    }

    suspend fun applyRemotePackage(remote: SyncPackageV2): Pair<SyncSummary, SyncPackageV2> =
        withContext(Dispatchers.IO) {
            syncMutex.withLock {
                val summary = mergeAndPersistUnsafe(remote, assetRemapper = null)
                val updatedPkg = buildLocalPackageUnsafe()
                val msg = "Synced ${summary.notesMerged + summary.newNotesAdded} notes from ${summary.remoteDevice}"
                refreshPrefsState(message = msg, syncedAt = System.currentTimeMillis())
                summary to updatedPkg
            }
        }

    suspend fun exportZipBackup(destUri: Uri): Int = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val pkg = buildLocalPackageUnsafe()
            val v2Bytes = SyncEngine.codec.encodeToString(pkg).toByteArray(Charsets.UTF_8)
            val legacyNotes = noteDao.getAllNotesOnce()
            val legacyBytes = SyncEngine.codec.encodeToString(legacyNotes).toByteArray(Charsets.UTF_8)

            appContext.contentResolver.openOutputStream(destUri)?.use { out ->
                ZipOutputStream(out).use { zos ->
                    zos.putNextEntry(ZipEntry(ZIP_FILENAME_V2))
                    zos.write(v2Bytes)
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry(ZIP_FILENAME_LEGACY))
                    zos.write(legacyBytes)
                    zos.closeEntry()

                    writeReferencedAssetsToZip(pkg.notes, zos)
                }
            } ?: throw IllegalStateException("Cannot write selected .zip destination")

            refreshPrefsState(
                message = "Exported Sync v2 ZIP (${pkg.notes.size} notes, ${pkg.folders.size} folders)",
                syncedAt = System.currentTimeMillis()
            )
            pkg.notes.size
        }
    }

    suspend fun importAndMergeZip(sourceUri: Uri): SyncSummary = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            var v2Raw: String? = null
            var legacyRaw: String? = null
            val extractedPdfs = HashMap<String, String>()
            val extractedImages = HashMap<String, String>()

            val pdfDir = File(appContext.filesDir, "pdfs").apply { mkdirs() }
            val imgDir = File(appContext.filesDir, "sync_images").apply { mkdirs() }

            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            when {
                                entry.name == ZIP_FILENAME_V2 || entry.name == SYNC_FILENAME_V2 -> {
                                    v2Raw = zis.bufferedReader(Charsets.UTF_8).readText()
                                }
                                entry.name == ZIP_FILENAME_LEGACY -> {
                                    legacyRaw = zis.bufferedReader(Charsets.UTF_8).readText()
                                }
                                entry.name.startsWith("assets/pdfs/") -> {
                                    val base = sanitizeFileName(File(entry.name).name)
                                    if (base.isNotEmpty()) {
                                        val outFile = File(pdfDir, base)
                                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                                        extractedPdfs[base] = outFile.absolutePath
                                    }
                                }
                                entry.name.startsWith("assets/images/") -> {
                                    val base = sanitizeFileName(File(entry.name).name)
                                    if (base.isNotEmpty()) {
                                        val outFile = File(imgDir, base)
                                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                                        extractedImages[base] = Uri.fromFile(outFile).toString()
                                    }
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            } ?: throw IllegalStateException("Cannot read selected .zip archive")

            val rawPayload = v2Raw?.takeIf { it.isNotBlank() }
                ?: legacyRaw?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("No valid sync payload inside .zip")

            val remote = SyncEngine.parsePackageOrLegacy(rawPayload)
            val summary = mergeAndPersistUnsafe(remote) { note ->
                remapImportedAssets(note, extractedPdfs, extractedImages)
            }
            refreshPrefsState(
                message = "Merged ZIP (${summary.notesMerged} updated, ${summary.newNotesAdded} added)",
                syncedAt = System.currentTimeMillis()
            )
            summary
        }
    }

    suspend fun autoSyncSavedFolderIfEnabled(): SyncSummary? = withContext(Dispatchers.IO) {
        val snap = loadPrefsSnapshot()
        if (!snap.autoSyncOnOpen) return@withContext null
        val uriStr = snap.savedFolderUri ?: return@withContext null
        val uri = Uri.parse(uriStr)
        return@withContext try {
            syncWithSafFolder(uri)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun syncWithSafFolder(treeUri: Uri): SyncSummary = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val syncDoc = findChildDocument(treeUri, rootDocId, SYNC_FILENAME_V2)
                ?: findChildDocument(treeUri, rootDocId, ZIP_FILENAME_V2)
                ?: findChildDocument(treeUri, rootDocId, ZIP_FILENAME_LEGACY)

            val pdfDir = File(appContext.filesDir, "pdfs").apply { mkdirs() }
            val imgDir = File(appContext.filesDir, "sync_images").apply { mkdirs() }
            val importedPdfs = HashMap<String, String>()
            val importedImages = HashMap<String, String>()

            // 1. Pull remote assets if assets/ exists in shared folder
            val assetsDoc = findChildDocument(treeUri, rootDocId, "assets")
            if (assetsDoc != null && assetsDoc.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                findChildDocument(treeUri, assetsDoc.docId, "pdfs")?.let { pdfSub ->
                    if (pdfSub.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        for (child in listChildDocuments(treeUri, pdfSub.docId)) {
                            if (child.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
                                val base = sanitizeFileName(child.name)
                                val localTarget = File(pdfDir, base)
                                if (!localTarget.exists() || localTarget.length() == 0L) {
                                    appContext.contentResolver.openInputStream(child.uri)?.use { inp ->
                                        FileOutputStream(localTarget).use { out -> inp.copyTo(out) }
                                    }
                                }
                                if (localTarget.exists()) importedPdfs[base] = localTarget.absolutePath
                            }
                        }
                    }
                }
                findChildDocument(treeUri, assetsDoc.docId, "images")?.let { imgSub ->
                    if (imgSub.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        for (child in listChildDocuments(treeUri, imgSub.docId)) {
                            if (child.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
                                val base = sanitizeFileName(child.name)
                                val localTarget = File(imgDir, base)
                                if (!localTarget.exists() || localTarget.length() == 0L) {
                                    appContext.contentResolver.openInputStream(child.uri)?.use { inp ->
                                        FileOutputStream(localTarget).use { out -> inp.copyTo(out) }
                                    }
                                }
                                if (localTarget.exists()) importedImages[base] = Uri.fromFile(localTarget).toString()
                            }
                        }
                    }
                }
            }

            // 2. Read & merge remote sync file if present
            val summary = if (syncDoc != null) {
                val raw = appContext.contentResolver.openInputStream(syncDoc.uri)?.use {
                    it.bufferedReader(Charsets.UTF_8).readText()
                }.orEmpty()
                if (raw.isNotBlank()) {
                    val remote = SyncEngine.parsePackageOrLegacy(raw)
                    mergeAndPersistUnsafe(remote) { note ->
                        remapImportedAssets(note, importedPdfs, importedImages)
                    }
                } else {
                    SyncSummary(remoteDevice = "Shared Folder")
                }
            } else {
                SyncSummary(remoteDevice = "Shared Folder (Initialized)")
            }

            // 3. Write merged state + local assets back to Shared Folder
            val mergedPkg = buildLocalPackageUnsafe()
            val payloadBytes = SyncEngine.codec.encodeToString(mergedPkg).toByteArray(Charsets.UTF_8)
            writeSafDocumentBytes(
                treeUri = treeUri,
                parentDocId = rootDocId,
                displayName = SYNC_FILENAME_V2,
                mimeType = "application/json",
                bytes = payloadBytes
            )
            pushLocalAssetsToSaf(treeUri, rootDocId, mergedPkg.notes)

            val msg = "Folder synced: ${mergedPkg.notes.size} notes, ${mergedPkg.links.size} links"
            refreshPrefsState(message = msg, syncedAt = System.currentTimeMillis())
            summary
        }
    }

    // ─── Unsafe Helpers (Always Called Under syncMutex) ────────────────────

    private suspend fun buildLocalPackageUnsafe(): SyncPackageV2 {
        val snap = loadPrefsSnapshot()
        val folders = folderDao.getAllFoldersOnce()
        val notes = noteDao.getAllNotesIncludingTrashOnce()
        val sidecar = readSidecarUnsafe()
        val tombstones = readTombstonesUnsafe()
        val activeNotes = notes.filter { !it.isTrash }
        val links = SyncEngine.extractLinks(activeNotes)

        return SyncPackageV2(
            schemaVersion = 2,
            exportedAt = System.currentTimeMillis(),
            deviceId = snap.deviceId,
            deviceName = snap.deviceName,
            platform = "android",
            folders = folders.map { it.toSyncFolder() },
            notes = notes.map { it.toSyncNote(sidecar[it.id]) },
            links = links.map { l ->
                SyncLink(
                    sourceId = l.sourceNoteId,
                    targetId = l.targetNoteId,
                    targetTitle = l.targetNoteTitle,
                    isResolved = l.isResolved
                )
            },
            tombstones = tombstones
        )
    }

    private suspend fun mergeAndPersistUnsafe(
        remote: SyncPackageV2,
        assetRemapper: ((NoteEntity) -> NoteEntity)?
    ): SyncSummary {
        val localFolders = folderDao.getAllFoldersOnce()
        val localNotes = noteDao.getAllNotesIncludingTrashOnce()
        val localTombstones = readTombstonesUnsafe()
        val localSidecar = readSidecarUnsafe()

        val result = SyncEngine.mergePackages(
            localFolders = localFolders,
            localNotes = localNotes,
            localTombstones = localTombstones,
            localSidecar = localSidecar,
            remote = remote
        )

        val finalNotes = if (assetRemapper != null) {
            result.notes.map(assetRemapper)
        } else {
            result.notes
        }

        val rebuiltLinks = if (assetRemapper != null) {
            SyncEngine.extractLinks(finalNotes.filter { !it.isTrash })
        } else {
            result.links
        }

        database.withTransaction {
            if (result.folders.isNotEmpty()) {
                folderDao.insertFolders(result.folders)
            }
            for (delFolderId in result.deletedFolderIds) {
                noteDao.unfileNotesInFolder(delFolderId)
                folderDao.deleteFolder(delFolderId)
            }
            if (finalNotes.isNotEmpty()) {
                noteDao.insertNotes(finalNotes)
            }
            for (delNoteId in result.deletedNoteIds) {
                noteDao.deleteNoteCompletely(delNoteId)
            }
            linkDao.deleteAllLinks()
            if (rebuiltLinks.isNotEmpty()) {
                linkDao.insertLinks(rebuiltLinks)
            }
        }

        writeTombstonesUnsafe(result.tombstones)
        writeSidecarUnsafe(result.sidecar)
        return result.summary.copy(linksRebuilt = rebuiltLinks.size)
    }

    private fun readTombstonesUnsafe(): List<SyncTombstone> {
        if (!tombstonesFile.exists()) return emptyList()
        return try {
            SyncEngine.codec.decodeFromString<List<SyncTombstone>>(tombstonesFile.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeTombstonesUnsafe(list: List<SyncTombstone>) {
        writeAtomicText(tombstonesFile, SyncEngine.codec.encodeToString(list))
    }

    private fun readSidecarUnsafe(): Map<String, DesktopNoteSidecar> {
        if (!sidecarFile.exists()) return emptyMap()
        return try {
            SyncEngine.codec.decodeFromString<Map<String, DesktopNoteSidecar>>(sidecarFile.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeSidecarUnsafe(map: Map<String, DesktopNoteSidecar>) {
        writeAtomicText(sidecarFile, SyncEngine.codec.encodeToString(map))
    }

    private fun writeAtomicText(target: File, text: String) {
        try {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
        } catch (_: Exception) {
            // Non-fatal metadata write
        }
    }

    // ─── Asset Mapping & Zip Helpers ───────────────────────────────────────

    private fun remapImportedAssets(
        note: NoteEntity,
        pdfsByBase: Map<String, String>,
        imagesByBase: Map<String, String>
    ): NoteEntity {
        if (note.blocksJson.isBlank() || note.blocksJson == "[]") return note
        val blocks = try {
            SyncEngine.codec.decodeFromString<List<NoteBlock>>(note.blocksJson)
        } catch (_: Exception) {
            return note
        }
        var changed = false
        val localPdfDir = File(appContext.filesDir, "pdfs")
        val updatedBlocks = blocks.map { blk ->
            when (blk) {
                is NoteBlock.PdfBlock -> {
                    val existingLocal = blk.filePath.isNotBlank() && File(blk.filePath).exists()
                    if (!existingLocal) {
                        val candidateNames = listOfNotNull(
                            File(blk.filePath).name.takeIf { it.isNotBlank() }?.let(::sanitizeFileName),
                            blk.fileName.takeIf { it.isNotBlank() }?.let(::sanitizeFileName)
                        )
                        val resolvedPath = candidateNames.firstNotNullOfOrNull { name ->
                            pdfsByBase[name] ?: File(localPdfDir, name).takeIf { it.exists() }?.absolutePath
                        }
                        if (resolvedPath != null) {
                            changed = true
                            blk.copy(filePath = resolvedPath)
                        } else {
                            blk
                        }
                    } else {
                        blk
                    }
                }
                is NoteBlock.ImageBlock -> {
                    if (blk.uriString.isNotBlank() && !isReadableUriOrFile(blk.uriString)) {
                        val base = sanitizeFileName(File(Uri.parse(blk.uriString).path ?: blk.uriString).name)
                        val mapped = imagesByBase[base]
                        if (mapped != null) {
                            changed = true
                            blk.copy(uriString = mapped)
                        } else {
                            blk
                        }
                    } else {
                        blk
                    }
                }
                else -> blk
            }
        }
        return if (changed) {
            note.copy(blocksJson = SyncEngine.codec.encodeToString(updatedBlocks))
        } else {
            note
        }
    }

    private fun writeReferencedAssetsToZip(notes: List<SyncNote>, zos: ZipOutputStream) {
        val seenEntries = HashSet<String>()
        for (note in notes) {
            if (note.isTrash) continue
            val blocks = try {
                SyncEngine.codec.decodeFromString<List<NoteBlock>>(note.blocksJson)
            } catch (_: Exception) {
                emptyList()
            }
            for (blk in blocks) {
                when (blk) {
                    is NoteBlock.PdfBlock -> {
                        val file = blk.filePath.takeIf { it.isNotBlank() }?.let(::File)
                        if (file != null && file.exists() && file.isFile) {
                            val name = sanitizeFileName(blk.fileName.ifBlank { file.name })
                            val entryName = "assets/pdfs/$name"
                            if (seenEntries.add(entryName)) {
                                zos.putNextEntry(ZipEntry(entryName))
                                FileInputStream(file).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                    is NoteBlock.ImageBlock -> {
                        if (blk.uriString.isNotBlank()) {
                            val parsed = Uri.parse(blk.uriString)
                            val base = sanitizeFileName(
                                File(parsed.path ?: "${blk.id}.jpg").name.ifBlank { "${blk.id}.jpg" }
                            )
                            val entryName = "assets/images/$base"
                            if (seenEntries.add(entryName)) {
                                try {
                                    openUriInputStream(blk.uriString)?.use { inp ->
                                        zos.putNextEntry(ZipEntry(entryName))
                                        inp.copyTo(zos)
                                        zos.closeEntry()
                                    }
                                } catch (_: Exception) {
                                    // Unreadable external content URI — skip gracefully
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun pushLocalAssetsToSaf(treeUri: Uri, rootDocId: String, notes: List<SyncNote>) {
        val pdfFilesToPush = LinkedHashMap<String, File>()
        val imageUrisToPush = LinkedHashMap<String, String>()
        for (note in notes) {
            if (note.isTrash) continue
            val blocks = try {
                SyncEngine.codec.decodeFromString<List<NoteBlock>>(note.blocksJson)
            } catch (_: Exception) {
                emptyList()
            }
            for (blk in blocks) {
                when (blk) {
                    is NoteBlock.PdfBlock -> if (blk.filePath.isNotBlank()) {
                        val f = File(blk.filePath)
                        if (f.exists() && f.isFile) {
                            val base = sanitizeFileName(blk.fileName.ifBlank { f.name })
                            pdfFilesToPush.putIfAbsent(base, f)
                        }
                    }
                    is NoteBlock.ImageBlock -> if (blk.uriString.isNotBlank()) {
                        val parsed = Uri.parse(blk.uriString)
                        val base = sanitizeFileName(
                            File(parsed.path ?: "${blk.id}.jpg").name.ifBlank { "${blk.id}.jpg" }
                        )
                        imageUrisToPush.putIfAbsent(base, blk.uriString)
                    }
                    else -> Unit
                }
            }
        }
        if (pdfFilesToPush.isEmpty() && imageUrisToPush.isEmpty()) return

        val assetsDoc = findOrCreateSafDirectory(treeUri, rootDocId, "assets") ?: return

        if (pdfFilesToPush.isNotEmpty()) {
            val pdfsDirDoc = findOrCreateSafDirectory(treeUri, assetsDoc.docId, "pdfs")
            if (pdfsDirDoc != null) {
                val existingPdfs = listChildDocuments(treeUri, pdfsDirDoc.docId).associateBy { it.name }
                for ((name, localFile) in pdfFilesToPush) {
                    val remoteChild = existingPdfs[name]
                    if (remoteChild != null && remoteChild.size == localFile.length()) {
                        continue
                    }
                    try {
                        writeSafDocumentBytes(
                            treeUri = treeUri,
                            parentDocId = pdfsDirDoc.docId,
                            displayName = name,
                            mimeType = "application/pdf",
                            bytes = localFile.readBytes()
                        )
                    } catch (_: Exception) {
                        // Skip non-fatal asset copy error
                    }
                }
            }
        }

        if (imageUrisToPush.isNotEmpty()) {
            val imagesDirDoc = findOrCreateSafDirectory(treeUri, assetsDoc.docId, "images")
            if (imagesDirDoc != null) {
                val existingImages = listChildDocuments(treeUri, imagesDirDoc.docId).associateBy { it.name }
                for ((name, uriStr) in imageUrisToPush) {
                    val remoteChild = existingImages[name]
                    if (remoteChild != null && remoteChild.size > 0L) {
                        continue
                    }
                    try {
                        val bytes = openUriInputStream(uriStr)?.use { it.readBytes() } ?: continue
                        if (bytes.isEmpty()) continue
                        val lower = name.lowercase()
                        val mime = when {
                            lower.endsWith(".png") -> "image/png"
                            lower.endsWith(".webp") -> "image/webp"
                            else -> "image/jpeg"
                        }
                        writeSafDocumentBytes(
                            treeUri = treeUri,
                            parentDocId = imagesDirDoc.docId,
                            displayName = name,
                            mimeType = mime,
                            bytes = bytes
                        )
                    } catch (_: Exception) {
                        // Skip non-fatal image copy error
                    }
                }
            }
        }
    }

    private fun openUriInputStream(uriStr: String): java.io.InputStream? {
        val parsed = Uri.parse(uriStr)
        if (parsed.scheme == null) {
            val f = File(uriStr)
            return if (f.exists() && f.isFile) FileInputStream(f) else null
        }
        return appContext.contentResolver.openInputStream(parsed)
    }

    private fun isReadableUriOrFile(uriStr: String): Boolean {
        val parsed = Uri.parse(uriStr)
        if (parsed.scheme == "file") {
            return parsed.path?.let { File(it).exists() } == true
        }
        return try {
            appContext.contentResolver.openInputStream(parsed)?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "file.bin" }
    }

    // ─── Pure Android DocumentsContract Helpers ─────────────────────────────

    private data class SafDoc(
        val docId: String,
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val size: Long
    )

    private fun listChildDocuments(treeUri: Uri, parentDocId: String): List<SafDoc> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val result = mutableListOf<SafDoc>()
        appContext.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx).orEmpty()
                val mime = cursor.getString(mimeIdx).orEmpty()
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else -1L
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                result.add(SafDoc(id, docUri, name, mime, size))
            }
        }
        return result
    }

    private fun findChildDocument(treeUri: Uri, parentDocId: String, displayName: String): SafDoc? {
        return listChildDocuments(treeUri, parentDocId).firstOrNull { it.name == displayName }
    }

    private fun findOrCreateSafDirectory(treeUri: Uri, parentDocId: String, name: String): SafDoc? {
        findChildDocument(treeUri, parentDocId, name)?.let { existing ->
            if (existing.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) return existing
        }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val createdUri = DocumentsContract.createDocument(
            appContext.contentResolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name
        ) ?: return null
        val createdId = DocumentsContract.getDocumentId(createdUri)
        return SafDoc(createdId, createdUri, name, DocumentsContract.Document.MIME_TYPE_DIR, 0L)
    }

    private fun writeSafDocumentBytes(
        treeUri: Uri,
        parentDocId: String,
        displayName: String,
        mimeType: String,
        bytes: ByteArray
    ) {
        val existing = findChildDocument(treeUri, parentDocId, displayName)
        if (existing != null) {
            val wroteTruncated = try {
                appContext.contentResolver.openOutputStream(existing.uri, "wt")?.use { out ->
                    out.write(bytes)
                    out.flush()
                    true
                } ?: false
            } catch (_: Exception) {
                false
            }
            if (wroteTruncated) return
            // Provider rejected "wt" truncate mode -> replace document cleanly
            try {
                DocumentsContract.deleteDocument(appContext.contentResolver, existing.uri)
            } catch (_: Exception) {
            }
        }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val newUri = DocumentsContract.createDocument(
            appContext.contentResolver,
            parentUri,
            mimeType,
            displayName
        ) ?: throw IllegalStateException("Cannot create $displayName in selected folder")

        appContext.contentResolver.openOutputStream(newUri)?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw IllegalStateException("Cannot write $displayName in selected folder")
    }
}
