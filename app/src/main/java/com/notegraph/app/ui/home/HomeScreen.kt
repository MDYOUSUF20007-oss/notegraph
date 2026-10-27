package com.notegraph.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notegraph.app.data.local.entity.NoteEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNoteClick: (String) -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateGraph: () -> Unit,
    onNavigateTags: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    var showMenuForNote by remember { mutableStateOf<NoteEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NoteGraph") },
                actions = {
                    IconButton(onClick = onNavigateSearch) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = onNavigateGraph) { Icon(Icons.Default.Share, "Graph") }
                    IconButton(onClick = onNavigateTags) { Icon(Icons.Default.List, "Tags") }
                    IconButton(onClick = onNavigateSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val newId = viewModel.createNote()
                onNoteClick(newId)
            }) {
                Icon(Icons.Default.Add, "New Note")
            }
        }
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(160.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = V2DPOrSpacedBy(8.dp)
        ) {
            items(notes) { note ->
                NoteCard(
                    note = note,
                    onClick = { onNoteClick(note.id) },
                    onLongClick = { showMenuForNote = note }
                )
            }
        }

        showMenuForNote?.let { note ->
            AlertDialog(
                onDismissRequest = { showMenuForNote = null },
                title = { Text(note.title) },
                text = { Text("Choose action") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.togglePin(note)
                        showMenuForNote = null
                    }) { Text(if (note.isPinned) "Unpin" else "Pin") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.moveToTrash(note)
                        showMenuForNote = null
                    }) { Text("Delete") }
                }
            )
        }
    }
}

@Composable
fun V2DPOrSpacedBy(dp: androidx.compose.ui.unit.Dp) = dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (note.isPinned) {
                Icon(Icons.Default.Star, "Pinned", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(text = note.title.ifEmpty { "Untitled" }, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content.ifEmpty { "Empty note..." },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = dateFormat.format(Date(note.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}