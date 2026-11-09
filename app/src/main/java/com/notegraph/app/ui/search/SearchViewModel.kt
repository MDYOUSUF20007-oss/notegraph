package com.notegraph.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notegraph.app.data.local.dao.NoteDao
import com.notegraph.app.data.local.entity.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val noteDao: NoteDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val searchResults: StateFlow<List<NoteEntity>> = _query
        .debounce(250L)
        .flatMapLatest { q ->
            val trimmed = q.trim()
            if (trimmed.isEmpty()) {
                flowOf(emptyList())
            } else {
                noteDao.searchNotesWithBlocks(trimmed)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }
}
