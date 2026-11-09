package com.notegraph.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAmoled by remember { mutableStateOf(ThemeManager.isAmoledMode) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }

    val json = remember { Json { ignoreUnknownKeys = true; prettyPrint = true } }

    // SAF Create Document for .zip backup
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        isBackingUp = true
        scope.launch {
            try {
                val notes = withContext(Dispatchers.IO) { viewModel.getAllNotesOnce() }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        ZipOutputStream(outputStream).use { zos ->
                            val jsonString = json.encodeToString(notes)
                            zos.putNextEntry(ZipEntry("notes.json"))
                            zos.write(jsonString.toByteArray(Charsets.UTF_8))
                            zos.closeEntry()
                        }
                    }
                }
                Toast.makeText(context, "Backup complete (${notes.size} notes exported)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isBackingUp = false
            }
        }
    }

    // SAF Open Document for restoring .zip
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        isRestoring = true
        scope.launch {
            try {
                val notesJson = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zis ->
                            var entry = zis.nextEntry
                            var content: String? = null
                            while (entry != null) {
                                if (entry.name == "notes.json") {
                                    content = zis.bufferedReader(Charsets.UTF_8).readText()
                                    break
                                }
                                entry = zis.nextEntry
                            }
                            content
                        }
                    }
                }

                if (!notesJson.isNullOrBlank()) {
                    val notes = json.decodeFromString<List<NoteEntity>>(notesJson)
                    withContext(Dispatchers.IO) {
                        viewModel.restoreNotes(notes)
                    }
                    Toast.makeText(context, "Restored ${notes.size} notes successfully", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Invalid backup: notes.json missing inside zip", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isRestoring = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Vault") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Appearance Section
            Text(
                text = "Display & Appearance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AMOLED Pure Black", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "#000000 background for battery savings and high contrast",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = isAmoled,
                        onCheckedChange = {
                            isAmoled = it
                            ThemeManager.isAmoledMode = it
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Vault Backup & Restore Section
            Text(
                text = "Backup & Restore Vault",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Export all notes, block structures, drawings, and OCR content into an open, portable .zip archive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = { backupLauncher.launch("NoteGraph_Vault_Backup.zip") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBackingUp
                    ) {
                        Text(if (isBackingUp) "Archiving Notes…" else "📦 Backup All Notes to .zip")
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRestoring
                    ) {
                        Text(if (isRestoring) "Restoring Notes…" else "📥 Restore / Import .zip Backup")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // About
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text("NoteGraph Phase 3 (Final Phase)", style = MaterialTheme.typography.bodyMedium)
            Text("Version 1.2 • Build 3", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(4.dp))
            Text(
                "Offline-first, bidirectional wiki-linking, multimedia blocks, on-device ML Kit OCR, force-directed graph, and OpenXML export.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
