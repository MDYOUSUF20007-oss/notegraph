package com.notegraph.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notegraph.app.data.local.dao.NoteDao
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val noteDao: NoteDao
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val notes: StateFlow<List<NoteEntity>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleBookmark(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.updateNote(note.copy(isFavorite = !note.isFavorite))
        }
    }

    fun toggleChecklistItem(note: NoteEntity, blockId: String, itemId: String) {
        viewModelScope.launch {
            val blocks = try {
                json.decodeFromString<List<NoteBlock>>(note.blocksJson)
            } catch (_: Exception) {
                emptyList()
            }
            val updatedBlocks = blocks.map { block ->
                if (block is NoteBlock.ChecklistBlock && block.id == blockId) {
                    block.copy(
                        items = block.items.map { item ->
                            if (item.id == itemId) item.copy(isChecked = !item.isChecked) else item
                        }
                    )
                } else {
                    block
                }
            }
            val updatedJson = json.encodeToString(updatedBlocks)
            noteDao.updateNote(
                note.copy(
                    blocksJson = updatedJson,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
