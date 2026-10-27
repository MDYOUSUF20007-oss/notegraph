package com.notegraph.app.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit
) {
    val note by viewModel.note.collectAsState()
    val backlinks by viewModel.backlinks.collectAsState()

    var title by remember(note) { mutableStateOf(note?.title ?: "") }
    var content by remember(note) { mutableStateOf(note?.content ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = title,
                        onValueChange = {
                            title = it
                            viewModel.updateTitle(it)
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        placeholder = { Text("Note Title") }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = content,
                onValueChange = {
                    content = it
                    viewModel.updateContent(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text("Start typing notes or use [[Wiki-Links]]...") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Backlinks (${backlinks.size})", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (backlinks.isEmpty()) {
                Text("No backlinks found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            } else {
                Column {
                    backlinks.forEach { link ->
                        Text("• Linked from Note ID: \${link.sourceNoteId}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}