package com.notegraph.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.ui.theme.AppThemeMode
import com.notegraph.app.ui.theme.GradientSwatchRow
import com.notegraph.app.ui.theme.MinimalistBackground
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
    val activePreset = ThemeManager.currentGradient
    val gradientsOn = ThemeManager.gradientsEnabled
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

    MinimalistBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Appearance & Vault",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // ─── Live Interactive Theme + Gradient Preview ───
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(ThemeManager.activeCardBrush(highlighted = true))
                        .border(0.75.dp, ThemeManager.cardBorderColor, RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = selectedTheme.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = activePreset.accentStart,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (gradientsOn) activePreset.name else "${activePreset.name} · Flat",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(ThemeManager.activeAccentBrush),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = activePreset.onAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Mini mock note card preview
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = activePreset.cardSurface,
                            border = BorderStroke(0.75.dp, activePreset.borderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Lecture Summary",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Clean contrast · distraction-free focus",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(ThemeManager.activeAccentBrush)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "New",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = activePreset.onAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── Theme Mode Matrix (AMOLED, Kindle, Matte / Mac) ───
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Theme Surface",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppThemeMode.values().forEach { mode ->
                            val isSelected = selectedTheme == mode
                            val modePreset = ThemeManager.presetFor(mode)
                            val icon = when (mode) {
                                AppThemeMode.DEEP_PURPLE_AMOLED -> Icons.Rounded.DarkMode
                                AppThemeMode.KINDLE_PARCHMENT -> Icons.Rounded.MenuBook
                                AppThemeMode.MATTE_STUDIO -> Icons.Rounded.LaptopMac
                            }
                            val subtitle = when (mode) {
                                AppThemeMode.DEEP_PURPLE_AMOLED -> "True Black"
                                AppThemeMode.KINDLE_PARCHMENT -> "E-Ink Paper"
                                AppThemeMode.MATTE_STUDIO -> "Slate Studio"
                            }

                            Surface(
                                onClick = { ThemeManager.setTheme(mode) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) modePreset.cardTint else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 0.75.dp,
                                    color = if (isSelected) modePreset.accentStart else ThemeManager.cardBorderColor
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(modePreset.accentStart, modePreset.accentEnd)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Rounded.Check else icon,
                                            contentDescription = mode.displayName,
                                            tint = modePreset.onAccent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Text(
                                        text = mode.shortLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── Choosable Gradient Colors (Per Theme) ───
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${selectedTheme.shortLabel} Gradient Palette",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Each theme remembers its own gradient color",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    ThemeManager.resetVisualsToDefault()
                                    Toast.makeText(context, "Visuals reset to default", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Reset Gradients",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        GradientSwatchRow(
                            mode = selectedTheme,
                            selectedId = ThemeManager.selectedGradientIdFor(selectedTheme),
                            onSelect = { chosen ->
                                ThemeManager.setGradient(selectedTheme, chosen.id)
                            }
                        )

                        HorizontalDivider(color = ThemeManager.cardBorderColor)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { ThemeManager.setGradientsEnabledState(!gradientsOn) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ambient Gradient Finish",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (gradientsOn) "Multi-stop accent & card sheen active" else "Minimal classic flat surfaces",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = gradientsOn,
                                onCheckedChange = { ThemeManager.setGradientsEnabledState(it) }
                            )
                        }
                    }
                }

                // ─── Vault Backup & Restore ───
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Vault Backup & Sync",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Export notes, blocks, ink strokes, and PDFs into a portable .zip archive for desktop or backup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(2.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ThemeManager.activeAccentBrush)
                                .clickable(enabled = !isBackingUp) {
                                    backupLauncher.launch("NoteGraph_Vault_Backup.zip")
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Backup,
                                    contentDescription = null,
                                    tint = activePreset.onAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isBackingUp) "Archiving Vault…" else "Backup Vault (.zip)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = activePreset.onAccent
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isRestoring,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isRestoring) "Restoring Vault…" else "Restore from .zip")
                        }
                    }
                }

                // ─── Minimalist Footer ───
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NoteGraph 2.0",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Offline-first notes, PDFs, ink & knowledge graph",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
