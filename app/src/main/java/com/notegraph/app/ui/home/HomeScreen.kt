package com.notegraph.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notegraph.app.data.local.entity.FolderEntity
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.ui.editor.ExportManager
import com.notegraph.app.ui.theme.AppThemeMode
import com.notegraph.app.ui.theme.NoteGraphColors
import com.notegraph.app.ui.theme.ThemeManager
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class FolderPreset(
    val name: String,
    val icon: String,
    val colorHex: String
)

private val FOLDER_PRESETS = listOf(
    FolderPreset("Study", "📚", "#6366F1"),
    FolderPreset("Work", "💼", "#38BDF8"),
    FolderPreset("Gaming", "🎮", "#A855F7"),
    FolderPreset("Shopping", "🛒", "#10B981"),
    FolderPreset("Reminder", "⏰", "#F59E0B")
)

private val FOLDER_ICON_OPTIONS = listOf(
    "📚", "💼", "🎮", "🛒", "⏰", "📁", "💡",
    "🏠", "💻", "🎨", "🎵", "🏋️", "✈️", "📝"
)

private val FOLDER_COLOR_OPTIONS = listOf(
    "#A855F7",
    "#6366F1",
    "#38BDF8",
    "#10B981",
    "#F59E0B",
    "#EC4899",
    "#EF4444",
    "#B45309"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNoteClick: (String) -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateGraph: () -> Unit,
    onNavigateTags: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateReader: () -> Unit = {}
) {
    val allNotes by viewModel.notes.collectAsState()
    val filteredNotes by viewModel.filteredNotes.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val todayDateStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    var showMenuForNote by remember { mutableStateOf<NoteEntity?>(null) }
    var showMovePickerForNote by remember { mutableStateOf<NoteEntity?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<FolderEntity?>(null) }

    // Drag-and-drop state from NoteCard onto FolderChips
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val folderBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var dragPointerRoot by remember { mutableStateOf(Offset.Zero) }
    var dragGrabOffset by remember { mutableStateOf(Offset.Zero) }
    var hoveredDropTargetId by remember { mutableStateOf<String?>(null) }

    val foldersById = remember(folders) { folders.associateBy { it.id } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                rootOrigin = coords.positionInRoot()
            }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                NoteGraphColors.RainbowViolet,
                                                NoteGraphColors.RainbowMagenta,
                                                NoteGraphColors.RainbowWarmOrange
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "NG",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "NoteGraph",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        ThemeCyclePill(
                            themeMode = ThemeManager.currentTheme,
                            onClick = { ThemeManager.cycleTheme() }
                        )
                        IconButton(onClick = onNavigateSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        viewModel.createNote { newId ->
                            onNoteClick(newId)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Note")
                }
            },
            bottomBar = {
                BottomGlassDock(
                    onNavigateGraph = onNavigateGraph,
                    onNavigateTags = onNavigateTags,
                    onNavigateReader = onNavigateReader,
                    onNavigateSearch = onNavigateSearch
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ─── Row 1: Daily Note Pill + Quick Status ───
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            viewModel.openOrCreateDailyNote { dailyId ->
                                onNoteClick(dailyId)
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📅", fontSize = 13.sp)
                            Text(
                                text = "$todayDateStr Journal",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (draggingNote != null) {
                        val dropFolderLabel = when (hoveredDropTargetId) {
                            null -> "Drop onto a folder ↓"
                            HomeViewModel.UNFILED_FOLDER_ID -> "Release to unfile"
                            else -> {
                                val target = foldersById[hoveredDropTargetId]
                                if (target != null) "Drop in ${target.icon} ${target.name}" else "Drop here"
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = dropFolderLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "${filteredNotes.size} notes • Hold to drag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ─── Row 2: Customizable Folder Shelf (Tap to filter, Long-press to edit, Drop notes) ───
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // All Notes
                    item(key = "folder_all") {
                        FolderChip(
                            icon = "🗂",
                            name = "All",
                            count = allNotes.size,
                            accentColor = MaterialTheme.colorScheme.primary,
                            isSelected = selectedFolderId == null,
                            isDropHovered = false,
                            showEditButton = false,
                            onClick = { viewModel.selectFolder(null) },
                            onLongClick = {},
                            onEditClick = {}
                        )
                    }

                    // Unfiled Notes (accepts drops to remove folder)
                    item(key = HomeViewModel.UNFILED_FOLDER_ID) {
                        DisposableEffect(HomeViewModel.UNFILED_FOLDER_ID) {
                            onDispose { folderBounds.remove(HomeViewModel.UNFILED_FOLDER_ID) }
                        }
                        val unfiledCount = allNotes.count { it.folderId == null }
                        FolderChip(
                            icon = "📄",
                            name = "Unfiled",
                            count = unfiledCount,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            isSelected = selectedFolderId == HomeViewModel.UNFILED_FOLDER_ID,
                            isDropHovered = hoveredDropTargetId == HomeViewModel.UNFILED_FOLDER_ID,
                            showEditButton = false,
                            modifier = Modifier.onGloballyPositioned { coords ->
                                folderBounds[HomeViewModel.UNFILED_FOLDER_ID] = coords.boundsInRoot()
                            },
                            onClick = { viewModel.selectFolder(HomeViewModel.UNFILED_FOLDER_ID) },
                            onLongClick = {},
                            onEditClick = {}
                        )
                    }

                    // User & Starter Folders
                    items(folders, key = { it.id }) { folder ->
                        DisposableEffect(folder.id) {
                            onDispose { folderBounds.remove(folder.id) }
                        }
                        val folderColor = parseColorSafe(folder.colorHex, MaterialTheme.colorScheme.primary)
                        val count = allNotes.count { it.folderId == folder.id }
                        val isSelected = selectedFolderId == folder.id
                        FolderChip(
                            icon = folder.icon,
                            name = folder.name,
                            count = count,
                            accentColor = folderColor,
                            isSelected = isSelected,
                            isDropHovered = hoveredDropTargetId == folder.id,
                            showEditButton = isSelected,
                            modifier = Modifier.onGloballyPositioned { coords ->
                                folderBounds[folder.id] = coords.boundsInRoot()
                            },
                            onClick = { viewModel.selectFolder(folder.id) },
                            onLongClick = { editingFolder = folder },
                            onEditClick = { editingFolder = folder }
                        )
                    }

                    // + New Folder button
                    item(key = "add_folder_btn") {
                        Surface(
                            onClick = { showCreateFolderDialog = true },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "New Folder",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Folder",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ─── Row 3: Masonry Note Card Grid ───
                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val activeFolder = selectedFolderId?.let { foldersById[it] }
                            Text(
                                text = if (activeFolder != null) {
                                    "No notes in ${activeFolder.icon} ${activeFolder.name} yet"
                                } else if (selectedFolderId == HomeViewModel.UNFILED_FOLDER_ID) {
                                    "No unfiled notes"
                                } else {
                                    "Your vault is ready"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Tap + to create a note, or long-press and drag any note onto a folder chip above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 92.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            val noteFolder = note.folderId?.let { foldersById[it] }
                            val isBeingDragged = draggingNote?.id == note.id

                            NoteCard(
                                note = note,
                                folder = noteFolder,
                                isBeingDragged = isBeingDragged,
                                onClick = { onNoteClick(note.id) },
                                onMenuClick = { showMenuForNote = note },
                                onDragStartRoot = { grabOffset, pointerRoot ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggingNote = note
                                    dragGrabOffset = grabOffset
                                    dragPointerRoot = pointerRoot
                                    hoveredDropTargetId = null
                                },
                                onDragUpdateRoot = { delta, hasMovedFar ->
                                    dragPointerRoot += delta
                                    val newHover = if (hasMovedFar) {
                                        findHoveredFolderKey(folderBounds, dragPointerRoot)
                                    } else null
                                    if (newHover != null && newHover != hoveredDropTargetId) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    hoveredDropTargetId = newHover
                                },
                                onDragFinish = { hasMovedFar ->
                                    val targetId = hoveredDropTargetId
                                    val dragged = draggingNote
                                    if (dragged != null && targetId != null) {
                                        viewModel.moveNoteToFolder(dragged.id, targetId)
                                        val msg = if (targetId == HomeViewModel.UNFILED_FOLDER_ID) {
                                            "Moved \"${dragged.title.ifBlank { "Untitled" }}\" to Unfiled"
                                        } else {
                                            val f = foldersById[targetId]
                                            "Moved \"${dragged.title.ifBlank { "Untitled" }}\" to ${f?.icon.orEmpty()} ${f?.name ?: "Folder"}"
                                        }
                                        scope.launch {
                                            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                                        }
                                    } else if (dragged != null && !hasMovedFar) {
                                        showMenuForNote = dragged
                                    }
                                    draggingNote = null
                                    hoveredDropTargetId = null
                                },
                                onDragCancel = {
                                    draggingNote = null
                                    hoveredDropTargetId = null
                                }
                            )
                        }
                    }
                }
            }
        }

        // ─── Floating Drag Ghost Preview Overlay ───
        val activeDragNote = draggingNote
        if (activeDragNote != null) {
            val ghostOffset = (dragPointerRoot - rootOrigin) - dragGrabOffset - Offset(0f, 36f)
            val targetFolder = hoveredDropTargetId?.let { foldersById[it] }
            val highlightColor = when (hoveredDropTargetId) {
                null -> MaterialTheme.colorScheme.primary
                HomeViewModel.UNFILED_FOLDER_ID -> MaterialTheme.colorScheme.secondary
                else -> targetFolder?.let { parseColorSafe(it.colorHex, MaterialTheme.colorScheme.primary) }
                    ?: MaterialTheme.colorScheme.primary
            }

            Surface(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            ghostOffset.x.roundToInt(),
                            ghostOffset.y.roundToInt()
                        )
                    }
                    .width(185.dp)
                    .shadow(14.dp, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                border = BorderStroke(2.dp, highlightColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = activeDragNote.title.ifBlank { "Untitled Note" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (hoveredDropTargetId) {
                            null -> "Drag onto any folder…"
                            HomeViewModel.UNFILED_FOLDER_ID -> "Release → 📄 Unfiled"
                            else -> "Release → ${targetFolder?.icon.orEmpty()} ${targetFolder?.name ?: "Folder"}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = highlightColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // ─── Dialog 1: Note Contextual Menu (Pin / Move to Folder / Delete) ───
    showMenuForNote?.let { note ->
        val currentFolder = note.folderId?.let { foldersById[it] }
        AlertDialog(
            onDismissRequest = { showMenuForNote = null },
            title = {
                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (currentFolder != null) {
                            "Folder: ${currentFolder.icon} ${currentFolder.name}"
                        } else {
                            "Folder: 📄 Unfiled"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = {
                            viewModel.togglePin(note)
                            showMenuForNote = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (note.isPinned) "Unpin Note" else "⭐ Pin to Top")
                    }

                    OutlinedButton(
                        onClick = {
                            showMovePickerForNote = note
                            showMenuForNote = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📁 Move to Folder…")
                    }

                    Button(
                        onClick = {
                            viewModel.moveToTrash(note)
                            showMenuForNote = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🗑 Delete Note")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenuForNote = null }) {
                    Text("Close")
                }
            }
        )
    }

    // ─── Dialog 2: Move Note to Folder Picker ───
    showMovePickerForNote?.let { note ->
        AlertDialog(
            onDismissRequest = { showMovePickerForNote = null },
            title = { Text("Move to Folder") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Unfiled option
                    val isUnfiled = note.folderId == null
                    Surface(
                        onClick = {
                            viewModel.moveNoteToFolder(note.id, HomeViewModel.UNFILED_FOLDER_ID)
                            showMovePickerForNote = null
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isUnfiled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("📄 Unfiled (No Folder)")
                            if (isUnfiled) {
                                Icon(Icons.Default.Check, contentDescription = "Current", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    folders.forEach { folder ->
                        val isCurrent = note.folderId == folder.id
                        val fColor = parseColorSafe(folder.colorHex, MaterialTheme.colorScheme.primary)
                        Surface(
                            onClick = {
                                viewModel.moveNoteToFolder(note.id, folder.id)
                                showMovePickerForNote = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) fColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (isCurrent) fColor else Color.Transparent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(fColor, CircleShape)
                                    )
                                    Text("${folder.icon} ${folder.name}")
                                }
                                if (isCurrent) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Current",
                                        tint = fColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMovePickerForNote = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ─── Dialog 3: Create New Folder ───
    if (showCreateFolderDialog) {
        FolderEditorDialog(
            existingFolder = null,
            onDismiss = { showCreateFolderDialog = false },
            onSave = { name, icon, colorHex ->
                viewModel.createFolder(name, icon, colorHex)
                showCreateFolderDialog = false
            },
            onDelete = null
        )
    }

    // ─── Dialog 4: Edit / Customize / Delete Existing Folder ───
    editingFolder?.let { folderToEdit ->
        FolderEditorDialog(
            existingFolder = folderToEdit,
            onDismiss = { editingFolder = null },
            onSave = { name, icon, colorHex ->
                viewModel.updateFolder(
                    folderToEdit.copy(
                        name = name,
                        icon = icon,
                        colorHex = colorHex
                    )
                )
                editingFolder = null
            },
            onDelete = {
                viewModel.deleteFolder(folderToEdit.id)
                editingFolder = null
            }
        )
    }
}

private fun findHoveredFolderKey(
    boundsMap: Map<String, Rect>,
    pointerRoot: Offset
): String? {
    for ((key, rect) in boundsMap) {
        if (pointerRoot.x >= rect.left - 14f &&
            pointerRoot.x <= rect.right + 14f &&
            pointerRoot.y >= rect.top - 14f &&
            pointerRoot.y <= rect.bottom + 14f
        ) {
            return key
        }
    }
    return null
}

@Composable
private fun ThemeCyclePill(
    themeMode: AppThemeMode,
    onClick: () -> Unit
) {
    val label = when (themeMode) {
        AppThemeMode.DEEP_PURPLE_AMOLED -> "AMOLED"
        AppThemeMode.KINDLE_PARCHMENT -> "Kindle"
        AppThemeMode.MATTE_STUDIO -> "Matte"
    }
    val dotColor = when (themeMode) {
        AppThemeMode.DEEP_PURPLE_AMOLED -> NoteGraphColors.DeepPurpleAmoledAccent
        AppThemeMode.KINDLE_PARCHMENT -> NoteGraphColors.KindleParchmentAccent
        AppThemeMode.MATTE_STUDIO -> NoteGraphColors.MatteStudioAccent
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, ThemeManager.cardBorderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChip(
    icon: String,
    name: String,
    count: Int,
    accentColor: Color,
    isSelected: Boolean,
    isDropHovered: Boolean,
    showEditButton: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val containerColor = when {
        isDropHovered -> accentColor.copy(alpha = 0.28f)
        isSelected -> accentColor.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = when {
        isDropHovered -> accentColor
        isSelected -> accentColor
        else -> ThemeManager.cardBorderColor
    }
    val borderWidth = if (isDropHovered || isSelected) 1.5.dp else 1.dp

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(icon, fontSize = 14.sp)
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected || isDropHovered) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accentColor.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            if (showEditButton) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Customize Folder",
                    tint = accentColor,
                    modifier = Modifier
                        .size(15.dp)
                        .clickable { onEditClick() }
                )
            }
        }
    }
}

@Composable
private fun BottomGlassDock(
    onNavigateGraph: () -> Unit,
    onNavigateTags: () -> Unit,
    onNavigateReader: () -> Unit,
    onNavigateSearch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, ThemeManager.cardBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockActionItem(
                    label = "Notes",
                    icon = Icons.Default.List,
                    isActive = true,
                    onClick = {}
                )
                DockActionItem(
                    label = "Graph",
                    icon = Icons.Default.Share,
                    isActive = false,
                    onClick = onNavigateGraph
                )
                DockActionItem(
                    label = "Tags",
                    icon = Icons.Default.Star,
                    isActive = false,
                    onClick = onNavigateTags
                )
                DockActionItem(
                    label = "Reader",
                    icon = Icons.Default.MenuBook,
                    isActive = false,
                    onClick = onNavigateReader
                )
                DockActionItem(
                    label = "Search",
                    icon = Icons.Default.Search,
                    isActive = false,
                    onClick = onNavigateSearch
                )
            }
        }
    }
}

@Composable
private fun DockActionItem(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = contentColor,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun NoteCard(
    note: NoteEntity,
    folder: FolderEntity?,
    isBeingDragged: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDragStartRoot: (grabOffset: Offset, pointerRoot: Offset) -> Unit,
    onDragUpdateRoot: (delta: Offset, hasMovedFar: Boolean) -> Unit,
    onDragFinish: (hasMovedFar: Boolean) -> Unit,
    onDragCancel: () -> Unit
) {
    val latestNote by rememberUpdatedState(note)
    val currentOnDragStart by rememberUpdatedState(onDragStartRoot)
    val currentOnDragUpdate by rememberUpdatedState(onDragUpdateRoot)
    val currentOnDragFinish by rememberUpdatedState(onDragFinish)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    var cardBoundsRoot by remember { mutableStateOf(Rect.Zero) }

    val blocks = remember(note.blocksJson) { ExportManager.parseBlocks(note) }
    val checklistBlocks = remember(blocks) { blocks.filterIsInstance<NoteBlock.ChecklistBlock>() }
    val totalTasks = remember(checklistBlocks) { checklistBlocks.sumOf { it.items.size } }
    val doneTasks = remember(checklistBlocks) { checklistBlocks.sumOf { b -> b.items.count { it.isChecked } } }
    val firstChecklistItems = remember(checklistBlocks) {
        checklistBlocks.flatMap { it.items }.filter { it.text.isNotBlank() }.take(2)
    }
    val hasAudio = remember(blocks) { blocks.any { it is NoteBlock.AudioBlock } }
    val hasImage = remember(blocks) { blocks.any { it is NoteBlock.ImageBlock } }

    val previewStrokes = remember(blocks) {
        val json = Json { ignoreUnknownKeys = true }
        val firstInk = blocks.filterIsInstance<NoteBlock.InkBlock>().firstOrNull { it.strokesJson.length > 4 }
        if (firstInk != null) {
            try {
                json.decodeFromString<List<InkStroke>>(firstInk.strokesJson)
                    .filter { it.points.size >= 2 }
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val folderColor = folder?.let { parseColorSafe(it.colorHex, MaterialTheme.colorScheme.primary) }
    val themeMode = ThemeManager.currentTheme

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isBeingDragged) 0.3f else 1f)
            .onGloballyPositioned { coords ->
                cardBoundsRoot = coords.boundsInRoot()
            }
            .pointerInput(note.id) {
                var totalDelta = Offset.Zero
                var movedFar = false
                detectDragGesturesAfterLongPress(
                    onDragStart = { localOffset ->
                        totalDelta = Offset.Zero
                        movedFar = false
                        currentOnDragStart(localOffset, cardBoundsRoot.topLeft + localOffset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDelta += dragAmount
                        if (totalDelta.x * totalDelta.x + totalDelta.y * totalDelta.y > 24f * 24f) {
                            movedFar = true
                        }
                        currentOnDragUpdate(dragAmount, movedFar)
                    },
                    onDragEnd = {
                        currentOnDragFinish(movedFar)
                    },
                    onDragCancel = {
                        currentOnDragCancel()
                    }
                )
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (note.isPinned) 1.5.dp else 1.dp,
            color = if (note.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            else ThemeManager.cardBorderColor
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top badge row: Folder pill / Pin + ⋮ Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (note.isPinned) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (folder != null && folderColor != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = folderColor.copy(alpha = 0.18f),
                            border = BorderStroke(0.5.dp, folderColor.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = "${folder.icon} ${folder.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = folderColor,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Note Options",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Note Title
            Text(
                text = latestNote.title.ifEmpty { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Note Body Snippet
            if (latestNote.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = latestNote.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (previewStrokes.isEmpty() && checklistBlocks.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Empty note…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            // Live Mini Ink Canvas Preview
            if (previewStrokes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                MiniInkPreviewCanvas(
                    strokes = previewStrokes,
                    themeMode = themeMode
                )
            }

            // Checklist Progress Preview
            if (checklistBlocks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = "☑ $doneTasks/$totalTasks done",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        firstChecklistItems.forEach { item ->
                            val mark = if (item.isChecked) "✓" else "○"
                            Text(
                                text = "$mark ${item.text}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Date + Media Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormat.format(Date(latestNote.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                if (hasAudio || hasImage) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (hasAudio) Text("🎙", fontSize = 11.sp)
                        if (hasImage) Text("🖼", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniInkPreviewCanvas(
    strokes: List<InkStroke>,
    themeMode: AppThemeMode
) {
    val bgColor = when (themeMode) {
        AppThemeMode.DEEP_PURPLE_AMOLED -> Color(0xFF07030F)
        AppThemeMode.KINDLE_PARCHMENT -> NoteGraphColors.KindleParchmentBackground
        AppThemeMode.MATTE_STUDIO -> NoteGraphColors.MatteStudioBackground
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(0.5.dp, ThemeManager.cardBorderColor, RoundedCornerShape(8.dp))
            .clipToBounds()
    ) {
        val allPts = strokes.flatMap { it.points }
        if (allPts.isEmpty()) return@Canvas

        val minX = allPts.minOf { it.x }
        val maxX = allPts.maxOf { it.x }
        val minY = allPts.minOf { it.y }
        val maxY = allPts.maxOf { it.y }

        val rawW = (maxX - minX).coerceAtLeast(10f)
        val rawH = (maxY - minY).coerceAtLeast(10f)
        val pad = 8f
        val availW = (size.width - pad * 2f).coerceAtLeast(1f)
        val availH = (size.height - pad * 2f).coerceAtLeast(1f)
        val scale = minOf(availW / rawW, availH / rawH)
        val xOffset = (size.width - rawW * scale) / 2f
        val yOffset = (size.height - rawH * scale) / 2f

        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            val rawHex = stroke.colorHex
            val adaptedHex = when {
                themeMode == AppThemeMode.KINDLE_PARCHMENT && rawHex.equals("#FFFFFF", ignoreCase = true) -> "#2C2C2C"
                themeMode != AppThemeMode.KINDLE_PARCHMENT && rawHex.equals("#2C2C2C", ignoreCase = true) -> "#FFFFFF"
                else -> rawHex
            }
            val baseColor = parseColorSafe(
                adaptedHex,
                if (themeMode == AppThemeMode.KINDLE_PARCHMENT) NoteGraphColors.KindleParchmentCharcoalText else Color.White
            )
            val color = baseColor.copy(alpha = if (stroke.isHighlighter) 0.45f else 0.95f)

            val path = Path().apply {
                moveTo(
                    xOffset + (stroke.points[0].x - minX) * scale,
                    yOffset + (stroke.points[0].y - minY) * scale
                )
                for (i in 1 until stroke.points.size) {
                    lineTo(
                        xOffset + (stroke.points[i].x - minX) * scale,
                        yOffset + (stroke.points[i].y - minY) * scale
                    )
                }
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = (stroke.strokeWidth * scale).coerceIn(1.5f, 4.5f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun FolderEditorDialog(
    existingFolder: FolderEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, colorHex: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(existingFolder) { mutableStateOf(existingFolder?.name ?: "") }
    var selectedIcon by remember(existingFolder) { mutableStateOf(existingFolder?.icon ?: "📁") }
    var selectedColorHex by remember(existingFolder) { mutableStateOf(existingFolder?.colorHex ?: "#A855F7") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existingFolder == null) "Create Custom Folder" else "Customize Folder")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick Presets (Study, Work, Gaming, Shopping, Reminder)
                Text(
                    "Quick Category Presets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FOLDER_PRESETS.forEach { preset ->
                        AssistChip(
                            onClick = {
                                name = preset.name
                                selectedIcon = preset.icon
                                selectedColorHex = preset.colorHex
                            },
                            label = { Text("${preset.icon} ${preset.name}", fontSize = 12.sp) }
                        )
                    }
                }

                // Folder Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder Name") },
                    placeholder = { Text("e.g., Study, Work, Gaming…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Emoji Icon Selector
                Text(
                    "Choose Icon",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FOLDER_ICON_OPTIONS.forEach { emoji ->
                        val isChosen = emoji == selectedIcon
                        Surface(
                            onClick = { selectedIcon = emoji },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                width = if (isChosen) 1.5.dp else 1.dp,
                                color = if (isChosen) MaterialTheme.colorScheme.primary else ThemeManager.cardBorderColor
                            )
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Color Swatch Selector
                Text(
                    "Choose Color",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FOLDER_COLOR_OPTIONS.forEach { hex ->
                        val swatchColor = parseColorSafe(hex, MaterialTheme.colorScheme.primary)
                        val isChosen = hex.equals(selectedColorHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(swatchColor, CircleShape)
                                .border(
                                    width = if (isChosen) 2.5.dp else 1.dp,
                                    color = if (isChosen) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChosen) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected Color",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (existingFolder != null && onDelete != null) {
                    HorizontalDivider()
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Folder",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Folder (keeps notes)")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.trim().ifBlank { "New Folder" }
                    onSave(finalName, selectedIcon, selectedColorHex)
                }
            ) {
                Text(if (existingFolder == null) "Create" else "Save")
            }
        }
    )
}

private fun parseColorSafe(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    fallback
}
