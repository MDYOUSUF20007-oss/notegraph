package com.notegraph.app.ui.tutorial

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notegraph.app.ui.components.ObsidianCard
import com.notegraph.app.ui.components.ObsidianIconTile
import com.notegraph.app.ui.components.ObsidianMetallicBadge
import com.notegraph.app.ui.components.ObsidianStatTile
import com.notegraph.app.ui.components.ObsidianTokens
import com.notegraph.app.ui.graph.GraphBlueprintSpec
import com.notegraph.app.ui.theme.MinimalistBackground
import com.notegraph.app.ui.theme.ThemeManager

enum class TutorialSection(val label: String) {
    ALL("All"),
    GRAPH("Graph Nodes"),
    SKETCH("Sketch & Lasso"),
    PDF("PDF & OCR"),
    NOTES("Notes & Tags"),
    VAULT("Reader & Vault");

    companion object {
        fun fromArg(raw: String?): TutorialSection {
            if (raw.isNullOrBlank()) return ALL
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: ALL
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(
    initialSection: String? = null,
    onBack: () -> Unit,
    onOpenGraph: () -> Unit = {},
    viewModel: TutorialViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val noteCount by viewModel.noteCount.collectAsState()
    var selectedSection by remember(initialSection) {
        mutableStateOf(TutorialSection.fromArg(initialSection))
    }
    var isCreatingDemo by remember { mutableStateOf(false) }

    val activePreset = ThemeManager.currentGradient
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens
    val obsidianAccent = if (isLuxury) lux.accent else ObsidianTokens.resolveAccent(activePreset.accentStart)
    val accentInk = ObsidianTokens.readableAccentInk(obsidianAccent)

    fun showSection(section: TutorialSection): Boolean {
        return selectedSection == TutorialSection.ALL || selectedSection == section
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
                                text = "NoteGraph Guide",
                                style = if (isLuxury) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                                fontFamily = if (isLuxury) FontFamily.Serif else FontFamily.Default,
                                color = if (isLuxury) lux.textPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isLuxury) {
                                    "OFFLINE VAULT // $noteCount NOTE${if (noteCount == 1) "" else "S"}"
                                } else {
                                    "Offline Vault • $noteCount note${if (noteCount == 1) "" else "s"} stored"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                                fontSize = if (isLuxury) 10.sp else MaterialTheme.typography.labelSmall.fontSize,
                                color = if (isLuxury) lux.textMuted else MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
            ) {
                // Section Filter Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TutorialSection.entries.forEach { section ->
                        if (isLuxury) {
                            val active = selectedSection == section
                            Surface(
                                onClick = { selectedSection = section },
                                shape = RoundedCornerShape(100.dp),
                                color = if (active) lux.accent.copy(alpha = 0.14f) else lux.elevatedSurface,
                                border = BorderStroke(1.dp, if (active) lux.accent else lux.borderHairline),
                                shadowElevation = 0.dp
                            ) {
                                Text(
                                    text = section.label.uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) lux.accent else lux.textSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            FilterChip(
                                selected = selectedSection == section,
                                onClick = { selectedSection = section },
                                label = { Text(section.label, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ─── Welcome / Interactive Starter Card ───
                    ObsidianCard(
                        accentColor = obsidianAccent,
                        verticalPadding = 2.dp,
                        headerContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ObsidianIconTile(
                                    icon = Icons.Rounded.MenuBook,
                                    accentColor = obsidianAccent
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Complete App Field Guide",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ObsidianTokens.TextPrimary
                                        )
                                        ObsidianMetallicBadge(
                                            text = "OFFLINE",
                                            accentColor = obsidianAccent
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Notes • [[Wiki-Links]] Graph • Ink Lasso • PDF OCR",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        color = ObsidianTokens.TextMuted
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Learn how every NoteGraph tool works — starting with how notes become linked hubs in your Knowledge Graph, and how to draw, select, and move sketches cleanly.",
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp,
                                color = ObsidianTokens.TextSecondary
                            )

                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val btnShape = RoundedCornerShape(if (isLuxury) 8.dp else 12.dp)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(btnShape)
                                        .background(ThemeManager.activeAccentBrush)
                                        .clickable(enabled = !isCreatingDemo) {
                                            isCreatingDemo = true
                                            viewModel.createDemoGraphNotes { createdNew ->
                                                isCreatingDemo = false
                                                Toast.makeText(
                                                    context,
                                                    if (createdNew) {
                                                        "Created 3 linked demo notes (Physics Overview, Calculus Basics, Wave Equations)"
                                                    } else {
                                                        "Demo notes already in vault — opening Knowledge Graph"
                                                    },
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                onOpenGraph()
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            tint = if (isLuxury) lux.onAccent else activePreset.onAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isCreatingDemo) "Creating…" else "Try Demo Graph",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isLuxury) lux.onAccent else activePreset.onAccent
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = onOpenGraph,
                                    shape = btnShape,
                                    border = BorderStroke(1.dp, if (isLuxury) lux.borderHairline else obsidianAccent.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = ObsidianTokens.TextPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = null,
                                        tint = accentInk,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Open Graph",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default
                                    )
                                }
                            }
                        },
                        footerContent = {
                            Text(
                                text = if (isLuxury) {
                                    "TIP: Tap 'Try Demo Graph' to insert 3 ready-linked notes and inspect blueprint edges right away."
                                } else {
                                    "TIP: Tap 'Try Demo Graph' to insert 3 ready-linked notes and see glowing edges right away."
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = ObsidianTokens.FooterText
                            )
                        }
                    )

                    // ─── SECTION 1: KNOWLEDGE GRAPH & CONNECTING NODES ───
                    if (showSection(TutorialSection.GRAPH)) {
                        TutorialGuideCard(
                            icon = Icons.Rounded.Share,
                            title = "Knowledge Graph: Making & Connecting Nodes",
                            badge = "CORE",
                            statValue = "3 Ways",
                            statLabel = "LINKING",
                            accentColor = obsidianAccent,
                            footer = "Remember: A note titled \"Untitled\" won't auto-link by plain name — give notes clear titles (>= 2 chars)!"
                        ) {
                            GraphDiagramPreview(
                                primaryColor = obsidianAccent,
                                secondaryColor = activePreset.accentEnd
                            )

                            Spacer(Modifier.height(12.dp))

                            TutorialStepRow(
                                step = "1",
                                accentColor = obsidianAccent,
                                heading = "Every Note = One Graph Node",
                                detail = "Whenever you create a note (+ New on Home), it automatically becomes a node on the Knowledge Graph. Nodes adopt their Folder's color; connected notes glow brighter and get labeled HUB."
                            )
                            TutorialStepRow(
                                step = "2",
                                accentColor = obsidianAccent,
                                heading = "Connect via [[Wiki-Links]] (Recommended)",
                                detail = "Inside any note body, type [[ — a floating 'Link note:' suggestion bar pops up. Tap an existing note to insert [[Note Title]], or type [[New Topic]] to plan a brand-new topic. Uncreated links appear below the note as [[New Topic]] (+ Create); tap to create & jump there!"
                            )
                            TutorialStepRow(
                                step = "3",
                                accentColor = obsidianAccent,
                                heading = "Connect via Bottom 'Link' Button",
                                detail = "In the Editor bottom glass toolbar, tap Link to browse your whole vault and insert [[Title]] directly at your cursor without typing brackets manually."
                            )
                            TutorialStepRow(
                                step = "4",
                                accentColor = obsidianAccent,
                                heading = "Connect via Automatic Title Mentions",
                                detail = "Mentioning another note's exact title anywhere in the note body, Note Segments, Checklists, Image OCR, or PDF Margin Notes automatically draws a graph edge and populates the Backlinks strip."
                            )
                            TutorialStepRow(
                                step = "5",
                                accentColor = obsidianAccent,
                                heading = "Navigate, Filter & Rearrange the Graph",
                                detail = "Pinch with 2 fingers (or use + / - / Reset) to zoom 20%–500%. Drag empty canvas to pan, or drag any node circle to arrange clusters. Tap a node once to inspect links at the bottom; tap again to open. Switch 'All' vs 'Linked' to hide isolated notes."
                            )
                        }
                    }

                    // ─── SECTION 2: SKETCH STUDIO & LASSO MOVE TOOL ───
                    if (showSection(TutorialSection.SKETCH)) {
                        TutorialGuideCard(
                            icon = Icons.Rounded.Brush,
                            title = "Sketch Studio & Lasso Move Tool",
                            badge = "DRAW",
                            statValue = "360° Drag",
                            statLabel = "LASSO",
                            accentColor = obsidianAccent,
                            footer = "All drawing, erasing, lasso moves, scaling & deletions support multi-step ↶ Undo / ↷ Redo."
                        ) {
                            TutorialStepRow(
                                step = "1",
                                accentColor = obsidianAccent,
                                heading = "Add & Expand Sketch Blocks",
                                detail = "Tap Sketch in the Editor's bottom toolbar to insert a drawing canvas. Tap the Expand icon (top-right of the toolbar) for Fullscreen Sketch Studio, and tap Paper: to switch between Theme, Dots, Grid, Lined, Cornell, and Blank rulings."
                            )
                            TutorialStepRow(
                                step = "2",
                                accentColor = obsidianAccent,
                                heading = "Pen, Highlight, Eraser, Laser & Snap",
                                detail = "Switch inks cleanly with 5 palette dots + Width slider. Enable Laser for a rainbow pointer that fades out after 1.5s. Toggle Snap ON to auto-straighten lines or snap closed loops into circles & rectangles. Toggle Stylus Only for S-Pen palm rejection."
                            )
                            TutorialStepRow(
                                step = "3",
                                accentColor = obsidianAccent,
                                heading = "Lasso Select & Smooth Interactive Move",
                                detail = "Tap Lasso and draw a dashed loop around the strokes you want. A unified dashed selection frame appears. Drag anywhere INSIDE the dashed box with your finger or stylus to glide strokes freely in any direction (up, down, left, right, diagonal) — or tap Move (turns to Move ON) to drag anywhere on the canvas!"
                            )
                            TutorialStepRow(
                                step = "4",
                                accentColor = obsidianAccent,
                                heading = "Scale, Delete & Deselect",
                                detail = "While strokes are selected in Lasso mode, tap Scale + (1.2x), Scale - (0.8x), Delete, or tap X (or tap outside when Move is off) to deselect."
                            )
                        }
                    }

                    // ─── SECTION 3: PDF ANNOTATION & OFFLINE OCR ───
                    if (showSection(TutorialSection.PDF)) {
                        TutorialGuideCard(
                            icon = Icons.Rounded.PictureAsPdf,
                            title = "PDF Markup, Focus Studio & Offline OCR",
                            badge = "PDF",
                            statValue = "ML Kit",
                            statLabel = "OFFLINE",
                            accentColor = obsidianAccent,
                            footer = "PDF strokes are normalized per page (0..1), staying razor-aligned across preview, fullscreen & PDF exports."
                        ) {
                            TutorialStepRow(
                                step = "1",
                                accentColor = obsidianAccent,
                                heading = "Import PDFs Anywhere",
                                detail = "Tap Open PDF on Home, PDF inside any note's bottom toolbar, or open any PDF from Files/Drive using 'Open with NoteGraph'."
                            )
                            TutorialStepRow(
                                step = "2",
                                accentColor = obsidianAccent,
                                heading = "Light / Night Reader & Two-Finger Zoom",
                                detail = "Flip the Light / Night toggle to invert white PDF pages into eye-friendly dark paper. Pinch with 2 fingers to zoom and pan while using Pen, Highlighter, or Eraser directly on top of pages."
                            )
                            TutorialStepRow(
                                step = "3",
                                accentColor = obsidianAccent,
                                heading = "Per-Page Margin Notes & OCR Picker",
                                detail = "Tap Note / Margin to jot page-level annotations (indexed by Search & Graph links!). Tap OCR to extract text offline from the Current Page, a Page Range, All Pages, or choose specific recognized paragraphs via the Block Picker."
                            )
                        }
                    }

                    // ─── SECTION 4: NOTES, FOLDERS, TAGS, VOICE & IMAGES ───
                    if (showSection(TutorialSection.NOTES)) {
                        TutorialGuideCard(
                            icon = Icons.Rounded.Label,
                            title = "Notes, Folders, Daily Journal, #Tags & Voice",
                            badge = "ORGANIZE",
                            statValue = "Multi-Block",
                            statLabel = "EDITOR",
                            accentColor = obsidianAccent,
                            footer = "Drag-and-drop note cards onto any Folder chip on Home to file notes effortlessly."
                        ) {
                            TutorialStepRow(
                                step = "1",
                                accentColor = obsidianAccent,
                                heading = "Folders & Drag-and-Drop Filing",
                                detail = "Tap + Folder to create emoji & color-coded folders (or presets like Study, Work). Long-press any note card on Home and drop it onto a folder chip — or onto Unfiled — to organize immediately. Long-press a folder chip to edit or delete it."
                            )
                            TutorialStepRow(
                                step = "2",
                                accentColor = obsidianAccent,
                                heading = "Today Daily Note, Templates & Trash",
                                detail = "Tap today's date pill (top-left of Home) to open or create today's Journal note. Tap Templates for structured starters (Cornell Study, Meeting Notes, etc.). Deleted notes move to Trash so you can restore mistakes."
                            )
                            TutorialStepRow(
                                step = "3",
                                accentColor = obsidianAccent,
                                heading = "Nested #Tags Tree",
                                detail = "Write #hashtags like #study/physics or #project/launch anywhere in a note or block. Open Tags from the bottom dock to browse the collapsible nested tag hierarchy."
                            )
                            TutorialStepRow(
                                step = "4",
                                accentColor = obsidianAccent,
                                heading = "Synced Voice Notes, Checklists & Image OCR",
                                detail = "Tap Voice to record waveform audio; any Note Segment text added while recording stamps a tappable 'Audio @ Xs' seek chip! Tap OCR for camera/gallery text extraction, or Tasks for trackable checklists."
                            )
                        }
                    }

                    // ─── SECTION 5: READER MODE, SECURITY, EXPORTS & SYNC ───
                    if (showSection(TutorialSection.VAULT)) {
                        TutorialGuideCard(
                            icon = Icons.Rounded.Lock,
                            title = "Book Reader, Vault PIN, Exports & Desktop Sync",
                            badge = "VAULT",
                            statValue = "5 Formats",
                            statLabel = "EXPORT",
                            accentColor = obsidianAccent,
                            footer = "Appearance & Vault Settings also lets you customize AMOLED, Kindle Paper, and Matte Studio presets."
                        ) {
                            TutorialStepRow(
                                step = "1",
                                accentColor = obsidianAccent,
                                heading = "Book Reader Mode Physics",
                                detail = "Open Reader from the Home bottom dock or the book icon in the Editor. Swipe left/right to turn pages with Corner Fold, 3D Curl, or Slide Stack physics, tap Bookmark to save your spot, or swipe straight up smoothly to toss/archive the sheet."
                            )
                            TutorialStepRow(
                                step = "2",
                                accentColor = obsidianAccent,
                                heading = "Vault PIN Protection",
                                detail = "Tap the Lock icon in the Editor top bar (or note card ⋮ menu) to set a local Vault PIN and lock private notes. Locked notes require your PIN before opening from Home, Search, Graph, Tags, or Reader."
                            )
                            TutorialStepRow(
                                step = "3",
                                accentColor = obsidianAccent,
                                heading = "5 Export Formats & .zip Vault Backup",
                                detail = "Tap Share in the Editor to export notes as .pdf, .docx, .pptx slides, .png, or .md. In Appearance & Vault Settings, export NoteGraph_Vault_Backup.zip to safely back up all notes or import your entire vault into NoteGraph Desktop."
                            )
                            TutorialStepRow(
                                step = "4",
                                accentColor = obsidianAccent,
                                heading = "3 Themes + Dynamic Gradient Accents",
                                detail = "Tap the Palette pill on Home or open Settings to switch between Deep Purple AMOLED, Kindle Paper (e-ink charcoal on warm parchment), and Matte Studio — each saves its own accent gradient preset locally."
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun GraphDiagramPreview(
    primaryColor: Color,
    secondaryColor: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val accentInk = ObsidianTokens.readableAccentInk(primaryColor)
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens
    val shape = RoundedCornerShape(if (isLuxury) 6.dp else 12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(172.dp)
            .clip(shape)
            .background(if (isLuxury) lux.recessedSurface else ObsidianTokens.StatTileSurface)
            .border(
                width = if (isLuxury) 1.dp else 0.75.dp,
                color = if (isLuxury) lux.borderHairline else primaryColor.copy(alpha = 0.35f),
                shape = shape
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val nodeA = Offset(w * 0.22f, h * 0.34f) // Physics Overview (HUB)
            val nodeB = Offset(w * 0.64f, h * 0.28f) // Calculus Basics
            val nodeC = Offset(w * 0.45f, h * 0.74f) // Wave Equations
            val nodeD = Offset(w * 0.85f, h * 0.72f) // Unlinked Note

            if (isLuxury) {
                // Subtle drafting grid lines
                val gridStep = 24.dp.toPx()
                val gridColor = lux.dividerRule.copy(alpha = 0.42f)
                var gx = gridStep
                while (gx < w) {
                    drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                    gx += gridStep
                }
                var gy = gridStep
                while (gy < h) {
                    drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                    gy += gridStep
                }

                // Ultra-thin 1px low-opacity curved vector edges (no neon glow)
                val edgeColor = lux.textSecondary.copy(alpha = GraphBlueprintSpec.UNSELECTED_EDGE_ALPHA)
                val activeEdgeColor = GraphBlueprintSpec.SignatureAccent.copy(alpha = 0.72f)
                fun drawCurvedEdge(a: Offset, b: Offset, col: Color, dashed: Boolean = false) {
                    val ctrl = GraphBlueprintSpec.bezierControlPoint(a, b)
                    val path = Path().apply {
                        moveTo(a.x, a.y)
                        quadraticBezierTo(ctrl.x, ctrl.y, b.x, b.y)
                    }
                    drawPath(
                        path = path,
                        color = col,
                        style = Stroke(
                            width = 1.2f,
                            cap = StrokeCap.Round,
                            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f) else null
                        )
                    )
                }
                drawCurvedEdge(nodeA, nodeB, activeEdgeColor)
                drawCurvedEdge(nodeA, nodeC, edgeColor)
                drawCurvedEdge(nodeB, nodeC, edgeColor, dashed = true)
            } else {
                // Classic thick straight edges
                drawLine(
                    color = primaryColor.copy(alpha = 0.85f),
                    start = nodeA,
                    end = nodeB,
                    strokeWidth = 3.2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = secondaryColor.copy(alpha = 0.85f),
                    start = nodeA,
                    end = nodeC,
                    strokeWidth = 3.0f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = primaryColor.copy(alpha = 0.6f),
                    start = nodeB,
                    end = nodeC,
                    strokeWidth = 2.4f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                )
            }

            // Edge labels
            val linkStyle = TextStyle(
                color = if (isLuxury) GraphBlueprintSpec.SignatureAccent else accentInk,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "[[Calculus]]",
                topLeft = Offset((nodeA.x + nodeB.x) / 2f - 32.dp.toPx(), nodeA.y - 24.dp.toPx()),
                style = linkStyle
            )
            drawText(
                textMeasurer = textMeasurer,
                text = if (isLuxury) "MENTION" else "title mention",
                topLeft = Offset((nodeB.x + nodeC.x) / 2f + 6.dp.toPx(), (nodeB.y + nodeC.y) / 2f - 4.dp.toPx()),
                style = linkStyle.copy(color = if (isLuxury) lux.textMuted else ObsidianTokens.TextMuted)
            )

            // Draw nodes
            fun drawMiniNode(center: Offset, title: String, isConnected: Boolean, isHub: Boolean) {
                if (isLuxury) {
                    if (isHub) {
                        val rectW = 28.dp.toPx()
                        val rectH = 18.dp.toPx()
                        val topLeft = Offset(center.x - rectW / 2f, center.y - rectH / 2f)
                        val r = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        drawRoundRect(
                            color = lux.elevatedSurface,
                            topLeft = topLeft,
                            size = Size(rectW, rectH),
                            cornerRadius = r
                        )
                        drawRoundRect(
                            color = GraphBlueprintSpec.SignatureAccent,
                            topLeft = topLeft,
                            size = Size(rectW, rectH),
                            cornerRadius = r,
                            style = Stroke(width = 1.4f)
                        )
                        drawCircle(
                            color = GraphBlueprintSpec.SignatureAccent,
                            radius = 2.5.dp.toPx(),
                            center = center
                        )
                    } else {
                        val r = 9.dp.toPx()
                        drawCircle(
                            color = lux.elevatedSurface,
                            radius = r,
                            center = center
                        )
                        drawCircle(
                            color = if (isConnected) lux.borderHairline else lux.textMuted.copy(alpha = 0.55f),
                            radius = r,
                            center = center,
                            style = Stroke(
                                width = 1.2f,
                                pathEffect = if (!isConnected) PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f) else null
                            )
                        )
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = title.uppercase(),
                        topLeft = Offset(center.x - 42.dp.toPx(), center.y + 13.dp.toPx()),
                        style = TextStyle(
                            color = if (isConnected) lux.textPrimary else lux.textMuted,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isHub) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                } else {
                    val baseRadius = if (isHub) 16.dp.toPx() else if (isConnected) 13.dp.toPx() else 10.dp.toPx()
                    val col = if (isConnected) primaryColor else Color(0xFF64748B)
                    if (isConnected) {
                        drawCircle(
                            color = col.copy(alpha = 0.22f),
                            radius = baseRadius * 1.55f,
                            center = center
                        )
                    }
                    drawCircle(
                        color = col,
                        radius = baseRadius,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = if (isConnected) 0.85f else 0.35f),
                        radius = baseRadius * 0.28f,
                        center = center
                    )
                    if (!isConnected) {
                        drawCircle(
                            color = ObsidianTokens.TextMuted.copy(alpha = 0.45f),
                            radius = baseRadius + 4.dp.toPx(),
                            center = center,
                            style = Stroke(
                                width = 1.2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )
                        )
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = title,
                        topLeft = Offset(center.x - 42.dp.toPx(), center.y + baseRadius + 5.dp.toPx()),
                        style = TextStyle(
                            color = if (isConnected) ObsidianTokens.TextPrimary else ObsidianTokens.TextMuted,
                            fontSize = 10.sp,
                            fontWeight = if (isHub) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }

            drawMiniNode(nodeA, "Physics (Hub)", isConnected = true, isHub = true)
            drawMiniNode(nodeB, "Calculus Basics", isConnected = true, isHub = false)
            drawMiniNode(nodeC, "Wave Equations", isConnected = true, isHub = false)
            drawMiniNode(nodeD, "Unlinked Note", isConnected = false, isHub = false)
        }
    }
}

@Composable
private fun TutorialGuideCard(
    icon: ImageVector,
    title: String,
    badge: String,
    statValue: String,
    statLabel: String,
    accentColor: Color,
    footer: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ObsidianCard(
        accentColor = accentColor,
        verticalPadding = 2.dp,
        headerContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ObsidianIconTile(
                    icon = icon,
                    accentColor = accentColor,
                    size = 40.dp,
                    iconSize = 19.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ObsidianTokens.TextPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        ObsidianMetallicBadge(
                            text = badge,
                            accentColor = accentColor
                        )
                    }
                }
                ObsidianStatTile(
                    value = statValue,
                    label = statLabel,
                    accentColor = accentColor
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ObsidianTokens.DividerRule)
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        },
        footerContent = {
            Text(
                text = footer,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = ObsidianTokens.FooterText
            )
        }
    )
}

@Composable
private fun TutorialStepRow(
    step: String,
    accentColor: Color,
    heading: String,
    detail: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val isLuxury = ThemeManager.isLuxuryUi
        val lux = ThemeManager.luxuryTokens
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isLuxury) lux.recessedSurface else accentColor.copy(alpha = 0.22f))
                .border(1.dp, if (isLuxury) lux.borderHairline else accentColor.copy(alpha = 0.65f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                fontSize = 10.sp,
                fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.Bold,
                color = if (isLuxury) GraphBlueprintSpec.SignatureAccent else ObsidianTokens.readableAccentInk(accentColor)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = heading,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = if (isLuxury) FontFamily.SansSerif else FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                color = ObsidianTokens.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 17.sp,
                color = ObsidianTokens.TextSecondary
            )
        }
    }
}
