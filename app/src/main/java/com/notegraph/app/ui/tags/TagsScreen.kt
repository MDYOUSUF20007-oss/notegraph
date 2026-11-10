package com.notegraph.app.ui.tags

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

data class TagTreeNode(
    val name: String,
    val fullPath: String,
    val children: MutableMap<String, TagTreeNode> = mutableMapOf(),
    val noteIds: MutableSet<String> = mutableSetOf()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onBack: () -> Unit,
    onNoteClick: (String) -> Unit = {},
    viewModel: TagsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsState()
    var pendingUnlockNote by remember { mutableStateOf<NoteEntity?>(null) }

    val tagTree = remember(notes) { buildTagTree(notes) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val activePreset = ThemeManager.currentGradient
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens

    val filteredNotes = remember(selectedTag, notes) {
        if (selectedTag == null) emptyList()
        else {
            val tagPattern = Regex("#${Regex.escape(selectedTag!!)}(?:[\\s,\\.]|\$)")
            notes.filter { note ->
                val fullText = "${note.title} ${note.content} ${note.blocksJson}"
                tagPattern.containsMatchIn(fullText) || fullText.contains("#${selectedTag}")
            }
        }
    }

    MinimalistBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        if (isLuxury) {
                            Text(
                                text = "Tags",
                                style = MaterialTheme.typography.headlineMedium,
                                fontFamily = FontFamily.Serif,
                                color = lux.textPrimary
                            )
                        } else {
                            Text("Tags", style = MaterialTheme.typography.titleLarge)
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
            val obsidianAccent = if (isLuxury) lux.accent else ObsidianTokens.resolveAccent(activePreset.accentStart)
            val accentInk = ObsidianTokens.readableAccentInk(obsidianAccent)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (tagTree.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
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
                                        icon = Icons.Rounded.Label,
                                        accentColor = obsidianAccent
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Tag Taxonomy",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = ObsidianTokens.TextPrimary
                                            )
                                            ObsidianMetallicBadge(
                                                text = "EMPTY",
                                                accentColor = obsidianAccent
                                            )
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "@vault • #study • #work/notes",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = ObsidianTokens.TextMuted
                                        )
                                    }
                                    ObsidianStatTile(
                                        value = "0 tags",
                                        label = "INDEX",
                                        accentColor = obsidianAccent
                                    )
                                }
                            },
                            footerContent = {
                                Text(
                                    text = "Add #study, #work/notes, or #todo/urgent inside any note body to build an offline tag tree stored in Room DB.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = ObsidianTokens.FooterText
                                )
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            ObsidianCard(
                                accentColor = obsidianAccent,
                                verticalPadding = 4.dp,
                                headerContent = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        ObsidianIconTile(
                                            icon = Icons.Rounded.Label,
                                            accentColor = obsidianAccent,
                                            size = 40.dp,
                                            iconSize = 19.dp
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "Tag Hierarchy",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ObsidianTokens.TextPrimary
                                                )
                                                ObsidianMetallicBadge(
                                                    text = "ACTIVE",
                                                    accentColor = obsidianAccent
                                                )
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = if (selectedTag != null) "Inspecting #$selectedTag" else "Select any #tag branch below",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = ObsidianTokens.TextMuted
                                            )
                                        }
                                        ObsidianStatTile(
                                            value = "${tagTree.size}",
                                            label = "ROOTS",
                                            accentColor = obsidianAccent
                                        )
                                    }
                                },
                                footerContent = {
                                    Text(
                                        text = "${notes.size} notes indexed across nested #parent/child tags • Offline local Room storage",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        lineHeight = 15.sp,
                                        color = ObsidianTokens.FooterText
                                    )
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        items(tagTree.values.toList()) { rootNode ->
                            TagNodeRow(
                                node = rootNode,
                                depth = 0,
                                selectedTag = selectedTag,
                                onTagSelected = { tag ->
                                    selectedTag = if (selectedTag == tag) null else tag
                                }
                            )
                        }

                        if (selectedTag != null) {
                            item {
                                Spacer(Modifier.height(14.dp))
                                HorizontalDivider(color = if (isLuxury) lux.dividerRule else ThemeManager.cardBorderColor)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = if (isLuxury) "#${selectedTag} // ${filteredNotes.size}" else "#${selectedTag} · ${filteredNotes.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                                    color = if (isLuxury) lux.accent else activePreset.accentStart,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            if (filteredNotes.isEmpty()) {
                                item {
                                    Text(
                                        text = "No notes found for this tag.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            } else {
                                items(filteredNotes, key = { it.id }) { note ->

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
                                                    icon = if (note.isLocked) Icons.Rounded.Lock else Icons.Rounded.Label,
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
                                                            text = if (note.isLocked) "LOCKED" else "#${selectedTag}",
                                                            accentColor = obsidianAccent
                                                        )
                                                    }
                                                    if (note.isLocked) {
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = "Locked with Vault PIN",
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 11.5.sp,
                                                            color = accentInk
                                                        )
                                                    } else if (note.content.isNotBlank()) {
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = note.content.take(150),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = ObsidianTokens.TextSecondary
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        footerContent = {
                                            Text(
                                                text = "Updated ${NoteJson.formatShortDateTime(note.updatedAt)} • Matched #${selectedTag} • Local Room storage",
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

@Composable
fun TagNodeRow(
    node: TagTreeNode,
    depth: Int,
    selectedTag: String?,
    onTagSelected: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    val hasChildren = node.children.isNotEmpty()
    val isSelected = selectedTag == node.fullPath
    val activePreset = ThemeManager.currentGradient
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens
    val rowShape = RoundedCornerShape(if (isLuxury) 6.dp else 12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(
                when {
                    isSelected && isLuxury -> lux.elevatedSurface
                    isSelected -> activePreset.accentStart.copy(alpha = 0.13f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (isLuxury && isSelected) {
                    Modifier.border(1.dp, lux.accent.copy(alpha = 0.65f), rowShape)
                } else Modifier
            )
            .clickable {
                if (hasChildren) {
                    isExpanded = !isExpanded
                }
                onTagSelected(node.fullPath)
            }
            .padding(
                start = (10 + depth * 18).dp,
                top = 8.dp,
                bottom = 8.dp,
                end = 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasChildren) {
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = if (isLuxury) lux.textMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(Modifier.width(18.dp))
        }

        Spacer(Modifier.width(6.dp))

        Text(
            text = "#${node.name}",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) {
                if (isLuxury) lux.accent else activePreset.accentStart
            } else {
                if (isLuxury) lux.textPrimary else MaterialTheme.colorScheme.onSurface
            }
        )

        Spacer(Modifier.weight(1f))

        Surface(
            shape = RoundedCornerShape(if (isLuxury) 4.dp else 10.dp),
            color = if (isLuxury) lux.recessedSurface else activePreset.accentStart.copy(alpha = 0.14f),
            border = if (isLuxury) {
                BorderStroke(1.dp, if (isSelected) lux.accent.copy(alpha = 0.65f) else lux.borderHairline)
            } else null,
            shadowElevation = 0.dp
        ) {
            Text(
                text = "${node.noteIds.size}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                color = if (isLuxury) {
                    if (isSelected) lux.accent else lux.textSecondary
                } else activePreset.accentStart,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }

    AnimatedVisibility(visible = isExpanded && hasChildren) {
        Column {
            for (child in node.children.values) {
                TagNodeRow(
                    node = child,
                    depth = depth + 1,
                    selectedTag = selectedTag,
                    onTagSelected = onTagSelected
                )
            }
        }
    }
}

private fun buildTagTree(notes: List<NoteEntity>): Map<String, TagTreeNode> {
    val roots = mutableMapOf<String, TagTreeNode>()
    val tagRegex = Regex("""#([a-zA-Z0-9_\-]+(?:/[a-zA-Z0-9_\-]+)*)""")

    for (note in notes) {
        val text = "${note.content} ${note.blocksJson}"
        val matches = tagRegex.findAll(text)
        for (match in matches) {
            val fullTag = match.groupValues[1]
            val segments = fullTag.split("/")
            var currentMap = roots
            var pathAccumulator = ""

            for ((index, segment) in segments.withIndex()) {
                pathAccumulator = if (index == 0) segment else "$pathAccumulator/$segment"
                val node = currentMap.getOrPut(segment) {
                    TagTreeNode(name = segment, fullPath = pathAccumulator)
                }
                node.noteIds.add(note.id)
                currentMap = node.children
            }
        }
    }
    return roots
}
