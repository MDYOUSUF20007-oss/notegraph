package com.notegraph.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.ui.common.NoteSecurityManager
import com.notegraph.app.ui.common.VaultPinDialog
import com.notegraph.app.ui.editor.ExportManager
import com.notegraph.app.ui.editor.PdfHelper
import com.notegraph.app.ui.theme.MinimalistBackground
import com.notegraph.app.ui.theme.ThemeManager
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

enum class PageTurnStyle(val label: String) {
    CORNER_FOLD("Corner Fold"),
    SPINE_CURL("3D Curl"),
    PAPER_STACK("Slide Stack")
}

private enum class FoldCorner {
    BOTTOM_RIGHT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    TOP_LEFT
}

private enum class ReaderGestureMode {
    NONE,
    BOOK_TURN,
    THROW_UP
}

private data class CornerFoldGeometry(
    val isActive: Boolean,
    val isCompletelyTurned: Boolean,
    val remainingPagePoly: List<Offset>,
    val exposedCornerPoly: List<Offset>,
    val reflectedFlapPoly: List<Offset>,
    val creaseStart: Offset?,
    val creaseEnd: Offset?,
    val anchor: Offset,
    val tip: Offset
)

/**
 * Tactile Book Reading Mode with selectable page-turn physics:
 * 1. reMarkable / Apple Books geometric Corner Fold (tracks corner & diagonal finger pull)
 * 2. Google Play Books 3D Spine Curl
 * 3. Layered Paper Slide Stack
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    onEditNote: (String) -> Unit = {},
    initialNoteId: String? = null,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var currentPageIndex by remember { mutableIntStateOf(0) }
    var turnStyle by remember { mutableStateOf(PageTurnStyle.CORNER_FOLD) }
    var unlockedInSession by remember { mutableStateOf(setOf<String>()) }
    var pendingPinNote by remember { mutableStateOf<NoteEntity?>(null) }
    var pendingPinOpensEditor by remember { mutableStateOf(false) }

    LaunchedEffect(notes, initialNoteId) {
        if (!initialNoteId.isNullOrBlank() && notes.isNotEmpty()) {
            val idx = notes.indexOfFirst { it.id == initialNoteId }
            if (idx != -1) currentPageIndex = idx
        }
    }

    // Touch-driven animation state (horizontal book turn + upward throw-away toss)
    val curlProgress = remember { Animatable(0f) }
    var dragDirection by remember { mutableIntStateOf(0) } // -1 = next, +1 = prev
    var activeCorner by remember { mutableStateOf(FoldCorner.BOTTOM_RIGHT) }
    var isDraggingFinger by remember { mutableStateOf(false) }

    val throwProgress = remember { Animatable(0f) }
    var isThrowingPage by remember { mutableStateOf(false) }
    var throwDriftX by remember { mutableFloatStateOf(0f) }
    var showEndToastPill by remember { mutableStateOf(false) }

    // Normalized corner tip position (relative to page container size) during free corner drag
    var dragTipNorm by remember { mutableStateOf(Offset(1f, 1f)) }
    var releaseStartTipNorm by remember { mutableStateOf(Offset(1f, 1f)) }
    val releaseBlend = remember { Animatable(0f) }
    var containerSize by remember { mutableStateOf(IntSize(1080, 1600)) }

    val currentNote = notes.getOrNull(currentPageIndex)
    val totalPages = notes.size.coerceAtLeast(1)
    val currentScrollState = rememberScrollState()

    LaunchedEffect(currentPageIndex) {
        currentScrollState.scrollTo(0)
    }

    LaunchedEffect(showEndToastPill) {
        if (showEndToastPill) {
            kotlinx.coroutines.delay(1500L)
            showEndToastPill = false
        }
    }

    fun triggerTurnNext() {
        if (currentPageIndex >= notes.size - 1) return
        if (isThrowingPage || curlProgress.isRunning || throwProgress.isRunning) return
        scope.launch {
            isDraggingFinger = false
            dragDirection = -1
            activeCorner = FoldCorner.BOTTOM_RIGHT
            releaseStartTipNorm = Offset(0.95f, 0.92f)
            releaseBlend.snapTo(0f)
            curlProgress.snapTo(0f)
            launch { releaseBlend.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
            curlProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            currentPageIndex++
            curlProgress.snapTo(0f)
            releaseBlend.snapTo(0f)
            dragDirection = 0
        }
    }

    fun triggerTurnPrev() {
        if (currentPageIndex <= 0) return
        if (isThrowingPage || curlProgress.isRunning || throwProgress.isRunning) return
        scope.launch {
            isDraggingFinger = false
            dragDirection = 1
            activeCorner = FoldCorner.BOTTOM_LEFT
            releaseStartTipNorm = Offset(0.05f, 0.92f)
            releaseBlend.snapTo(0f)
            curlProgress.snapTo(0f)
            launch { releaseBlend.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
            curlProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            currentPageIndex--
            curlProgress.snapTo(0f)
            releaseBlend.snapTo(0f)
            dragDirection = 0
        }
    }

    MinimalistBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Column {
                            Text(
                                "Reader",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (currentNote != null) currentNote.title.ifBlank { "Untitled" } else "No Notes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Reading Mode")
                        }
                    },
                    actions = {
                        AssistChip(
                            onClick = {
                                val styles = PageTurnStyle.values()
                                turnStyle = styles[(styles.indexOf(turnStyle) + 1) % styles.size]
                            },
                            label = { Text(turnStyle.label, fontSize = 11.sp) }
                        )
                        if (currentNote != null) {
                            IconButton(onClick = {
                                if (currentNote.isLocked &&
                                    NoteSecurityManager.hasPin(context) &&
                                    currentNote.id !in unlockedInSession
                                ) {
                                    pendingPinNote = currentNote
                                    pendingPinOpensEditor = true
                                } else {
                                    onEditNote(currentNote.id)
                                }
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit / Markup Note")
                            }
                            IconButton(onClick = { viewModel.toggleBookmark(currentNote) }) {
                                Icon(
                                    if (currentNote.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Dog-ear Bookmark",
                                    tint = if (currentNote.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                    border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { triggerTurnPrev() },
                            enabled = currentPageIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page")
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Page ${currentPageIndex + 1} of $totalPages",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Serif
                            )
                            Text(
                                text = "Turn edge/corner • Swipe up to toss page",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { triggerTurnNext() },
                            enabled = currentPageIndex < notes.size - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page")
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No notes found in vault to read.",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else if (currentNote != null) {
                val curPageIdxState by rememberUpdatedState(currentPageIndex)
                val notesSizeState by rememberUpdatedState(notes.size)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSize = it }
                        .pointerInput(turnStyle) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                if (isThrowingPage || throwProgress.isRunning || curlProgress.isRunning) {
                                    return@awaitEachGesture
                                }
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val downPos = down.position

                                val inRightCorner = downPos.x > w * 0.72f &&
                                    (downPos.y > h * 0.65f || downPos.y < h * 0.35f)
                                val inLeftCorner = downPos.x < w * 0.28f &&
                                    (downPos.y > h * 0.65f || downPos.y < h * 0.35f)
                                val inBottomThrowZone = downPos.y > h * 0.70f

                                var gestureMode = ReaderGestureMode.NONE
                                var activeDir = 0
                                var lastY = downPos.y
                                var lastFrameDy = 0f
                                var lastTotalDx = 0f
                                var lastTotalDy = 0f

                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val active = event.changes.filter { it.pressed }

                                    if (active.isEmpty()) {
                                        if (gestureMode == ReaderGestureMode.BOOK_TURN && activeDir != 0) {
                                            val curTip = dragTipNorm
                                            val curProg = curlProgress.value
                                            isDraggingFinger = false
                                            releaseStartTipNorm = curTip
                                            val capturedDir = activeDir

                                            scope.launch {
                                                val shouldTurn = curProg > 0.28f &&
                                                    ((capturedDir == -1 && curPageIdxState < notesSizeState - 1) ||
                                                        (capturedDir == 1 && curPageIdxState > 0))
                                                if (shouldTurn) {
                                                    releaseBlend.snapTo(0f)
                                                    launch {
                                                        releaseBlend.animateTo(
                                                            1f,
                                                            tween(270, easing = FastOutSlowInEasing)
                                                        )
                                                    }
                                                    curlProgress.animateTo(
                                                        1f,
                                                        tween(270, easing = FastOutSlowInEasing)
                                                    )
                                                    if (capturedDir == -1 && currentPageIndex < notes.size - 1) {
                                                        currentPageIndex++
                                                    } else if (capturedDir == 1 && currentPageIndex > 0) {
                                                        currentPageIndex--
                                                    }
                                                    curlProgress.snapTo(0f)
                                                    releaseBlend.snapTo(0f)
                                                } else {
                                                    releaseBlend.snapTo(0f)
                                                    curlProgress.animateTo(0f, tween(210, easing = FastOutSlowInEasing))
                                                }
                                                dragDirection = 0
                                            }
                                        } else if (gestureMode == ReaderGestureMode.THROW_UP) {
                                            val curThrow = throwProgress.value
                                            val velocityThrow = lastFrameDy < -14f && lastTotalDy < -36f
                                            val canAdvance = curPageIdxState < notesSizeState - 1
                                            val capturedDrift = lastTotalDx * 0.65f

                                            scope.launch {
                                                if (canAdvance && (curThrow > 0.22f || velocityThrow)) {
                                                    throwDriftX = capturedDrift
                                                    throwProgress.animateTo(
                                                        1f,
                                                        tween(260, easing = CubicBezierEasing(0.18f, 0f, 0.22f, 1f))
                                                    )
                                                    if (currentPageIndex < notes.size - 1) {
                                                        currentPageIndex++
                                                    }
                                                    throwProgress.snapTo(0f)
                                                    throwDriftX = 0f
                                                    isThrowingPage = false
                                                } else {
                                                    if (!canAdvance && curThrow > 0.08f) {
                                                        showEndToastPill = true
                                                    }
                                                    throwProgress.animateTo(
                                                        0f,
                                                        spring(dampingRatio = 0.72f, stiffness = 420f)
                                                    )
                                                    throwDriftX = 0f
                                                    isThrowingPage = false
                                                }
                                            }
                                        }
                                        break
                                    }

                                    val pointer = active.first()
                                    lastFrameDy = pointer.position.y - lastY
                                    lastY = pointer.position.y
                                    val totalDx = pointer.position.x - downPos.x
                                    val totalDy = pointer.position.y - downPos.y
                                    lastTotalDx = totalDx
                                    lastTotalDy = totalDy

                                    if (gestureMode == ReaderGestureMode.NONE) {
                                        val movedDist = sqrt(totalDx * totalDx + totalDy * totalDy)
                                        if (movedDist < 10f) continue

                                        val isPredominantlyUpward = totalDy < -18f && abs(totalDy) > abs(totalDx) * 1.25f
                                        val canThrowFromPosition = !currentScrollState.canScrollForward || inBottomThrowZone
                                        val isHorizontalSwipe = abs(totalDx) > 14f && abs(totalDx) > abs(totalDy) * 1.15f
                                        val isCornerDrag = (inRightCorner || inLeftCorner) &&
                                            movedDist > 8f &&
                                            abs(totalDx) >= abs(totalDy) * 0.65f

                                        if (isPredominantlyUpward && canThrowFromPosition) {
                                            gestureMode = ReaderGestureMode.THROW_UP
                                            isThrowingPage = true
                                        } else if (isHorizontalSwipe || isCornerDrag) {
                                            val desiredDir = when {
                                                inRightCorner && totalDx <= -2f -> -1
                                                inLeftCorner && totalDx >= 2f -> 1
                                                totalDx < 0 -> -1
                                                totalDx > 0 -> 1
                                                else -> 0
                                            }

                                            if (desiredDir == -1 && curPageIdxState >= notesSizeState - 1) break
                                            if (desiredDir == 1 && curPageIdxState <= 0) break
                                            if (desiredDir == 0) continue

                                            activeDir = desiredDir
                                            dragDirection = desiredDir
                                            activeCorner = when (desiredDir) {
                                                -1 -> if (downPos.y < h * 0.40f) FoldCorner.TOP_RIGHT else FoldCorner.BOTTOM_RIGHT
                                                else -> if (downPos.y < h * 0.40f) FoldCorner.TOP_LEFT else FoldCorner.BOTTOM_LEFT
                                            }
                                            gestureMode = ReaderGestureMode.BOOK_TURN
                                            isDraggingFinger = true
                                        } else if (abs(totalDy) > 22f) {
                                            // Yield vertical drag to inner page scrollState
                                            break
                                        } else {
                                            continue
                                        }
                                    }

                                    pointer.consume()

                                    if (gestureMode == ReaderGestureMode.THROW_UP) {
                                        throwDriftX = totalDx * 0.65f
                                        val rawLift = if (curPageIdxState < notesSizeState - 1) {
                                            (-totalDy / (h * 0.46f)).coerceIn(0f, 1.15f)
                                        } else {
                                            (-totalDy / (h * 1.45f)).coerceIn(0f, 0.24f)
                                        }
                                        scope.launch { throwProgress.snapTo(rawLift) }
                                        continue
                                    }

                                    // Compute responsive corner tip position from drag vector
                                    val anchorX = when (activeCorner) {
                                        FoldCorner.BOTTOM_RIGHT, FoldCorner.TOP_RIGHT -> 1f
                                        FoldCorner.BOTTOM_LEFT, FoldCorner.TOP_LEFT -> 0f
                                    }
                                    val anchorY = when (activeCorner) {
                                        FoldCorner.BOTTOM_RIGHT, FoldCorner.BOTTOM_LEFT -> 1f
                                        FoldCorner.TOP_RIGHT, FoldCorner.TOP_LEFT -> 0f
                                    }
                                    val dxNorm = totalDx / w
                                    val dyNorm = totalDy / h

                                    val rawTipX: Float
                                    val rawTipY: Float
                                    val progressEstimate: Float

                                    if (activeDir == -1) {
                                        val peelLeft = (-dxNorm).coerceIn(0f, 1.85f)
                                        rawTipX = (anchorX - peelLeft * 1.12f).coerceIn(-0.95f, 0.99f)
                                        val naturalLift = peelLeft * 0.20f
                                        rawTipY = if (anchorY == 1f) {
                                            (1f + dyNorm * 1.1f - naturalLift).coerceIn(-0.15f, 0.98f)
                                        } else {
                                            (0f + dyNorm * 1.1f + naturalLift).coerceIn(0.02f, 1.15f)
                                        }
                                        progressEstimate = (peelLeft * 1.15f).coerceIn(0f, 1f)
                                    } else {
                                        val peelRight = dxNorm.coerceIn(0f, 1.85f)
                                        rawTipX = (anchorX + peelRight * 1.12f).coerceIn(0.01f, 1.95f)
                                        val naturalLift = peelRight * 0.20f
                                        rawTipY = if (anchorY == 1f) {
                                            (1f + dyNorm * 1.1f - naturalLift).coerceIn(-0.15f, 0.98f)
                                        } else {
                                            (0f + dyNorm * 1.1f + naturalLift).coerceIn(0.02f, 1.15f)
                                        }
                                        progressEstimate = (peelRight * 1.15f).coerceIn(0f, 1f)
                                    }

                                    dragTipNorm = Offset(rawTipX, rawTipY)
                                    scope.launch { curlProgress.snapTo(progressEstimate) }
                                }
                            }
                        }
                ) {
                    val p = curlProgress.value
                    val tp = throwProgress.value
                    val isThrowVisualActive = isThrowingPage || tp > 0.002f
                    val nextNote = when {
                        isThrowVisualActive -> notes.getOrNull(currentPageIndex + 1)
                        dragDirection == -1 -> notes.getOrNull(currentPageIndex + 1)
                        dragDirection == 1 -> notes.getOrNull(currentPageIndex - 1)
                        else -> null
                    }

                    val wPx = containerSize.width.toFloat().coerceAtLeast(1f)
                    val hPx = containerSize.height.toFloat().coerceAtLeast(1f)

                    // Calculate corner fold geometry when CORNER_FOLD is selected and curl is active
                    val foldGeom: CornerFoldGeometry? = if (!isThrowVisualActive && turnStyle == PageTurnStyle.CORNER_FOLD && p > 0.002f && dragDirection != 0) {
                        val anchor = cornerOffset(activeCorner, wPx, hPx)
                        val finishTarget = when (activeCorner) {
                            FoldCorner.BOTTOM_RIGHT -> Offset(-0.98f * wPx, 0.96f * hPx)
                            FoldCorner.TOP_RIGHT -> Offset(-0.98f * wPx, 0.04f * hPx)
                            FoldCorner.BOTTOM_LEFT -> Offset(1.98f * wPx, 0.96f * hPx)
                            FoldCorner.TOP_LEFT -> Offset(1.98f * wPx, 0.04f * hPx)
                        }
                        val tip = if (isDraggingFinger) {
                            Offset(dragTipNorm.x * wPx, dragTipNorm.y * hPx)
                        } else if (releaseBlend.value > 0f) {
                            val t = releaseBlend.value
                            val startPx = Offset(releaseStartTipNorm.x * wPx, releaseStartTipNorm.y * hPx)
                            Offset(
                                startPx.x + (finishTarget.x - startPx.x) * t,
                                startPx.y + (finishTarget.y - startPx.y) * t
                            )
                        } else {
                            // Springing back flat from release point
                            val ratio = (p / 0.35f).coerceIn(0f, 1f)
                            val relPx = Offset(releaseStartTipNorm.x * wPx, releaseStartTipNorm.y * hPx)
                            Offset(
                                anchor.x + (relPx.x - anchor.x) * ratio,
                                anchor.y + (relPx.y - anchor.y) * ratio
                            )
                        }
                        computeCornerFoldGeometry(wPx, hPx, anchor, tip)
                    } else null

                    // 1. Underneath Next/Prev Page (revealed beneath the lifting corner/curl or tossed page)
                    if (nextNote != null && (p > 0.002f || isThrowVisualActive)) {
                        val underneathModifier = if (isThrowVisualActive) {
                            val normTp = tp.coerceIn(0f, 1f)
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val underScale = 0.94f + 0.06f * normTp
                                    scaleX = underScale
                                    scaleY = underScale
                                    translationY = 22f * (1f - normTp)
                                    alpha = 0.78f + 0.22f * normTp
                                }
                        } else {
                            Modifier.fillMaxSize()
                        }
                        BookPageSurface(
                            note = nextNote,
                            pageNumber = if (isThrowVisualActive || dragDirection == -1) currentPageIndex + 2 else currentPageIndex,
                            isBookmarked = nextNote.isFavorite,
                            isSessionUnlocked = nextNote.id in unlockedInSession,
                            onUnlockClick = {
                                if (NoteSecurityManager.hasPin(context)) {
                                    pendingPinNote = nextNote
                                    pendingPinOpensEditor = false
                                } else {
                                    unlockedInSession = unlockedInSession + nextNote.id
                                }
                            },
                            onDogEarClick = { viewModel.toggleBookmark(nextNote) },
                            onToggleChecklist = { blkId, itemId -> viewModel.toggleChecklistItem(nextNote, blkId, itemId) },
                            showCornerHint = false,
                            modifier = underneathModifier
                        )
                    }

                    // 2. Foreground Current Page (book fold/curl OR upward throw-away physics!)
                    val fgModifier = when {
                        isThrowVisualActive -> {
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val tiltSign = when {
                                        throwDriftX > 16f -> 1f
                                        throwDriftX < -16f -> -1f
                                        else -> -1f
                                    }
                                    translationY = -hPx * 1.32f * tp
                                    translationX = throwDriftX * (1f + tp * 0.72f) + (tiltSign * wPx * 0.06f * tp)
                                    rotationZ = (tiltSign * 14f * tp) + (throwDriftX / wPx) * 16f
                                    rotationX = (24f * tp).coerceAtMost(32f)
                                    scaleX = (1f - 0.16f * tp).coerceAtLeast(0.60f)
                                    scaleY = (1f - 0.12f * tp).coerceAtLeast(0.65f)
                                    alpha = (1f - ((tp - 0.58f).coerceAtLeast(0f) / 0.42f)).coerceIn(0f, 1f)
                                    cameraDistance = 14f * density
                                }
                        }
                        turnStyle == PageTurnStyle.CORNER_FOLD && foldGeom != null && foldGeom.isActive -> {
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    clip = true
                                    shape = GenericShape { _, _ ->
                                        val poly = foldGeom.remainingPagePoly
                                        if (poly.size >= 3) {
                                            moveTo(poly[0].x, poly[0].y)
                                            for (i in 1 until poly.size) {
                                                lineTo(poly[i].x, poly[i].y)
                                            }
                                            close()
                                        } else {
                                            // Page has swept 100% across
                                            moveTo(0f, 0f)
                                            lineTo(0.01f, 0f)
                                            close()
                                        }
                                    }
                                }
                        }
                        turnStyle == PageTurnStyle.SPINE_CURL && p > 0.005f -> {
                            val foldX = if (dragDirection == -1) wPx * (1f - p) else wPx * p
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    clip = true
                                    shape = GenericShape { size, _ ->
                                        if (dragDirection == -1) {
                                            moveTo(0f, 0f)
                                            lineTo(foldX.coerceAtLeast(0.01f), 0f)
                                            lineTo(foldX.coerceAtLeast(0.01f), size.height)
                                            lineTo(0f, size.height)
                                            close()
                                        } else {
                                            moveTo(foldX.coerceAtMost(size.width - 0.01f), 0f)
                                            lineTo(size.width, 0f)
                                            lineTo(size.width, size.height)
                                            lineTo(foldX.coerceAtMost(size.width - 0.01f), size.height)
                                            close()
                                        }
                                    }
                                }
                        }
                        turnStyle == PageTurnStyle.PAPER_STACK && p > 0.005f -> {
                            val slideX = if (dragDirection == -1) -wPx * p else wPx * p
                            val stackScale = 1f - (p * 0.06f)
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = slideX
                                    scaleX = stackScale
                                    scaleY = stackScale
                                }
                        }
                        else -> Modifier.fillMaxSize()
                    }

                    BookPageSurface(
                        note = currentNote,
                        pageNumber = currentPageIndex + 1,
                        isBookmarked = currentNote.isFavorite,
                        isSessionUnlocked = currentNote.id in unlockedInSession,
                        scrollState = currentScrollState,
                        onUnlockClick = {
                            if (NoteSecurityManager.hasPin(context)) {
                                pendingPinNote = currentNote
                                pendingPinOpensEditor = false
                            } else {
                                unlockedInSession = unlockedInSession + currentNote.id
                            }
                        },
                        onDogEarClick = { viewModel.toggleBookmark(currentNote) },
                        onToggleChecklist = { blkId, itemId -> viewModel.toggleChecklistItem(currentNote, blkId, itemId) },
                        showCornerHint = (currentPageIndex < notes.size - 1) && !isThrowVisualActive && p <= 0.002f && turnStyle == PageTurnStyle.CORNER_FOLD,
                        modifier = fgModifier
                    )

                    // 3. Tactile Shadow & Reflected Backside Flap Overlay
                    if (!isThrowVisualActive && turnStyle == PageTurnStyle.CORNER_FOLD && foldGeom != null && foldGeom.isActive) {
                        ReMarkableCornerFoldOverlay(
                            geometry = foldGeom,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (!isThrowVisualActive && turnStyle == PageTurnStyle.SPINE_CURL && p > 0.005f) {
                        SpineCurlOverlay(
                            progress = p,
                            isNext = dragDirection == -1,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Transient pill when user tries to toss the final page of the notebook
                    AnimatedVisibility(
                        visible = showEndToastPill,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 18.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                            border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                        ) {
                            Text(
                                text = if (notes.size <= 1) "Only 1 page in notebook" else "Last page reached",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
        }
    }

    pendingPinNote?.let { target ->
        VaultPinDialog(
            isSettingNewPin = false,
            title = "Unlock Note",
            subtitle = "\"${target.title.ifBlank { "Untitled" }}\" is protected by your Vault PIN.",
            onDismiss = { pendingPinNote = null },
            onVerify = { attempt -> NoteSecurityManager.verifyPin(context, attempt) },
            onSuccess = {
                unlockedInSession = unlockedInSession + target.id
                val openEd = pendingPinOpensEditor
                pendingPinNote = null
                if (openEd) {
                    onEditNote(target.id)
                }
            }
        )
    }
}

private fun cornerOffset(corner: FoldCorner, w: Float, h: Float): Offset = when (corner) {
    FoldCorner.BOTTOM_RIGHT -> Offset(w, h)
    FoldCorner.TOP_RIGHT -> Offset(w, 0f)
    FoldCorner.BOTTOM_LEFT -> Offset(0f, h)
    FoldCorner.TOP_LEFT -> Offset(0f, 0f)
}

/**
 * Exact 2D paper-fold half-plane polygon solver (Sutherland-Hodgman clip + reflection across fold crease).
 */
private fun computeCornerFoldGeometry(
    w: Float,
    h: Float,
    anchor: Offset,
    tip: Offset
): CornerFoldGeometry {
    val dx = tip.x - anchor.x
    val dy = tip.y - anchor.y
    val distSq = dx * dx + dy * dy
    if (distSq < 9f) {
        return CornerFoldGeometry(false, false, emptyList(), emptyList(), emptyList(), null, null, anchor, tip)
    }

    val mid = Offset((anchor.x + tip.x) / 2f, (anchor.y + tip.y) / 2f)
    val normal = Offset(dx, dy)
    val rect = listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w, h), Offset(0f, h))

    fun signedDist(p: Offset): Float = (p.x - mid.x) * normal.x + (p.y - mid.y) * normal.y

    val remaining = clipConvexPolygon(rect, ::signedDist, keepPositiveSide = true)
    val exposed = clipConvexPolygon(rect, ::signedDist, keepPositiveSide = false)

    val reflectedFlap = exposed.map { v ->
        val t = signedDist(v) / distSq
        Offset(v.x - 2f * t * normal.x, v.y - 2f * t * normal.y)
    }

    val scaleTol = sqrt(distSq) * 0.02f
    val creasePts = remaining.filter { abs(signedDist(it)) <= scaleTol }
    val c1 = creasePts.firstOrNull()
    val c2 = if (creasePts.size >= 2) creasePts.last() else null

    return CornerFoldGeometry(
        isActive = true,
        isCompletelyTurned = remaining.size < 3,
        remainingPagePoly = remaining,
        exposedCornerPoly = exposed,
        reflectedFlapPoly = reflectedFlap,
        creaseStart = c1,
        creaseEnd = c2,
        anchor = anchor,
        tip = tip
    )
}

private fun clipConvexPolygon(
    poly: List<Offset>,
    eval: (Offset) -> Float,
    keepPositiveSide: Boolean
): List<Offset> {
    if (poly.isEmpty()) return emptyList()
    val out = mutableListOf<Offset>()
    val sign = if (keepPositiveSide) 1f else -1f

    for (i in poly.indices) {
        val curr = poly[i]
        val prev = poly[(i + poly.size - 1) % poly.size]
        val currVal = eval(curr) * sign
        val prevVal = eval(prev) * sign

        val currIn = currVal >= -0.001f
        val prevIn = prevVal >= -0.001f

        if (currIn != prevIn) {
            val denom = prevVal - currVal
            val t = if (abs(denom) > 1e-5f) (prevVal / denom).coerceIn(0f, 1f) else 0.5f
            out.add(
                Offset(
                    prev.x + (curr.x - prev.x) * t,
                    prev.y + (curr.y - prev.y) * t
                )
            )
        }
        if (currIn) {
            out.add(curr)
        }
    }
    return out
}

@Composable
private fun ReMarkableCornerFoldOverlay(
    geometry: CornerFoldGeometry,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val c1 = geometry.creaseStart ?: return@Canvas
        val c2 = geometry.creaseEnd ?: return@Canvas

        val dx = geometry.tip.x - geometry.anchor.x
        val dy = geometry.tip.y - geometry.anchor.y
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(4f)
        val uNorm = Offset(dx / dist, dy / dist)
        val creaseMid = Offset((c1.x + c2.x) / 2f, (c1.y + c2.y) / 2f)

        // 1. Cast shadow onto the REVEALED next page inside exposedCornerPoly
        if (geometry.exposedCornerPoly.size >= 3) {
            val exposedPath = pointsToPath(geometry.exposedCornerPoly)
            val shadowDepth = (dist * 0.30f).coerceIn(24f, 130f)
            val shadowEnd = Offset(creaseMid.x - uNorm.x * shadowDepth, creaseMid.y - uNorm.y * shadowDepth)
            clipPath(exposedPath) {
                drawPath(
                    path = exposedPath,
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0x58000000),
                            0.45f to Color(0x22000000),
                            1.0f to Color.Transparent
                        ),
                        start = creaseMid,
                        end = shadowEnd
                    )
                )
            }
        }

        // 2. Lifted Paper Flap Drop Shadow onto remaining foreground page + Reflected Flap Mesh
        if (geometry.reflectedFlapPoly.size >= 3) {
            val flapPath = pointsToPath(geometry.reflectedFlapPoly)

            if (geometry.remainingPagePoly.size >= 3) {
                val remainPath = pointsToPath(geometry.remainingPagePoly)
                clipPath(remainPath) {
                    // Ambient drop shadow under raised corner flap
                    drawPath(
                        path = flapPath,
                        color = Color(0x26000000),
                        style = Stroke(width = 22f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            // Reflected Back-of-Page Surface with cylindrical specular paper lighting
            val flapSpan = (dist * 0.50f).coerceAtLeast(18f)
            val gradEnd = Offset(creaseMid.x + uNorm.x * flapSpan, creaseMid.y + uNorm.y * flapSpan)

            clipPath(flapPath) {
                drawPath(
                    path = flapPath,
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xFFD6CEC2),
                            0.18f to Color(0xFFFDFBF7), // Crisp specular fold sheen
                            0.62f to Color(0xFFEBE5DA), // Warm parchment paper back
                            1.00f to Color(0xFFDDD5C8)  // Soft corner edge shading
                        ),
                        start = creaseMid,
                        end = gradEnd
                    )
                )

                // Subtle ghost reverse-page ink bleed lines across the folded paper back
                val bounds = flapPath.getBounds()
                var gy = bounds.top + 28f
                while (gy < bounds.bottom - 18f) {
                    drawLine(
                        color = Color(0x102C2C2C),
                        start = Offset(bounds.left + 16f, gy),
                        end = Offset(bounds.right - 22f, gy),
                        strokeWidth = 3f
                    )
                    gy += 26f
                }
            }

            // Crisp tactile paper thickness outline around folded corner
            drawPath(
                path = flapPath,
                color = Color(0x36000000),
                style = Stroke(width = 1.6f, join = StrokeJoin.Round)
            )
        }

        // 3. Highlighted fold crease line
        drawLine(
            color = Color(0x38000000),
            start = c1,
            end = c2,
            strokeWidth = 2f
        )
    }
}

private fun pointsToPath(points: List<Offset>): Path = Path().apply {
    if (points.isNotEmpty()) {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
        close()
    }
}

/**
 * Renders a tactile book page with authentic parchment texture, spine crease shading,
 * ink previews, PDF summaries, interactive checklists, and corner dog-ear bookmark.
 */
@Composable
private fun BookPageSurface(
    note: NoteEntity,
    pageNumber: Int,
    isBookmarked: Boolean,
    isSessionUnlocked: Boolean = false,
    scrollState: ScrollState = rememberScrollState(),
    onUnlockClick: () -> Unit = {},
    onDogEarClick: () -> Unit,
    onToggleChecklist: (blockId: String, itemId: String) -> Unit,
    showCornerHint: Boolean,
    modifier: Modifier = Modifier
) {
    val blocks = remember(note) { ExportManager.parseBlocks(note) }
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp),
        color = Color(0xFFF9F6F0),
        shadowElevation = 8.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Book Spine Shading along the left edge
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(18.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0x33000000),
                                Color(0x1A000000),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Page Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = dateFormat.format(Date(note.updatedAt)).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8B4513),
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 1.2.sp
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Serif,
                    color = Color(0xFF2C2C2C)
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFDCD6CD), thickness = 1.dp)
                Spacer(Modifier.height(14.dp))

                if (note.isLocked && !isSessionUnlocked) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFECE7DE),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFF2C2C2C), modifier = Modifier.size(16.dp))
                                Text("Locked Note", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF2C2C2C))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Unlock with your Vault PIN to read or edit this page.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B655D)
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = onUnlockClick) {
                                Text("Unlock Page")
                            }
                        }
                    }
                } else {
                    if (note.content.isNotBlank()) {
                        Text(
                            text = note.content,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 28.sp,
                                fontSize = 17.sp,
                                fontFamily = FontFamily.Serif,
                                color = Color(0xFF2C2C2C)
                            )
                        )
                        Spacer(Modifier.height(14.dp))
                    }

                    for (block in blocks) {
                        when (block) {
                            is NoteBlock.TextBlock -> {
                                if (block.text.isNotBlank()) {
                                    Text(
                                        text = block.text,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            lineHeight = 26.sp,
                                            fontFamily = FontFamily.Serif,
                                            color = Color(0xFF2C2C2C)
                                        )
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                            is NoteBlock.InkBlock -> {
                                val strokes = remember(block.strokesJson) {
                                    try {
                                        json.decodeFromString<List<InkStroke>>(block.strokesJson)
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFECE7DE),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Rounded.Brush, contentDescription = null, tint = Color(0xFF2C2C2C), modifier = Modifier.size(15.dp))
                                            Text(
                                                "Handwritten Sketch (${strokes.size} strokes)",
                                                fontFamily = FontFamily.Serif,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color(0xFF2C2C2C)
                                            )
                                        }
                                        if (strokes.isNotEmpty()) {
                                            Spacer(Modifier.height(6.dp))
                                            Canvas(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(110.dp)
                                                    .background(Color(0xFFF9F6F0), RoundedCornerShape(6.dp))
                                            ) {
                                                val allPts = strokes.flatMap { it.points }
                                                if (allPts.isNotEmpty()) {
                                                    val minX = allPts.minOf { it.x }
                                                    val maxX = allPts.maxOf { it.x }
                                                    val minY = allPts.minOf { it.y }
                                                    val maxY = allPts.maxOf { it.y }
                                                    val rawW = (maxX - minX).coerceAtLeast(10f)
                                                    val rawH = (maxY - minY).coerceAtLeast(10f)
                                                    val pad = 12f
                                                    val scale = minOf((size.width - pad * 2) / rawW, (size.height - pad * 2) / rawH)
                                                    val ox = (size.width - rawW * scale) / 2f
                                                    val oy = (size.height - rawH * scale) / 2f
                                                    for (s in strokes) {
                                                        if (s.points.size < 2) continue
                                                        val p = Path().apply {
                                                            moveTo(ox + (s.points[0].x - minX) * scale, oy + (s.points[0].y - minY) * scale)
                                                            for (i in 1 until s.points.size) {
                                                                lineTo(ox + (s.points[i].x - minX) * scale, oy + (s.points[i].y - minY) * scale)
                                                            }
                                                        }
                                                        val hex = if (s.colorHex.equals("#FFFFFF", true)) "#2C2C2C" else s.colorHex
                                                        val col = try {
                                                            Color(android.graphics.Color.parseColor(hex))
                                                        } catch (_: Exception) {
                                                            Color(0xFF2C2C2C)
                                                        }
                                                        drawPath(
                                                            p,
                                                            color = col.copy(alpha = if (s.isHighlighter) 0.4f else 0.95f),
                                                            style = Stroke(width = (s.strokeWidth * scale).coerceIn(1.5f, 5f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            is NoteBlock.AudioBlock -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFECE7DE),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Mic, contentDescription = null, tint = Color(0xFF2C2C2C), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text("Voice Recording (${block.durationMs / 1000}s)", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleSmall, color = Color(0xFF2C2C2C))
                                            Text("Audio block preserved in note", style = MaterialTheme.typography.bodySmall, color = Color(0xFF757575))
                                        }
                                    }
                                }
                            }
                            is NoteBlock.ImageBlock -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFECE7DE),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Image, contentDescription = null, tint = Color(0xFF2C2C2C), modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Text("Embedded Image Attachment", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleSmall, color = Color(0xFF2C2C2C))
                                        }
                                        if (!block.extractedOcrText.isNullOrBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                "“${block.extractedOcrText.trim()}”",
                                                fontStyle = FontStyle.Italic,
                                                fontFamily = FontFamily.Serif,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF4A4A4A)
                                            )
                                        }
                                    }
                                }
                            }
                            is NoteBlock.ChecklistBlock -> {
                                val checkedCount = block.items.count { it.isChecked }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFECE7DE),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = block.title.ifBlank { "To-Do Checklist" },
                                                fontFamily = FontFamily.Serif,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color(0xFF2C2C2C)
                                            )
                                            Text(
                                                text = "$checkedCount/${block.items.size}",
                                                fontFamily = FontFamily.Serif,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF8B4513)
                                            )
                                        }
                                        if (block.items.isNotEmpty()) {
                                            Spacer(Modifier.height(6.dp))
                                            block.items.forEach { item ->
                                                val prefix = if (item.isChecked) "☑" else "☐"
                                                Text(
                                                    text = "$prefix ${item.text}",
                                                    fontFamily = FontFamily.Serif,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                                    ),
                                                    color = if (item.isChecked) Color(0xFF757575) else Color(0xFF2C2C2C),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { onToggleChecklist(block.id, item.id) }
                                                        .padding(vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            is NoteBlock.PdfBlock -> {
                                val anns = remember(block.annotationsJson) { PdfHelper.parseAnnotations(block.annotationsJson) }
                                val markedPages = anns.count { it.strokes.isNotEmpty() || it.pageNote.isNotBlank() }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFECE7DE),
                                    border = BorderStroke(1.dp, Color(0xFFD2C8B8)),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Color(0xFF2C2C2C), modifier = Modifier.size(16.dp))
                                            Text(
                                                text = block.fileName.ifBlank { "PDF Document" },
                                                fontFamily = FontFamily.Serif,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color(0xFF2C2C2C)
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "${block.pageCount} pages • $markedPages annotated • Tap Edit above to markup",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF8B4513)
                                        )
                                        anns.filter { it.pageNote.isNotBlank() }.take(3).forEach { ann ->
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "• Page ${ann.pageIndex + 1}: ${ann.pageNote}",
                                                fontFamily = FontFamily.Serif,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF4A4A4A)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "— $pageNumber —",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Serif,
                        color = Color(0xFF8B4513)
                    )
                }
            }

            // Interactive Corner Dog-Ear Bookmark (Top-Right Corner)
            DogEarBookmark(
                isFolded = isBookmarked,
                onClick = onDogEarClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(56.dp)
            )

            // Subtle reMarkable Bottom-Right Corner Lift Cue when idle
            if (showCornerHint) {
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(44.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val fold = 22f
                    drawPath(
                        path = Path().apply {
                            moveTo(w - fold, h)
                            lineTo(w, h - fold)
                            lineTo(w - fold, h - fold)
                            close()
                        },
                        color = Color(0xFFE7E0D4)
                    )
                    drawPath(
                        path = Path().apply {
                            moveTo(w - fold, h)
                            lineTo(w, h - fold)
                            lineTo(w, h)
                            close()
                        },
                        color = Color(0x28000000)
                    )
                }
            }
        }
    }
}

@Composable
private fun DogEarBookmark(
    isFolded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        val w = size.width

        if (isFolded) {
            val foldSize = 44f
            val shadowPath = Path().apply {
                moveTo(w - foldSize - 4f, 0f)
                lineTo(w, foldSize + 4f)
                lineTo(w - foldSize - 4f, foldSize + 4f)
                close()
            }
            drawPath(shadowPath, color = Color(0x44000000))

            val foldPath = Path().apply {
                moveTo(w - foldSize, 0f)
                lineTo(w, foldSize)
                lineTo(w - foldSize, foldSize)
                close()
            }
            drawPath(
                path = foldPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFBBF24), Color(0xFFD97706)),
                    start = Offset(w - foldSize, 0f),
                    end = Offset(w, foldSize)
                )
            )

            drawLine(
                color = Color(0x66FFFFFF),
                start = Offset(w - foldSize, 0f),
                end = Offset(w, foldSize),
                strokeWidth = 1.5f
            )
        } else {
            val foldSize = 24f
            val outlinePath = Path().apply {
                moveTo(w - foldSize, 0f)
                lineTo(w, foldSize)
            }
            drawPath(
                path = outlinePath,
                color = Color(0x448B4513),
                style = Stroke(
                    width = 1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )
            )
        }
    }
}

@Composable
private fun SpineCurlOverlay(
    progress: Float,
    isNext: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val curlAmount = progress.coerceIn(0f, 1f)
        if (curlAmount <= 0.001f) return@Canvas

        val foldX = if (isNext) w * (1f - curlAmount) else w * curlAmount
        val shadowWidth = 60f + (curlAmount * 40f)

        val shadowRect = if (isNext) {
            Rect(foldX, 0f, foldX + shadowWidth, h)
        } else {
            Rect(foldX - shadowWidth, 0f, foldX, h)
        }

        drawRect(
            brush = Brush.horizontalGradient(
                colors = if (isNext) {
                    listOf(Color(0x66000000), Color.Transparent)
                } else {
                    listOf(Color.Transparent, Color(0x66000000))
                },
                startX = shadowRect.left,
                endX = shadowRect.right
            ),
            topLeft = Offset(shadowRect.left, 0f),
            size = Size(shadowRect.width, h)
        )

        val curlFlapWidth = (curlAmount * 120f).coerceAtMost(w * 0.45f).coerceAtLeast(4f)
        val flapLeft = if (isNext) (foldX - curlFlapWidth) else foldX
        val flapRight = if (isNext) foldX else (foldX + curlFlapWidth)

        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFE2DDD5),
                    Color(0xFFFDFBF7),
                    Color(0xFFB5ADA0)
                ),
                startX = flapLeft,
                endX = flapRight
            ),
            topLeft = Offset(flapLeft, 0f),
            size = Size(curlFlapWidth, h)
        )

        drawLine(
            color = Color(0x44000000),
            start = Offset(foldX, 0f),
            end = Offset(foldX, h),
            strokeWidth = 2f
        )
    }
}
