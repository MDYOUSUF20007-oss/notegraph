package com.notegraph.app.ui.editor

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.NoteBlock
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit
) {
    val note by viewModel.note.collectAsState()
    val backlinks by viewModel.backlinks.collectAsState()
    val blocks by viewModel.blocks.collectAsState()

    var title by remember(note) { mutableStateOf(note?.title ?: "") }
    var content by remember(note) { mutableStateOf(note?.content ?: "") }

    val json = remember { Json { ignoreUnknownKeys = true } }

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
        },
        bottomBar = {
            // ─── Multimedia toolbar ───
            BottomAppBar(
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { viewModel.addInkBlock() }) {
                        Text("✏️ Drawing")
                    }
                    TextButton(onClick = { viewModel.addAudioBlock() }) {
                        Text("🎙 Voice")
                    }
                    TextButton(onClick = { viewModel.addImageBlock() }) {
                        Text("🖼 Image/OCR")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ─── Classic text editor ───
            OutlinedTextField(
                value = content,
                onValueChange = {
                    content = it
                    viewModel.updateContent(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text("Start typing notes or use [[Wiki-Links]]...") }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ─── Multimedia blocks ───
            blocks.forEachIndexed { index, block ->
                key(block.id) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column {
                            // Delete button row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = { viewModel.removeBlock(block.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove block",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            when (block) {
                                is NoteBlock.TextBlock -> {
                                    var blockText by remember(block.text) { mutableStateOf(block.text) }
                                    OutlinedTextField(
                                        value = blockText,
                                        onValueChange = { newText ->
                                            blockText = newText
                                            viewModel.updateBlock(block.copy(text = newText))
                                            viewModel.stampTextBlockWithAudioTime(block.id)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        placeholder = { Text("Text block…") }
                                    )
                                    block.audioTimestampMs?.let { ts ->
                                        Text(
                                            "🔗 Audio @ ${ts / 1000}s",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                is NoteBlock.InkBlock -> {
                                    val strokes: List<InkStroke> = remember(block.strokesJson) {
                                        try {
                                            json.decodeFromString<List<InkStroke>>(block.strokesJson)
                                        } catch (_: Exception) {
                                            emptyList()
                                        }
                                    }
                                    InkCanvas(
                                        strokes = strokes,
                                        heightDp = block.heightDp,
                                        onStrokesChanged = { newStrokes ->
                                            viewModel.updateInkStrokes(block.id, newStrokes)
                                        }
                                    )
                                }

                                is NoteBlock.AudioBlock -> {
                                    AudioRecorderBlock(
                                        audioFilePath = block.filePath,
                                        durationMs = block.durationMs,
                                        onRecordingStarted = { _, startMs ->
                                            viewModel.onAudioRecordingStarted(startMs)
                                        },
                                        onRecordingStopped = { duration ->
                                            viewModel.onAudioRecordingStopped()
                                            viewModel.updateBlock(block.copy(durationMs = duration))
                                        }
                                    )
                                }

                                is NoteBlock.ImageBlock -> {
                                    ImageOcrBlock(
                                        uriString = block.uriString,
                                        extractedText = block.extractedOcrText,
                                        onImagePicked = { uri: Uri ->
                                            viewModel.updateBlock(
                                                block.copy(uriString = uri.toString())
                                            )
                                        },
                                        onOcrResult = { text ->
                                            viewModel.updateBlock(
                                                block.copy(extractedOcrText = text)
                                            )
                                            // Also append OCR text into the main note content
                                            val current = content
                                            val appended = if (current.isBlank()) text
                                                else "$current\n\n--- OCR ---\n$text"
                                            content = appended
                                            viewModel.updateContent(appended)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Backlinks section ───
            Text("Backlinks (${backlinks.size})", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (backlinks.isEmpty()) {
                Text(
                    "No backlinks found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Column {
                    backlinks.forEach { link ->
                        Text(
                            "• Linked from Note ID: ${link.sourceNoteId}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Bottom spacer so content clears the bottom bar
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
