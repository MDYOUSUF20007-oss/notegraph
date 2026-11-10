package com.notegraph.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notegraph.app.data.local.NoteJson
import com.notegraph.app.data.local.dao.FolderDao
import com.notegraph.app.data.local.dao.NoteDao
import com.notegraph.app.data.local.entity.ChecklistItem
import com.notegraph.app.data.local.entity.FolderEntity
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.data.sync.VaultSyncManager
import com.notegraph.app.ui.editor.PdfHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val vaultSyncManager: VaultSyncManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        const val UNFILED_FOLDER_ID = "__UNFILED__"
    }

    val notes: StateFlow<List<NoteEntity>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<NoteEntity>())

    val folders: StateFlow<List<FolderEntity>> = folderDao.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<FolderEntity>())

    val trashNotes: StateFlow<List<NoteEntity>> = noteDao.getTrashNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<NoteEntity>())

    private val _selectedFolderId = MutableStateFlow<String?>(null)
    val selectedFolderId: StateFlow<String?> = _selectedFolderId.asStateFlow()

    val filteredNotes: StateFlow<List<NoteEntity>> = combine(notes, selectedFolderId) { allNotes, filterId ->
        when (filterId) {
            null -> allNotes
            UNFILED_FOLDER_ID -> allNotes.filter { it.folderId == null }
            else -> allNotes.filter { it.folderId == filterId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<NoteEntity>())

    init {
        seedStarterFoldersIfNeeded()
        viewModelScope.launch {
            vaultSyncManager.autoSyncSavedFolderIfEnabled()
        }
    }

    private fun seedStarterFoldersIfNeeded() {
        viewModelScope.launch {
            val prefs = appContext.getSharedPreferences("notegraph_prefs", Context.MODE_PRIVATE)
            val alreadySeeded = prefs.getBoolean("starter_folders_seeded", false)
            if (!alreadySeeded) {
                val existing = folderDao.getAllFoldersOnce()
                if (existing.isEmpty()) {
                    val starters = listOf(
                        FolderEntity(id = "folder_study", name = "Study", icon = "📚", colorHex = "#6366F1", createdAt = 1L, updatedAt = 1L),
                        FolderEntity(id = "folder_work", name = "Work", icon = "💼", colorHex = "#38BDF8", createdAt = 2L, updatedAt = 2L),
                        FolderEntity(id = "folder_gaming", name = "Gaming", icon = "🎮", colorHex = "#A855F7", createdAt = 3L, updatedAt = 3L),
                        FolderEntity(id = "folder_shopping", name = "Shopping", icon = "🛒", colorHex = "#10B981", createdAt = 4L, updatedAt = 4L),
                        FolderEntity(id = "folder_reminder", name = "Reminder", icon = "⏰", colorHex = "#F59E0B", createdAt = 5L, updatedAt = 5L)
                    )
                    folderDao.insertFolders(starters)
                }
                prefs.edit().putBoolean("starter_folders_seeded", true).apply()
            }
        }
    }

    fun selectFolder(folderId: String?) {
        _selectedFolderId.value = folderId
    }

    fun createFolder(name: String, icon: String, colorHex: String) {
        val cleanName = name.trim().ifBlank { "New Folder" }
        val now = System.currentTimeMillis()
        val newFolder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            icon = icon.ifBlank { "📁" },
            colorHex = colorHex.ifBlank { "#A855F7" },
            createdAt = now,
            updatedAt = now
        )
        viewModelScope.launch {
            folderDao.insertFolder(newFolder)
        }
    }

    fun updateFolder(folder: FolderEntity) {
        val cleanName = folder.name.trim().ifBlank { "Folder" }
        viewModelScope.launch {
            folderDao.updateFolder(folder.copy(name = cleanName, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteFolder(folderId: String) {
        if (_selectedFolderId.value == folderId) {
            _selectedFolderId.value = null
        }
        viewModelScope.launch {
            vaultSyncManager.recordTombstone(folderId, "folder")
            noteDao.unfileNotesInFolder(folderId)
            folderDao.deleteFolder(folderId)
        }
    }

    fun moveNoteToFolder(noteId: String, folderId: String?) {
        val actualFolderId = if (folderId == UNFILED_FOLDER_ID) null else folderId
        viewModelScope.launch {
            noteDao.moveNoteToFolder(noteId, actualFolderId, System.currentTimeMillis())
        }
    }

    fun createNote(
        title: String = "Untitled Note",
        folderId: String? = null,
        onCreated: ((String) -> Unit)? = null
    ): String {
        val id = UUID.randomUUID().toString()
        val resolvedFolderId = folderId ?: _selectedFolderId.value?.takeIf { it != UNFILED_FOLDER_ID }
        val now = System.currentTimeMillis()
        val newNote = NoteEntity(
            id = id,
            title = title,
            content = "",
            folderId = resolvedFolderId,
            createdAt = now,
            updatedAt = now
        )
        viewModelScope.launch {
            noteDao.insertNote(newNote)
            onCreated?.invoke(id)
        }
        return id
    }

    fun openOrCreateDailyNote(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val todayTitle = NoteJson.formatIsoDate(System.currentTimeMillis())
            val existing = noteDao.getNoteByTitle(todayTitle)
            if (existing != null) {
                onReady(existing.id)
            } else {
                val id = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val starterBlocks: List<NoteBlock> = listOf(
                    NoteBlock.ChecklistBlock(
                        id = UUID.randomUUID().toString(),
                        title = "Today's Tasks",
                        items = listOf(
                            ChecklistItem(
                                id = UUID.randomUUID().toString(),
                                text = "",
                                isChecked = false
                            )
                        )
                    )
                )
                val dailyNote = NoteEntity(
                    id = id,
                    title = todayTitle,
                    content = "#daily\n",
                    folderId = _selectedFolderId.value?.takeIf { it != UNFILED_FOLDER_ID },
                    createdAt = now,
                    updatedAt = now,
                    templateType = "DAILY",
                    blocksJson = NoteJson.encodeBlocks(starterBlocks)
                )
                noteDao.insertNote(dailyNote)
                onReady(id)
            }
        }
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.updateNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
        }
    }

    fun moveToTrash(note: NoteEntity) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            noteDao.setTrashState(note.id, true, now, now)
        }
    }

    fun restoreFromTrash(noteId: String) {
        viewModelScope.launch {
            noteDao.setTrashState(noteId, false, null, System.currentTimeMillis())
        }
    }

    fun deletePermanently(noteId: String) {
        viewModelScope.launch {
            vaultSyncManager.recordTombstone(noteId, "note")
            noteDao.deleteNoteCompletely(noteId)
        }
    }

    fun toggleLock(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.updateNote(note.copy(isLocked = !note.isLocked, updatedAt = System.currentTimeMillis()))
        }
    }

    fun importPdfAsNewNote(uri: Uri, onReady: (String?) -> Unit) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                PdfHelper.importPdfFromUri(appContext, uri)
            }
            if (info == null) {
                onReady(null)
                return@launch
            }
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val cleanTitle = info.fileName.removeSuffix(".pdf").ifBlank { "PDF Document" }
            val pdfBlock: List<NoteBlock> = listOf(
                NoteBlock.PdfBlock(
                    id = UUID.randomUUID().toString(),
                    filePath = info.filePath,
                    fileName = info.fileName,
                    pageCount = info.pageCount,
                    currentPage = 0,
                    annotationsJson = "[]"
                )
            )
            val note = NoteEntity(
                id = id,
                title = cleanTitle,
                content = "#pdf #study\n",
                folderId = _selectedFolderId.value?.takeIf { it != UNFILED_FOLDER_ID },
                createdAt = now,
                updatedAt = now,
                templateType = "PDF",
                blocksJson = NoteJson.encodeBlocks(pdfBlock)
            )
            noteDao.insertNote(note)
            onReady(id)
        }
    }

    fun createNoteFromTemplate(templateType: String, onCreated: (String) -> Unit) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val resolvedFolderId = _selectedFolderId.value?.takeIf { it != UNFILED_FOLDER_ID }
        val (title, body, blocks) = when (templateType) {
            "CORNELL" -> Triple(
                "Cornell Lecture Notes",
                "#study #cornell\n\n**Topic / Course:** \n**Key Questions & Cues:** \n- ",
                listOf<NoteBlock>(
                    NoteBlock.InkBlock(
                        id = UUID.randomUUID().toString(),
                        paperTemplate = "CORNELL",
                        heightDp = 320f
                    ),
                    NoteBlock.TextBlock(id = UUID.randomUUID().toString(), text = "Summary:\n")
                )
            )
            "MEETING" -> Triple(
                "Meeting / People Check-In",
                "#work #meeting\n\n**With:** [[\n**Follow-up Notes:**\n",
                listOf<NoteBlock>(
                    NoteBlock.ChecklistBlock(
                        id = UUID.randomUUID().toString(),
                        title = "Action Items & Next Steps",
                        items = listOf(
                            ChecklistItem(id = UUID.randomUUID().toString(), text = "Send follow-up summary", isChecked = false)
                        )
                    )
                )
            )
            "CHECKLIST" -> Triple(
                "Task List",
                "#todo\n",
                listOf<NoteBlock>(
                    NoteBlock.ChecklistBlock(
                        id = UUID.randomUUID().toString(),
                        title = "Tasks",
                        items = listOf(
                            ChecklistItem(id = UUID.randomUUID().toString(), text = "", isChecked = false)
                        )
                    )
                )
            )
            else -> Triple("Untitled Note", "", emptyList())
        }

        val note = NoteEntity(
            id = id,
            title = title,
            content = body,
            folderId = resolvedFolderId,
            createdAt = now,
            updatedAt = now,
            templateType = templateType,
            blocksJson = NoteJson.encodeBlocks(blocks)
        )
        viewModelScope.launch {
            noteDao.insertNote(note)
            onCreated(id)
        }
    }
}
