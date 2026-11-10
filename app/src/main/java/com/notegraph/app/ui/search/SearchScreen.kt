package com.notegraph.app.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.ui.common.NoteSecurityManager
import com.notegraph.app.ui.common.VaultPinDialog
import com.notegraph.app.ui.theme.MinimalistBackground
import com.notegraph.app.ui.theme.ThemeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNoteClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val query by viewModel.query.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var pendingUnlockNote by remember { mutableStateOf<NoteEntity?>(null) }
    val activePreset = ThemeManager.currentGradient

    MinimalistBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text("Search", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
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
            ) {
                // Minimalist Capsule Search Bar
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = activePreset.accentStart,
                            modifier = Modifier.size(18.dp)
                        )
                        BasicTextField(
                            value = query,
                            onValueChange = { viewModel.onQueryChanged(it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(activePreset.accentStart),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Search notes, #tags, [[links]], OCR…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    )
                                }
                                inner()
                            }
                        )
                        if (query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { viewModel.onQueryChanged("") }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (query.isBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            Text(
                                text = "Instant Offline Search",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Finds matches across titles, note bodies, [[wiki-links]], #tags, and extracted OCR text.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No results for \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "${searchResults.size} result${if (searchResults.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = activePreset.accentStart,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()) }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(searchResults, key = { it.id }) { note ->
                            val cardShape = RoundedCornerShape(16.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .background(ThemeManager.activeCardBrush(highlighted = false))
                                    .border(0.75.dp, ThemeManager.cardBorderColor, cardShape)
                                    .clickable {
                                        if (note.isLocked && NoteSecurityManager.hasPin(context)) {
                                            pendingUnlockNote = note
                                        } else {
                                            onNoteClick(note.id)
                                        }
                                    }
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (note.isLocked) {
                                            Icon(
                                                imageVector = Icons.Rounded.Lock,
                                                contentDescription = "Locked",
                                                tint = activePreset.accentEnd,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = note.title.ifBlank { "Untitled" },
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    if (note.isLocked) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Protected by Vault PIN",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = activePreset.accentEnd
                                        )
                                    } else {
                                        if (note.content.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = note.content.take(160),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 3,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (note.blocksJson.length > 2) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = "Includes media / PDF / OCR blocks",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = activePreset.accentEnd
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = dateFormat.format(Date(note.updatedAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingUnlockNote?.let { locked ->
        VaultPinDialog(
            isSettingNewPin = false,
            title = "Unlock Note",
            subtitle = "\"${locked.title.ifBlank { "Untitled" }}\" is protected.",
            onDismiss = { pendingUnlockNote = null },
            onVerify = { attempt -> NoteSecurityManager.verifyPin(context, attempt) },
            onSuccess = {
                pendingUnlockNote = null
                onNoteClick(locked.id)
            }
        )
    }
}
