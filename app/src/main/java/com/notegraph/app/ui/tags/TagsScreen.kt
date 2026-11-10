package com.notegraph.app.ui.tags

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
                    title = { Text("Tags", style = MaterialTheme.typography.titleLarge) },
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
            ) {
                if (tagTree.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tags yet. Use #study, #work/notes, or #todo/urgent inside any note to organize here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Text(
                                text = "Hierarchy",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
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
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = ThemeManager.cardBorderColor)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "#${selectedTag} · ${filteredNotes.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = activePreset.accentStart,
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
                                    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
                                    val cardShape = RoundedCornerShape(16.dp)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
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
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                            }
                                            if (note.isLocked) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = "Locked with Vault PIN",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = activePreset.accentEnd
                                                )
                                            } else if (note.content.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = note.content.take(120),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 2,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(Modifier.height(6.dp))
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) activePreset.accentStart.copy(alpha = 0.13f) else Color.Transparent
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(Modifier.width(18.dp))
        }

        Spacer(Modifier.width(6.dp))

        Text(
            text = "#${node.name}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) activePreset.accentStart else MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.weight(1f))

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = activePreset.accentStart.copy(alpha = 0.14f)
        ) {
            Text(
                text = "${node.noteIds.size}",
                style = MaterialTheme.typography.labelSmall,
                color = activePreset.accentStart,
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
