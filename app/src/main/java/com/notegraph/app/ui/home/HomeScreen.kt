package com.notegraph.app.ui.home

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Today
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
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
import com.notegraph.app.ui.common.NoteSecurityManager
import com.notegraph.app.ui.common.VaultPinDialog
import com.notegraph.app.ui.components.ObsidianCard
import com.notegraph.app.ui.components.ObsidianIconTile
import com.notegraph.app.ui.components.ObsidianMetallicBadge
import com.notegraph.app.ui.components.ObsidianStatTile
import com.notegraph.app.ui.components.ObsidianTokens
import com.notegraph.app.ui.editor.ExportManager
import com.notegraph.app.ui.theme.AppThemeMode
import com.notegraph.app.ui.theme.GradientFloatingActionButton
import com.notegraph.app.ui.theme.MinimalistBackground
import com.notegraph.app.ui.theme.NoteGraphColors
import com.notegraph.app.ui.theme.ThemeManager
import com.notegraph.app.ui.theme.ThemeStudioSheetContent
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
    val trashNotes by viewModel.trashNotes.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val todayDateStr = remember {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())
    }

    var showMenuForNote by remember { mutableStateOf<NoteEntity?>(null) }
    var showMovePickerForNote by remember { mutableStateOf<NoteEntity?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var showTrashDialog by remember { mutableStateOf(false) }
    var showThemeStudioSheet by remember { mutableStateOf(false) }
    var pendingUnlockNote by remember { mutableStateOf<NoteEntity?>(null) }
    var pendingSetPinForNote by remember { mutableStateOf<NoteEntity?>(null) }

    val openPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.importPdfAsNewNote(uri) { noteId ->
            if (noteId != null) {
                onNoteClick(noteId)
            } else {
                Toast.makeText(context, "Could not open selected PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Drag-and-drop state from NoteCard onto FolderChips
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val folderBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var dragPointerRoot by remember { mutableStateOf(Offset.Zero) }
    var dragGrabOffset by remember { mutableStateOf(Offset.Zero) }
    var hoveredDropTargetId by remember { mutableStateOf<String?>(null) }

    val foldersById = remember(folders) { folders.associateBy { it.id } }
    val activePreset = ThemeManager.currentGradient

    MinimalistBackground(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                rootOrigin = coords.positionInRoot()
            }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ThemeManager.activeAccentBrush),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "N",
                                    color = activePreset.onAccent,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text(
                                    text = "NoteGraph",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${filteredNotes.size} note${if (filteredNotes.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                )
                            }
                        }
                    },
                    actions = {
                        ThemeStudioPill(
                            themeMode = ThemeManager.currentTheme,
                            onClick = { showThemeStudioSheet = true }
                        )
                        IconButton(onClick = onNavigateSettings) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Appearance & Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                GradientFloatingActionButton(
                    onClick = {
                        viewModel.createNote { newId ->
                            onNoteClick(newId)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New Note",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = "New"
                )
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
                // ─── Minimalist Utility Pills (Daily, Open PDF, Templates, Trash) ───
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickActionPill(
                        icon = Icons.Rounded.Today,
                        label = todayDateStr,
                        highlighted = true,
                        onClick = {
                            viewModel.openOrCreateDailyNote { dailyId ->
                                onNoteClick(dailyId)
                            }
                        }
                    )

                    QuickActionPill(
                        icon = Icons.Rounded.PictureAsPdf,
                        label = "Open PDF",
                        highlighted = false,
                        onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }
                    )

                    QuickActionPill(
                        icon = Icons.Rounded.Layers,
                        label = "Templates",
                        highlighted = false,
                        onClick = { showTemplatesDialog = true }
                    )

                    if (trashNotes.isNotEmpty()) {
                        Surface(
                            onClick = { showTrashDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Trash",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${trashNotes.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    if (draggingNote != null) {
                        val dropFolderLabel = when (hoveredDropTargetId) {
                            null -> "Drop onto a folder"
                            HomeViewModel.UNFILED_FOLDER_ID -> "Release to unfile"
                            else -> {
                                val target = foldersById[hoveredDropTargetId]
                                if (target != null) "Drop in ${target.name}" else "Drop here"
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = activePreset.accentStart.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, activePreset.accentStart)
                        ) {
                            Text(
                                text = dropFolderLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = activePreset.accentStart,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // ─── Minimalist Folder Shelf (Tap to filter, Long-press to customize, Drop notes) ───
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item(key = "folder_all") {
                        FolderChip(
                            vectorIcon = Icons.Rounded.FolderOpen,
                            emojiIcon = null,
                            name = "All",
                            count = allNotes.size,
                            accentColor = activePreset.accentStart,
                            isSelected = selectedFolderId == null,
                            isDropHovered = false,
                            showEditButton = false,
                            onClick = { viewModel.selectFolder(null) },
                            onLongClick = {},
                            onEditClick = {}
                        )
                    }

                    item(key = HomeViewModel.UNFILED_FOLDER_ID) {
                        DisposableEffect(HomeViewModel.UNFILED_FOLDER_ID) {
                            onDispose { folderBounds.remove(HomeViewModel.UNFILED_FOLDER_ID) }
                        }
                        val unfiledCount = allNotes.count { it.folderId == null }
                        FolderChip(
                            vectorIcon = Icons.Rounded.Description,
                            emojiIcon = null,
                            name = "Unfiled",
                            count = unfiledCount,
                            accentColor = activePreset.accentEnd,
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

                    items(folders, key = { it.id }) { folder ->
                        DisposableEffect(folder.id) {
                            onDispose { folderBounds.remove(folder.id) }
                        }
                        val folderColor = parseColorSafe(folder.colorHex, activePreset.accentStart)
                        val count = allNotes.count { it.folderId == folder.id }
                        val isSelected = selectedFolderId == folder.id
                        FolderChip(
                            vectorIcon = null,
                            emojiIcon = folder.icon,
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

                    item(key = "add_folder_btn") {
                        Surface(
                            onClick = { showCreateFolderDialog = true },
                            shape = RoundedCornerShape(18.dp),
                            color = Color.Transparent,
                            border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "New Folder",
                                    tint = activePreset.accentStart,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Folder",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = activePreset.accentStart,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // ─── Minimalist Masonry Note Grid ───
                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val activeFolder = selectedFolderId?.let { foldersById[it] }
                        val emptyAccent = activeFolder?.let {
                            ObsidianTokens.resolveAccent(parseColorSafe(it.colorHex, activePreset.accentStart))
                        } ?: ObsidianTokens.resolveAccent(activePreset.accentStart)

                        ObsidianCard(
                            accentColor = emptyAccent,
                            headerContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ObsidianIconTile(
                                        icon = if (activeFolder != null) Icons.Rounded.Folder else Icons.Rounded.Description,
                                        accentColor = emptyAccent
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = when {
                                                    activeFolder != null -> activeFolder.name
                                                    selectedFolderId == HomeViewModel.UNFILED_FOLDER_ID -> "Unfiled Notes"
                                                    else -> "Clean Slate"
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = ObsidianTokens.TextPrimary
                                            )
                                            ObsidianMetallicBadge(
                                                text = "READY",
                                                accentColor = emptyAccent
                                            )
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "@vault • ${activePreset.name}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = ObsidianTokens.TextMuted
                                        )
                                    }
                                    ObsidianStatTile(
                                        value = "0 notes",
                                        label = "VAULT",
                                        accentColor = emptyAccent
                                    )
                                }
                            },
                            footerContent = {
                                Text(
                                    text = "Tap + New below to write, or long-press a note card to drag it into a folder chip above. All notes remain offline in local Room storage.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = ObsidianTokens.FooterText
                                )
                            }
                        )
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(162.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalItemSpacing = 2.dp
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            val noteFolder = note.folderId?.let { foldersById[it] }
                            val isBeingDragged = draggingNote?.id == note.id

                            NoteCard(
                                note = note,
                                folder = noteFolder,
                                isBeingDragged = isBeingDragged,
                                onClick = {
                                    if (note.isLocked && NoteSecurityManager.hasPin(context)) {
                                        pendingUnlockNote = note
                                    } else {
                                        onNoteClick(note.id)
                                    }
                                },
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
                                            "Moved to Unfiled"
                                        } else {
                                            val f = foldersById[targetId]
                                            "Moved to ${f?.name ?: "Folder"}"
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
                null -> activePreset.accentStart
                HomeViewModel.UNFILED_FOLDER_ID -> activePreset.accentEnd
                else -> targetFolder?.let { parseColorSafe(it.colorHex, activePreset.accentStart) }
                    ?: activePreset.accentStart
            }

            ObsidianCard(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            ghostOffset.x.roundToInt(),
                            ghostOffset.y.roundToInt()
                        )
                    }
                    .width(205.dp),
                accentColor = highlightColor,
                verticalPadding = 0.dp,
                headerPadding = 12.dp,
                headerContent = {
                    Text(
                        text = activeDragNote.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ObsidianTokens.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                footerContent = {
                    Text(
                        text = when (hoveredDropTargetId) {
                            null -> "Drag onto folder chip…"
                            HomeViewModel.UNFILED_FOLDER_ID -> "Release -> Move to Unfiled"
                            else -> "Release -> Move to ${targetFolder?.name ?: "Folder"}"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = ObsidianTokens.readableAccentInk(ObsidianTokens.resolveAccent(highlightColor))
                    )
                }
            )
        }
    }

    // ─── Live Appearance & Gradient Studio Bottom Sheet ───
    if (showThemeStudioSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showThemeStudioSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            ThemeStudioSheetContent(
                onOpenSettings = onNavigateSettings,
                onClose = { showThemeStudioSheet = false }
            )
        }
    }

    // ─── Dialog 1: Note Contextual Menu ───
    showMenuForNote?.let { note ->
        val currentFolder = note.folderId?.let { foldersById[it] }
        AlertDialog(
            onDismissRequest = { showMenuForNote = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (currentFolder != null) "Folder · ${currentFolder.name}" else "Folder · Unfiled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = {
                            viewModel.togglePin(note)
                            showMenuForNote = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                    ) {
                        Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (note.isPinned) "Unpin Note" else "Pin to Top")
                    }

                    OutlinedButton(
                        onClick = {
                            if (note.isLocked) {
                                viewModel.toggleLock(note)
                            } else if (NoteSecurityManager.hasPin(context)) {
                                viewModel.toggleLock(note)
                            } else {
                                pendingSetPinForNote = note
                            }
                            showMenuForNote = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                    ) {
                        Icon(
                            if (note.isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (note.isLocked) "Unlock Note" else "Lock with Vault PIN")
                    }

                    OutlinedButton(
                        onClick = {
                            showMovePickerForNote = note
                            showMenuForNote = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                    ) {
                        Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Move to Folder…")
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
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Move to Trash")
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Move to Folder") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isUnfiled = note.folderId == null
                    Surface(
                        onClick = {
                            viewModel.moveNoteToFolder(note.id, HomeViewModel.UNFILED_FOLDER_ID)
                            showMovePickerForNote = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isUnfiled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                        border = BorderStroke(0.75.dp, if (isUnfiled) activePreset.accentStart else ThemeManager.cardBorderColor),
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
                                Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Unfiled")
                            }
                            if (isUnfiled) {
                                Icon(Icons.Rounded.Check, contentDescription = "Current", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    folders.forEach { folder ->
                        val isCurrent = note.folderId == folder.id
                        val fColor = parseColorSafe(folder.colorHex, activePreset.accentStart)
                        Surface(
                            onClick = {
                                viewModel.moveNoteToFolder(note.id, folder.id)
                                showMovePickerForNote = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) fColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                            border = BorderStroke(0.75.dp, if (isCurrent) fColor else ThemeManager.cardBorderColor),
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
                                            .size(9.dp)
                                            .background(fColor, CircleShape)
                                    )
                                    Text("${folder.icon} ${folder.name}")
                                }
                                if (isCurrent) {
                                    Icon(
                                        Icons.Rounded.Check,
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

    // ─── Dialog 5: Note Templates Picker ───
    if (showTemplatesDialog) {
        AlertDialog(
            onDismissRequest = { showTemplatesDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Create from Template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TemplateOptionCard(
                        icon = Icons.Rounded.PictureAsPdf,
                        title = "PDF Markup",
                        subtitle = "Import PDF & annotate directly above pages",
                        onClick = {
                            showTemplatesDialog = false
                            openPdfLauncher.launch(arrayOf("application/pdf"))
                        }
                    )
                    TemplateOptionCard(
                        icon = Icons.Rounded.Brush,
                        title = "Cornell Study & Sketch",
                        subtitle = "Cornell ruled ink canvas + structured notes",
                        onClick = {
                            showTemplatesDialog = false
                            viewModel.createNoteFromTemplate("CORNELL") { onNoteClick(it) }
                        }
                    )
                    TemplateOptionCard(
                        icon = Icons.Rounded.People,
                        title = "Meeting / People CRM",
                        subtitle = "Linked [[Person]] header + action items",
                        onClick = {
                            showTemplatesDialog = false
                            viewModel.createNoteFromTemplate("MEETING") { onNoteClick(it) }
                        }
                    )
                    TemplateOptionCard(
                        icon = Icons.Rounded.CheckBox,
                        title = "To-Do Checklist",
                        subtitle = "Interactive task list with progress bar",
                        onClick = {
                            showTemplatesDialog = false
                            viewModel.createNoteFromTemplate("CHECKLIST") { onNoteClick(it) }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplatesDialog = false }) { Text("Close") }
            }
        )
    }

    // ─── Dialog 6: Trash / Recycle Bin Recovery ───
    if (showTrashDialog) {
        AlertDialog(
            onDismissRequest = { showTrashDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Trash (${trashNotes.size})") },
            text = {
                if (trashNotes.isEmpty()) {
                    Text("Trash is empty.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        trashNotes.forEach { tNote ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                                border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = tNote.title.ifBlank { "Untitled" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.restoreFromTrash(tNote.id) }) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "Restore")
                                    }
                                    IconButton(onClick = { viewModel.deletePermanently(tNote.id) }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete Forever",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrashDialog = false }) { Text("Done") }
            }
        )
    }

    // ─── Dialog 7: Unlock PIN Prompt ───
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

    // ─── Dialog 8: First-Time Set Vault PIN ───
    pendingSetPinForNote?.let { noteToLock ->
        VaultPinDialog(
            isSettingNewPin = true,
            title = "Set Vault PIN",
            subtitle = "Create a 4-8 digit PIN to lock sensitive notes.",
            onDismiss = { pendingSetPinForNote = null },
            onVerify = { true },
            onSuccess = { pin ->
                NoteSecurityManager.setPin(context, pin)
                viewModel.toggleLock(noteToLock)
                pendingSetPinForNote = null
                Toast.makeText(context, "Note locked", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun QuickActionPill(
    icon: ImageVector,
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val activePreset = ThemeManager.currentGradient
    val shape = RoundedCornerShape(20.dp)
    if (highlighted) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(ThemeManager.activeAccentBrush)
                .clickable(onClick = onClick)
                .padding(horizontal = 13.dp, vertical = 7.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = activePreset.onAccent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = activePreset.onAccent
                )
            }
        }
    } else {
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = activePreset.accentStart,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun TemplateOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val activePreset = ThemeManager.currentGradient
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
        border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(activePreset.accentStart.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = activePreset.accentStart,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
private fun ThemeStudioPill(
    themeMode: AppThemeMode,
    onClick: () -> Unit
) {
    val preset = ThemeManager.currentGradient
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(preset.accentStart, preset.accentEnd))
                    )
            )
            Text(
                text = themeMode.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChip(
    vectorIcon: ImageVector?,
    emojiIcon: String?,
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
        isDropHovered -> accentColor.copy(alpha = 0.25f)
        isSelected -> accentColor.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val borderColor = when {
        isDropHovered -> accentColor
        isSelected -> accentColor.copy(alpha = 0.75f)
        else -> ThemeManager.cardBorderColor
    }
    val borderWidth = if (isDropHovered || isSelected) 1.25.dp else 0.75.dp

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
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (vectorIcon != null) {
                Icon(
                    imageVector = vectorIcon,
                    contentDescription = null,
                    tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            } else if (!emojiIcon.isNullOrBlank()) {
                Text(emojiIcon, fontSize = 13.sp)
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected || isDropHovered) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
            if (showEditButton) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Customize Folder",
                    tint = accentColor,
                    modifier = Modifier
                        .size(14.dp)
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f),
            border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockActionItem(
                    label = "Notes",
                    icon = Icons.Rounded.Description,
                    isActive = true,
                    onClick = {}
                )
                DockActionItem(
                    label = "Graph",
                    icon = Icons.Rounded.Share,
                    isActive = false,
                    onClick = onNavigateGraph
                )
                DockActionItem(
                    label = "Tags",
                    icon = Icons.Rounded.Label,
                    isActive = false,
                    onClick = onNavigateTags
                )
                DockActionItem(
                    label = "Reader",
                    icon = Icons.Rounded.MenuBook,
                    isActive = false,
                    onClick = onNavigateReader
                )
                DockActionItem(
                    label = "Search",
                    icon = Icons.Rounded.Search,
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
    val activePreset = ThemeManager.currentGradient
    val contentColor = if (isActive) {
        activePreset.accentStart
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) activePreset.accentStart.copy(alpha = 0.13f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
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
    val pdfBlocks = remember(blocks) { blocks.filterIsInstance<NoteBlock.PdfBlock>() }
    val hasPdf = pdfBlocks.isNotEmpty()

    val previewStrokes = remember(blocks) {
        val json = Json { ignoreUnknownKeys = true }
        val firstInk = blocks.filterIsInstance<NoteBlock.InkBlock>().firstOrNull { it.strokesJson.length > 4 }
        if (firstInk != null) {
            try {
                json.decodeFromString<List<InkStroke>>(firstInk.strokesJson)
                    .filter { it.points.size >= 2 }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()) }
    val activePreset = ThemeManager.currentGradient
    val themeAccent = ObsidianTokens.resolveAccent(activePreset.accentStart)
    val folderColor = folder?.let { ObsidianTokens.resolveAccent(parseColorSafe(it.colorHex, themeAccent)) }
    val cardAccent = themeAccent
    val badgeColor = folderColor ?: themeAccent
    val accentInk = ObsidianTokens.readableAccentInk(cardAccent)
    val folderInk = ObsidianTokens.readableAccentInk(badgeColor)
    val themeMode = ThemeManager.currentTheme

    val primaryIcon = when {
        latestNote.isLocked -> Icons.Rounded.Lock
        hasPdf -> Icons.Rounded.PictureAsPdf
        previewStrokes.isNotEmpty() -> Icons.Rounded.Brush
        hasAudio -> Icons.Rounded.Mic
        checklistBlocks.isNotEmpty() -> Icons.Rounded.CheckBox
        else -> Icons.Rounded.Description
    }

    val mediaSummaryParts = remember(hasPdf, previewStrokes, hasAudio, hasImage, checklistBlocks, latestNote.isLocked) {
        buildList {
            if (hasPdf) add("${pdfBlocks.size} PDF")
            if (previewStrokes.isNotEmpty()) add("Ink")
            if (hasAudio) add("Voice")
            if (hasImage) add("OCR")
            if (checklistBlocks.isNotEmpty()) add("$doneTasks/$totalTasks tasks")
            if (latestNote.isLocked) add("PIN locked")
        }
    }

    ObsidianCard(
        modifier = Modifier
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
            },
        accentColor = themeAccent,
        onClick = onClick,
        verticalPadding = 4.dp,
        headerContent = {
            // Tier 1 Header: Icon Frame + Status/Folder Pill + Overflow Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ObsidianIconTile(
                        icon = primaryIcon,
                        accentColor = badgeColor,
                        size = 30.dp,
                        iconSize = 15.dp
                    )

                    if (note.isPinned) {
                        ObsidianMetallicBadge(
                            text = "PIN",
                            accentColor = themeAccent
                        )
                    } else if (note.isLocked) {
                        ObsidianMetallicBadge(
                            text = "LOCKED",
                            accentColor = themeAccent
                        )
                    }

                    if (folder != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(folderInk)
                            )
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = folderInk,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Note Options",
                        modifier = Modifier.size(15.dp),
                        tint = ObsidianTokens.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Note Title
            Text(
                text = latestNote.title.ifEmpty { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = ObsidianTokens.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Note Body Snippet / Security Notice
            if (latestNote.isLocked) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Protected by Vault PIN",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = ObsidianTokens.readableAccentInk(themeAccent)
                )
            } else {
                if (latestNote.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = latestNote.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = ObsidianTokens.TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (previewStrokes.isEmpty() && checklistBlocks.isEmpty() && !hasPdf) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Empty canvas",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ObsidianTokens.TextMuted.copy(alpha = 0.72f)
                    )
                }

                // PDF Attachment Indicator
                if (hasPdf) {
                    val firstPdf = pdfBlocks.first()
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianTokens.StatTileSurface,
                        border = BorderStroke(0.75.dp, cardAccent.copy(alpha = 0.42f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureAsPdf,
                                contentDescription = null,
                                tint = accentInk,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${firstPdf.fileName.ifBlank { "PDF" }} (${firstPdf.pageCount}p)",
                                style = MaterialTheme.typography.labelSmall,
                                color = ObsidianTokens.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Live Mini Ink Canvas Preview
                if (previewStrokes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MiniInkPreviewCanvas(
                        strokes = previewStrokes,
                        themeMode = themeMode,
                        accentColor = cardAccent
                    )
                }

                // Checklist Progress Preview
                if (checklistBlocks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianTokens.StatTileSurface,
                        border = BorderStroke(0.75.dp, ObsidianTokens.DividerRule),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$doneTasks / $totalTasks done",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = accentInk,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (totalTasks > 0) {
                                val pct = doneTasks.toFloat() / totalTasks.toFloat()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.10f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(pct.coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .background(ThemeManager.activeAccentBrush)
                                    )
                                }
                            }
                            firstChecklistItems.forEach { item ->
                                val mark = if (item.isChecked) "✓" else "○"
                                Text(
                                    text = "$mark ${item.text}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = if (item.isChecked) ObsidianTokens.TextMuted else ObsidianTokens.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        footerContent = {
            // Tier 2 Footer Bar: Last-modified timestamp, sync & vector storage status without truncation
            val statusSummary = if (mediaSummaryParts.isNotEmpty()) {
                mediaSummaryParts.joinToString(" • ") + " • Local Room DB"
            } else {
                "Local Room storage • Saved"
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormat.format(Date(latestNote.updatedAt)),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = ObsidianTokens.FooterText,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasPdf || hasAudio || hasImage) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasPdf) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureAsPdf,
                                    contentDescription = "Has PDF",
                                    tint = accentInk,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            if (hasAudio) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Has Audio",
                                    tint = accentInk,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            if (hasImage) {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = "Has Image",
                                    tint = accentInk,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
                Text(
                    text = statusSummary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    lineHeight = 13.5.sp,
                    color = ObsidianTokens.TextMuted
                )
            }
        }
    )
}

@Composable
private fun MiniInkPreviewCanvas(
    strokes: List<InkStroke>,
    themeMode: AppThemeMode,
    accentColor: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF07070A))
            .border(0.75.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
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
                rawHex.equals("#2C2C2C", ignoreCase = true) ||
                    rawHex.equals("#22201B", ignoreCase = true) ||
                    rawHex.equals("#000000", ignoreCase = true) -> "#FFFFFF"
                else -> rawHex
            }
            val baseColor = parseColorSafe(
                adaptedHex,
                if (themeMode == AppThemeMode.KINDLE_PARCHMENT) Color(0xFFF8FAFC) else Color.White
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
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Text(if (existingFolder == null) "New Folder" else "Customize Folder")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Presets",
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

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder Name") },
                    placeholder = { Text("e.g., Study, Work, Gaming…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Icon",
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
                            color = if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = if (isChosen) 1.25.dp else 0.75.dp,
                                color = if (isChosen) MaterialTheme.colorScheme.primary else ThemeManager.cardBorderColor
                            )
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 17.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Text(
                    "Accent Color",
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
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected Color",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (existingFolder != null && onDelete != null) {
                    HorizontalDivider(color = ThemeManager.cardBorderColor)
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
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
} catch (_: Exception) {
    fallback
}
