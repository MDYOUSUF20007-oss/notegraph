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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notegraph.app.data.local.NoteJson
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.ui.common.NoteSecurityManager
import com.notegraph.app.ui.common.VaultPinDialog
import com.notegraph.app.ui.components.ObsidianCard
import com.notegraph.app.ui.components.ObsidianIconTile
import com.notegraph.app.ui.components.ObsidianMetallicBadge
import com.notegraph.app.ui.components.ObsidianStatTile
import com.notegraph.app.ui.components.ObsidianTokens
import com.notegraph.app.ui.theme.MinimalistBackground
import com.notegraph.app.ui.theme.ThemeManager

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
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens

    MinimalistBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        if (isLuxury) {
                            Text(
                                text = "Search",
                                style = MaterialTheme.typography.headlineMedium,
                                fontFamily = FontFamily.Serif,
                                color = lux.textPrimary
                            )
                        } else {
                            Text("Search", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isLuxury) lux.textPrimary else LocalContentColor.current
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
                    shape = RoundedCornerShape(if (isLuxury) 100.dp else 22.dp),
                    color = if (isLuxury) lux.elevatedSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    border = BorderStroke(
                        width = if (isLuxury) 1.dp else 0.75.dp,
                        color = if (isLuxury) lux.borderHairline else ThemeManager.cardBorderColor
                    ),
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = if (isLuxury) lux.accent else activePreset.accentStart,
                            modifier = Modifier.size(18.dp)
                        )
                        BasicTextField(
                            value = query,
                            onValueChange = { viewModel.onQueryChanged(it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                                fontSize = if (isLuxury) 13.sp else MaterialTheme.typography.bodyMedium.fontSize,
                                color = if (isLuxury) lux.textPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(if (isLuxury) lux.accent else activePreset.accentStart),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text(
                                        text = if (isLuxury) "SEARCH NOTES, #TAGS, [[LINKS]], OCR..." else "Search notes, #tags, [[links]], OCR…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                                        fontSize = if (isLuxury) 11.5.sp else MaterialTheme.typography.bodyMedium.fontSize,
                                        color = if (isLuxury) lux.textMuted else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    )
                                }
                                inner()
                            }
                        )
                        if (query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = if (isLuxury) lux.textMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { viewModel.onQueryChanged("") }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                val obsidianAccent = if (isLuxury) lux.accent else ObsidianTokens.resolveAccent(activePreset.accentStart)
                val accentInk = ObsidianTokens.readableAccentInk(obsidianAccent)

                if (query.isBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ObsidianCard(
                            accentColor = obsidianAccent,
                            headerContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ObsidianIconTile(
                                        icon = Icons.Rounded.Search,
                                        accentColor = obsidianAccent
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Instant Offline Search",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = ObsidianTokens.TextPrimary
                                            )
                                            ObsidianMetallicBadge(
                                                text = "INDEXED",
                                                accentColor = obsidianAccent
                                            )
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "@vault • #tags • [[links]] • OCR",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = ObsidianTokens.TextMuted
                                        )
                                    }
                                    ObsidianStatTile(
                                        value = "Local",
                                        label = "ENGINE",
                                        accentColor = obsidianAccent
                                    )
                                }
                            },
                            footerContent = {
                                Text(
                                    text = "Scans titles, Markdown bodies, [[wiki-links]], nested #tags, and extracted PDF OCR text directly in local Room DB.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = ObsidianTokens.FooterText
                                )
                            }
                        )
                    }
                } else if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ObsidianCard(
                            accentColor = obsidianAccent,
                            headerContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ObsidianIconTile(
                                        icon = Icons.Rounded.Search,
                                        accentColor = obsidianAccent
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "No results for \"$query\"",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ObsidianTokens.TextPrimary
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "0 matches in offline vault",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = ObsidianTokens.TextMuted
                                        )
                                    }
                                }
                            },
                            footerContent = {
                                Text(
                                    text = "Try searching another keyword, #tag, [[link]] title, or OCR phrase stored on device.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = ObsidianTokens.FooterText
                                )
                            }
                        )
                    }
                } else {
                    Text(
                        text = if (isLuxury) {
                            "${searchResults.size} RESULT${if (searchResults.size == 1) "" else "S"}"
                        } else {
                            "${searchResults.size} result${if (searchResults.size == 1) "" else "s"}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                        color = if (isLuxury) lux.accent else activePreset.accentStart,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(searchResults, key = { it.id }) { note ->
                            val hasRichBlocks = note.blocksJson.length > 2
                            val hasPdfBlock = note.blocksJson.contains("PdfBlock", ignoreCase = true) ||
                                note.blocksJson.contains("\"pdf\"", ignoreCase = true)

                            ObsidianCard(
                                accentColor = obsidianAccent,
                                onClick = {
                                    if (note.isLocked && NoteSecurityManager.hasPin(context)) {
                                        pendingUnlockNote = note
                                    } else {
                                        onNoteClick(note.id)
                                    }
                                },
                                headerContent = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        ObsidianIconTile(
                                            icon = when {
                                                note.isLocked -> Icons.Rounded.Lock
                                                hasPdfBlock -> Icons.Rounded.PictureAsPdf
                                                else -> Icons.Rounded.Description
                                            },
                                            accentColor = obsidianAccent,
                                            size = 38.dp,
                                            iconSize = 18.dp
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = note.title.ifBlank { "Untitled" },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ObsidianTokens.TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                ObsidianMetallicBadge(
                                                    text = when {
                                                        note.isLocked -> "LOCKED"
                                                        hasRichBlocks -> "MEDIA"
                                                        else -> "MATCH"
                                                    },
                                                    accentColor = obsidianAccent
                                                )
                                            }
                                            if (note.isLocked) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = "Protected by Vault PIN",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.5.sp,
                                                    color = accentInk
                                                )
                                            } else if (note.content.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = note.content.take(180),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = ObsidianTokens.TextSecondary
                                                )
                                            }
                                        }
                                    }
                                },
                                footerContent = {
                                    val blockInfo = if (hasRichBlocks) {
                                        "Rich media / PDF / OCR blocks indexed"
                                    } else {
                                        "Markdown text note"
                                    }
                                    Text(
                                        text = "Modified ${NoteJson.formatShortDateTime(note.updatedAt)} • $blockInfo • Local Room DB",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        lineHeight = 15.sp,
                                        color = ObsidianTokens.FooterText
                                    )
                                }
                            )
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
