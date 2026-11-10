package com.notegraph.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.ui.theme.AppThemeMode
import com.notegraph.app.ui.theme.NoteGraphColors
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
    val selectedTheme = ThemeManager.currentTheme
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))

            // ─── Theme Section (3 Luxury Themes) ───
            Text(
                text = "Color Theme System",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose your reading and editing aesthetic:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(12.dp))

            // 1. Deep Purple AMOLED
            ThemeOptionCard(
                title = "Deep Purple AMOLED",
                description = "True OLED Black (#000000), Royal Night surface (#150A26), Electric Violet accent (#A855F7), Stark White typography.",
                previewBg = NoteGraphColors.DeepPurpleAmoledBackground,
                previewAccent = NoteGraphColors.DeepPurpleAmoledAccent,
                previewBorder = NoteGraphColors.DeepPurpleAmoledCardBorder,
                previewText = NoteGraphColors.DeepPurpleAmoledText,
                isSelected = selectedTheme == AppThemeMode.DEEP_PURPLE_AMOLED,
                onClick = {
                    ThemeManager.setTheme(AppThemeMode.DEEP_PURPLE_AMOLED)
                }
            )

            Spacer(Modifier.height(8.dp))

            // 2. Kindle Parchment
            ThemeOptionCard(
                title = "Kindle Parchment",
                description = "Warm Paper (#F4F1EA), Soft Linen surface (#EAE4D7), Charcoal Ink text (#2C2C2C), Warm Sepia accent (#B45309).",
                previewBg = NoteGraphColors.KindleParchmentBackground,
                previewAccent = NoteGraphColors.KindleParchmentAccent,
                previewBorder = NoteGraphColors.KindleParchmentSepiaDivider,
                previewText = NoteGraphColors.KindleParchmentCharcoalText,
                isSelected = selectedTheme == AppThemeMode.KINDLE_PARCHMENT,
                onClick = {
                    ThemeManager.setTheme(AppThemeMode.KINDLE_PARCHMENT)
                }
            )

            Spacer(Modifier.height(8.dp))

            // 3. Matte Studio
            ThemeOptionCard(
                title = "Matte Studio",
                description = "Slate Matte (#1E2229), Graphite Box (#262B35), Crisp Silver text (#E2E8F0), Studio Cyan accent (#38BDF8).",
                previewBg = NoteGraphColors.MatteStudioBackground,
                previewAccent = NoteGraphColors.MatteStudioAccent,
                previewBorder = NoteGraphColors.MatteStudioGridBorder,
                previewText = NoteGraphColors.MatteStudioText,
                isSelected = selectedTheme == AppThemeMode.MATTE_STUDIO,
                onClick = {
                    ThemeManager.setTheme(AppThemeMode.MATTE_STUDIO)
                }
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ─── Vault Backup & Restore Section ───
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
                        "Export all notes, block structures, ink strokes, and OCR content into an open, portable .zip archive.",
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

            // ─── About Section ───
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text("NoteGraph Luxury Edition", style = MaterialTheme.typography.bodyMedium)
            Text("Version 2.0 • Tri-Theme Matrix • Interactive Checklists • Drag & Drop Folders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeOptionCard(
    title: String,
    description: String,
    previewBg: Color,
    previewAccent: Color,
    previewBorder: Color,
    previewText: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(2.dp, previewAccent, RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Palette swatch preview circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .border(2.dp, previewBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = previewBg,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            color = previewAccent,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.size(16.dp)
                        ) {}
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = previewAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
