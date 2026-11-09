package com.notegraph.app.ui.tags

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notegraph.app.data.local.entity.NoteEntity
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
    val notes by viewModel.notes.collectAsState()

    // Build the hierarchical tag tree from notes
    val tagTree = remember(notes) { buildTagTree(notes) }
    var selectedTag by remember { mutableStateOf<String?>(null) }

    // Filter notes matching selected tag
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nested Tags Browser") },
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
        ) {
            if (tagTree.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No tags discovered.\nAdd tags like #project, #work/notes, or #todo/urgent in your notes to browse them here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    item {
                        Text(
                            "Tag Hierarchy",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
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

                    // Display filtered notes section when a tag is selected
                    if (selectedTag != null) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Notes tagged #${selectedTag} (${filteredNotes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        if (filteredNotes.isEmpty()) {
                            item {
                                Text(
                                    "No active notes found for this tag.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        } else {
                            items(filteredNotes) { note ->
                                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onNoteClick(note.id) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            note.title.ifBlank { "Untitled" },
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        if (note.content.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                note.content.take(120),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            dateFormat.format(Date(note.updatedAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (hasChildren) {
                    isExpanded = !isExpanded
                }
                onTagSelected(node.fullPath)
            }
            .padding(
                start = (12 + depth * 20).dp,
                top = 6.dp,
                bottom = 6.dp,
                end = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasChildren) {
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(18.dp)
            )
        } else {
            Spacer(Modifier.width(18.dp))
        }

        Spacer(Modifier.width(4.dp))

        Text(
            text = "#${node.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.weight(1f))

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "${node.noteIds.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
    // Match #tag or #folder/subfolder/tag
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
