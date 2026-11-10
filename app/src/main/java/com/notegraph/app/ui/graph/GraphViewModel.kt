package com.notegraph.app.ui.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notegraph.app.data.local.dao.FolderDao
import com.notegraph.app.data.local.dao.LinkDao
import com.notegraph.app.data.local.dao.NoteDao
import com.notegraph.app.data.local.entity.FolderEntity
import com.notegraph.app.data.local.entity.LinkEntity
import com.notegraph.app.data.local.entity.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class GraphViewModel @Inject constructor(
    noteDao: NoteDao,
    linkDao: LinkDao,
    folderDao: FolderDao
) : ViewModel() {

    val notes: StateFlow<List<NoteEntity>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val links: StateFlow<List<LinkEntity>> = linkDao.getAllLinks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> = folderDao.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
