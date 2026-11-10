package com.notegraph.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notegraph.app.data.local.dao.FolderDao
import com.notegraph.app.data.local.dao.NoteDao
import com.notegraph.app.data.local.entity.ChecklistItem
import com.notegraph.app.data.local.entity.FolderEntity
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        const val UNFILED_FOLDER_ID = "__UNFILED__"
    }

    private val json = Json { ignoreUnknownKeys = true }

    val notes: StateFlow<List<NoteEntity>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<NoteEntity>())

    val folders: StateFlow<List<FolderEntity>> = folderDao.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<FolderEntity>())

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
    }

    private fun seedStarterFoldersIfNeeded() {
        viewModelScope.launch {
            val prefs = appContext.getSharedPreferences("notegraph_prefs", Context.MODE_PRIVATE)
            val alreadySeeded = prefs.getBoolean("starter_folders_seeded", false)
            if (!alreadySeeded) {
                val existing = folderDao.getAllFoldersOnce()
                if (existing.isEmpty()) {
                    val baseTime = System.currentTimeMillis()
                    val starters = listOf(
                        FolderEntity(id = "folder_study", name = "Study", icon = "📚", colorHex = "#6366F1", createdAt = baseTime),
                        FolderEntity(id = "folder_work", name = "Work", icon = "💼", colorHex = "#38BDF8", createdAt = baseTime + 1L),
                        FolderEntity(id = "folder_gaming", name = "Gaming", icon = "🎮", colorHex = "#A855F7", createdAt = baseTime + 2L),
                        FolderEntity(id = "folder_shopping", name = "Shopping", icon = "🛒", colorHex = "#10B981", createdAt = baseTime + 3L),
                        FolderEntity(id = "folder_reminder", name = "Reminder", icon = "⏰", colorHex = "#F59E0B", createdAt = baseTime + 4L)
                    )
                    starters.forEach { folderDao.insertFolder(it) }
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
        val newFolder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            icon = icon.ifBlank { "📁" },
            colorHex = colorHex.ifBlank { "#A855F7" },
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            folderDao.insertFolder(newFolder)
        }
    }

    fun updateFolder(folder: FolderEntity) {
        val cleanName = folder.name.trim().ifBlank { "Folder" }
        viewModelScope.launch {
            folderDao.updateFolder(folder.copy(name = cleanName))
        }
    }

    fun deleteFolder(folderId: String) {
        if (_selectedFolderId.value == folderId) {
            _selectedFolderId.value = null
        }
        viewModelScope.launch {
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
            val todayTitle = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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
                    blocksJson = json.encodeToString<List<NoteBlock>>(starterBlocks)
                )
                noteDao.insertNote(dailyNote)
                onReady(id)
            }
        }
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.updateNote(note.copy(isPinned = !note.isPinned))
        }
    }

    fun moveToTrash(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.setTrashState(note.id, true, System.currentTimeMillis())
        }
    }
}
