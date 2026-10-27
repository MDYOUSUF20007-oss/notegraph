package com.notegraph.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notegraph.app.data.local.dao.NoteDao
import com.notegraph.app.data.local.entity.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val noteDao: NoteDao
) : ViewModel() {

    val notes: StateFlow<List<NoteEntity>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createNote(title: String = "Untitled Note", folderId: String? = null): String {
        val id = UUID.randomUUID().toString()
        val newNote = NoteEntity(
            id = id,
            title = title,
            content = "",
            folderId = folderId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            noteDao.insertNote(newNote)
        }
        return id
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