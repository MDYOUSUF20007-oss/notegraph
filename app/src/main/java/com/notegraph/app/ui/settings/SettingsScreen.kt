package com.notegraph.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notegraph.app.data.local.NoteJson
import com.notegraph.app.ui.components.ObsidianCard
import com.notegraph.app.ui.components.ObsidianIconTile
import com.notegraph.app.ui.components.ObsidianMetallicBadge
import com.notegraph.app.ui.components.ObsidianStatTile
import com.notegraph.app.ui.components.ObsidianTokens
import com.notegraph.app.ui.theme.AppThemeMode
import com.notegraph.app.ui.theme.GradientSwatchRow
import com.notegraph.app.ui.theme.LuxuryTokens
import com.notegraph.app.ui.theme.MinimalistBackground
import com.notegraph.app.ui.theme.ThemeManager
import com.notegraph.app.ui.theme.UiEngineMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateTutorial: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedTheme = ThemeManager.currentTheme
    val uiEngine = ThemeManager.uiEngine
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens
    val activePreset = ThemeManager.currentGradient
    val gradientsOn = !isLuxury && ThemeManager.gradientsEnabled
    val syncPrefs by viewModel.syncPrefs.collectAsState()
    val lanHostState by viewModel.lanHostState.collectAsState()

    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var isFolderSyncing by remember { mutableStateOf(false) }
    var isLanSyncing by remember { mutableStateOf(false) }
    var peerAddress by remember { mutableStateOf(viewModel.getLastPeerAddress()) }
    var peerPin by remember { mutableStateOf("") }

    // SAF Create Document for Sync v2 .zip backup (v2 + legacy notes.json + media assets)
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        isBackingUp = true
        scope.launch {
            try {
                val count = viewModel.exportZipBackup(uri)
                Toast.makeText(context, "Sync v2 backup complete ($count notes exported)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isBackingUp = false
            }
        }
    }

    // SAF Open Document for merging/restoring .zip (supports both Sync v2 and legacy zips)
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        isRestoring = true
        scope.launch {
            try {
                val summary = viewModel.importAndMergeZip(uri)
                Toast.makeText(
                    context,
                    "Synced ${summary.notesMerged} updated, ${summary.newNotesAdded} added (${summary.linksRebuilt} links)",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Sync restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isRestoring = false
            }
        }
    }

    // SAF Open Document Tree for Shared Cloud Folder (Drive / OneDrive / Dropbox / Syncthing)
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri ?: return@rememberLauncherForActivityResult
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        } catch (_: SecurityException) {
        }
        viewModel.setSavedFolderUri(treeUri)
        isFolderSyncing = true
        scope.launch {
            try {
                val summary = viewModel.syncWithSafFolder(treeUri)
                Toast.makeText(
                    context,
                    "Folder synced (${summary.newNotesAdded} added, ${summary.notesMerged} merged)",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Folder sync error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isFolderSyncing = false
            }
        }
    }

    MinimalistBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        if (isLuxury) {
                            Text(
                                text = "Appearance & Vault",
                                style = MaterialTheme.typography.headlineMedium,
                                fontFamily = FontFamily.Serif,
                                color = lux.textPrimary
                            )
                        } else {
                            Text(
                                text = "Appearance & Vault",
                                style = MaterialTheme.typography.titleLarge
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            val obsidianAccent = ObsidianTokens.resolveAccent(activePreset.accentStart)
            val accentInk = ObsidianTokens.readableAccentInk(obsidianAccent)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ─── Live Interactive Theme + Architecture Preview ───
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
                                icon = Icons.Rounded.Palette,
                                accentColor = obsidianAccent
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = when {
                                            isLuxury -> selectedTheme.luxuryName
                                            gradientsOn -> activePreset.name
                                            else -> "${activePreset.name} · Flat"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianTokens.TextPrimary
                                    )
                                    ObsidianMetallicBadge(
                                        text = selectedTheme.shortLabelFor(isLuxury),
                                        accentColor = obsidianAccent
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isLuxury) {
                                        "${selectedTheme.luxurySubtitle} // 1px Flat System"
                                    } else {
                                        "${selectedTheme.displayName} • Obsidian Card System"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = ObsidianTokens.TextMuted
                                )
                            }
                            ObsidianStatTile(
                                value = when {
                                    isLuxury -> "1PX"
                                    gradientsOn -> "GRAD"
                                    else -> "FLAT"
                                },
                                label = if (isLuxury) "LUXURY" else "FINISH",
                                accentColor = obsidianAccent
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Mini mock note card preview inside Tier 1
                        Surface(
                            shape = RoundedCornerShape(if (isLuxury) 6.dp else 12.dp),
                            color = ObsidianTokens.StatTileSurface,
                            border = BorderStroke(
                                width = if (isLuxury) 1.dp else 0.75.dp,
                                color = if (isLuxury) lux.borderHairline else obsidianAccent.copy(alpha = 0.45f)
                            )
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
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ObsidianTokens.TextPrimary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (isLuxury) {
                                            "Zero gradients · 1px borders · monospace chrome"
                                        } else {
                                            "Frosted two-tier contrast · specular edge glow"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ObsidianTokens.TextMuted
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(if (isLuxury) 100.dp else 20.dp))
                                        .background(ThemeManager.activeAccentBrush)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (isLuxury) "NEW" else "New",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = activePreset.onAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    },
                    footerContent = {
                        Text(
                            text = if (isLuxury) {
                                "Engine: Bespoke Luxury • Surface: ${selectedTheme.luxuryName} • Zero gradient fills · Persisted in SharedPreferences"
                            } else {
                                "Active preset: ${activePreset.name} (${selectedTheme.displayName}) • Persisted locally via SharedPreferences • Dynamic accent synced to cards & graph nodes"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            color = ObsidianTokens.FooterText
                        )
                    }
                )

                // ─── Theme Mode Matrix (AMOLED Black, Kindle Parchment, Matte Minimalist) ───
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isLuxury) "CURATED THEME SURFACE" else "Theme Surface",
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
                            val modeLux = LuxuryTokens.forMode(mode)
                            val icon = when (mode) {
                                AppThemeMode.DEEP_PURPLE_AMOLED -> Icons.Rounded.DarkMode
                                AppThemeMode.KINDLE_PARCHMENT -> Icons.Rounded.MenuBook
                                AppThemeMode.MATTE_STUDIO -> Icons.Rounded.LaptopMac
                            }
                            val subtitle = when {
                                isLuxury -> mode.luxurySubtitle
                                mode == AppThemeMode.DEEP_PURPLE_AMOLED -> "True Black"
                                mode == AppThemeMode.KINDLE_PARCHMENT -> "E-Ink Paper"
                                else -> "Slate Studio"
                            }

                            Surface(
                                onClick = { ThemeManager.setTheme(mode) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(if (isLuxury) 8.dp else 16.dp),
                                color = if (isLuxury) {
                                    if (isSelected) modeLux.recessedSurface else modeLux.elevatedSurface
                                } else {
                                    if (isSelected) modePreset.cardTint else MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(
                                    width = if (isLuxury) 1.dp else if (isSelected) 1.5.dp else 0.75.dp,
                                    color = when {
                                        isLuxury && isSelected -> modeLux.accent
                                        isLuxury -> lux.borderHairline
                                        isSelected -> modePreset.accentStart
                                        else -> ThemeManager.cardBorderColor
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 13.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isLuxury) {
                                        Surface(
                                            shape = CircleShape,
                                            color = modeLux.canvasBackground,
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) modeLux.accent else modeLux.borderHairline
                                            ),
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Rounded.Check else icon,
                                                    contentDescription = mode.luxuryName,
                                                    tint = if (isSelected) modeLux.accent else modeLux.textSecondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    } else {
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
                                    }
                                    Text(
                                        text = mode.shortLabelFor(isLuxury),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isLuxury) modeLux.textPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    Text(
                                        text = subtitle,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        color = if (isLuxury) modeLux.textMuted else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── Curated Luxury Palette / Classic Gradient Palette ───
                if (isLuxury) {
                    ObsidianCard(
                        accentColor = obsidianAccent,
                        verticalPadding = 2.dp,
                        headerContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Curated Surface Tokens",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ObsidianTokens.TextPrimary
                                    )
                                    Text(
                                        text = "Strict zero-gradient palette for ${selectedTheme.luxuryName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ObsidianTokens.TextMuted
                                    )
                                }
                                ObsidianMetallicBadge(
                                    text = "FLAT",
                                    accentColor = obsidianAccent
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val swatches = listOf(
                                    "CANVAS" to lux.canvasBackground,
                                    "SURFACE" to lux.elevatedSurface,
                                    "1PX" to lux.borderHairline,
                                    "ACCENT" to lux.accent
                                )
                                swatches.forEach { (label, swatchCol) ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = lux.recessedSurface,
                                        border = BorderStroke(1.dp, lux.borderHairline),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = swatchCol,
                                                border = BorderStroke(1.dp, lux.borderHairline),
                                                modifier = Modifier.size(18.dp)
                                            ) {}
                                            Text(
                                                text = label,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 8.5.sp,
                                                color = lux.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        footerContent = {
                            Text(
                                text = "AMOLED (#000000/#121212/#262626), Kindle Parchment (#F4F1EA), Matte Minimalist (#1E2229) • Accent #8B5CF6",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 14.5.sp,
                                color = ObsidianTokens.FooterText
                            )
                        }
                    )
                } else {
                    ObsidianCard(
                        accentColor = obsidianAccent,
                        verticalPadding = 2.dp,
                        headerContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${selectedTheme.shortLabel} Gradient Palette",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ObsidianTokens.TextPrimary
                                    )
                                    Text(
                                        text = "Each theme remembers its own gradient color",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ObsidianTokens.TextMuted
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
                                        tint = accentInk
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            GradientSwatchRow(
                                mode = selectedTheme,
                                selectedId = ThemeManager.selectedGradientIdFor(selectedTheme),
                                onSelect = { chosen ->
                                    ThemeManager.setGradient(selectedTheme, chosen.id)
                                },
                                labelColor = ObsidianTokens.TextPrimary,
                                secondaryLabelColor = ObsidianTokens.TextMuted
                            )

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = ObsidianTokens.DividerRule)
                            Spacer(Modifier.height(10.dp))

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
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ObsidianTokens.TextPrimary
                                    )
                                    Text(
                                        text = if (gradientsOn) "Multi-stop accent & card sheen active" else "Minimal classic flat surfaces",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ObsidianTokens.TextMuted
                                    )
                                }
                                Switch(
                                    checked = gradientsOn,
                                    onCheckedChange = { ThemeManager.setGradientsEnabledState(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = activePreset.onAccent,
                                        checkedTrackColor = obsidianAccent
                                    )
                                )
                            }
                        },
                        footerContent = {
                            Text(
                                text = "AMOLED, Kindle Paper, and Matte Studio each keep independent accent presets offline on this device.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp,
                                color = ObsidianTokens.FooterText
                            )
                        }
                    )
                }

                // ─── Interface Engine Switcher (Bottom of Theme / Appearance Section) ───
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
                                icon = Icons.Rounded.Layers,
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
                                        text = "Interface Engine",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ObsidianTokens.TextPrimary
                                    )
                                    ObsidianMetallicBadge(
                                        text = uiEngine.badgeLabel,
                                        accentColor = obsidianAccent
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Switches live across cards, graph blueprint, canvas & toolbars without restart",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ObsidianTokens.TextMuted
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                UiEngineMode.CLASSIC,
                                UiEngineMode.BESPOKE_LUXURY
                            ).forEach { option ->
                                val isActive = uiEngine == option
                                val optionBorder = when {
                                    isActive && isLuxury -> lux.accent
                                    isActive -> obsidianAccent
                                    isLuxury -> lux.borderHairline
                                    else -> ObsidianTokens.DividerRule
                                }
                                val optionFill = when {
                                    isActive && isLuxury -> lux.accent.copy(alpha = 0.10f)
                                    isActive -> obsidianAccent.copy(alpha = 0.14f)
                                    else -> ObsidianTokens.StatTileSurface
                                }

                                Surface(
                                    onClick = { ThemeManager.setUiEngine(option) },
                                    shape = RoundedCornerShape(if (isLuxury) 6.dp else 12.dp),
                                    color = optionFill,
                                    border = BorderStroke(
                                        width = if (isActive) 1.25.dp else 1.dp,
                                        color = optionBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RadioButton(
                                            selected = isActive,
                                            onClick = { ThemeManager.setUiEngine(option) },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = obsidianAccent,
                                                unselectedColor = ObsidianTokens.TextMuted
                                            )
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = option.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                                                color = ObsidianTokens.TextPrimary
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = option.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ObsidianTokens.TextMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    footerContent = {
                        Text(
                            text = "Active engine: ${uiEngine.displayName} • Stored locally in SharedPreferences • Instant runtime tree switch",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.5.sp,
                            color = ObsidianTokens.FooterText
                        )
                    }
                )

                // ─── Cross-OS Cloud Folder & Wi-Fi LAN Sync (ObsidianCard) ───
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
                                icon = Icons.Rounded.Sync,
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
                                        text = "Cross-OS Node Sync",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ObsidianTokens.TextPrimary
                                    )
                                    ObsidianMetallicBadge(
                                        text = "WIN / MAC / ANDROID",
                                        accentColor = obsidianAccent
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Two-way LWW merge for every note, folder, checklist, ink stroke, PDF markup, graph link & deletion across Android, Windows & macOS.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ObsidianTokens.TextSecondary
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // 1. Shared Cloud Folder (Drive / OneDrive / Syncthing / Dropbox / iCloud Bridge)
                        Text(
                            text = "Shared Cloud Folder Sync",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ObsidianTokens.TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        val savedFolderDisplay = syncPrefs.savedFolderUri?.let { uriStr ->
                            Uri.decode(Uri.parse(uriStr).lastPathSegment ?: uriStr)
                        } ?: "No shared folder linked"
                        Text(
                            text = savedFolderDisplay,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = ObsidianTokens.TextMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianTokens.TextPrimary),
                                border = BorderStroke(1.dp, obsidianAccent.copy(alpha = 0.45f))
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = accentInk, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Choose Folder", fontSize = 12.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .alpha(if (!isFolderSyncing && syncPrefs.savedFolderUri != null) 1f else 0.45f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ThemeManager.activeAccentBrush)
                                    .clickable(enabled = !isFolderSyncing && syncPrefs.savedFolderUri != null) {
                                        val uriStr = syncPrefs.savedFolderUri ?: return@clickable
                                        isFolderSyncing = true
                                        scope.launch {
                                            try {
                                                val summary = viewModel.syncWithSafFolder(Uri.parse(uriStr))
                                                Toast.makeText(
                                                    context,
                                                    "Synced (${summary.newNotesAdded} added, ${summary.notesMerged} merged)",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isFolderSyncing = false
                                            }
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isFolderSyncing) "Syncing…" else "Sync Folder Now",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = activePreset.onAccent
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setAutoSyncOnOpen(!syncPrefs.autoSyncOnOpen) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Auto-sync shared folder on launch",
                                style = MaterialTheme.typography.bodySmall,
                                color = ObsidianTokens.TextSecondary
                            )
                            Switch(
                                checked = syncPrefs.autoSyncOnOpen,
                                onCheckedChange = { viewModel.setAutoSyncOnOpen(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = activePreset.onAccent,
                                    checkedTrackColor = obsidianAccent
                                )
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = ObsidianTokens.DividerRule)
                        Spacer(Modifier.height(12.dp))

                        // 2. Direct Wi-Fi LAN Peer-to-Peer Sync
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Wifi, contentDescription = null, tint = accentInk, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Direct Wi-Fi LAN Sync",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ObsidianTokens.TextPrimary
                                )
                            }
                            Switch(
                                checked = lanHostState.isRunning,
                                onCheckedChange = { enabled ->
                                    if (enabled) viewModel.startLanHostServer() else viewModel.stopLanHostServer()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = activePreset.onAccent,
                                    checkedTrackColor = obsidianAccent
                                )
                            )
                        }

                        if (lanHostState.isRunning) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianTokens.StatTileSurface,
                                border = BorderStroke(0.75.dp, obsidianAccent.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val hostDisplay = if (lanHostState.addresses.isNotEmpty()) {
                                        "${lanHostState.addresses.first()}:${lanHostState.port}"
                                    } else {
                                        "Port ${lanHostState.port}"
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Enter on Win / Mac: $hostDisplay",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ObsidianTokens.TextPrimary
                                        )
                                        Text(
                                            text = "Sync PIN: ${lanHostState.pin} • ${lanHostState.statusMessage}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            color = ObsidianTokens.TextMuted
                                        )
                                    }
                                    IconButton(onClick = { viewModel.regenerateLanPin() }) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "New PIN", tint = accentInk, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = peerAddress,
                                onValueChange = { peerAddress = it },
                                label = { Text("Desktop / Peer IP:Port") },
                                placeholder = { Text("192.168.1.42:8765") },
                                singleLine = true,
                                modifier = Modifier.weight(1.6f),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            )
                            OutlinedTextField(
                                value = peerPin,
                                onValueChange = { peerPin = it },
                                label = { Text("PIN") },
                                placeholder = { Text("4829") },
                                singleLine = true,
                                modifier = Modifier.weight(0.9f),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                isLanSyncing = true
                                scope.launch {
                                    val result = viewModel.syncWithLanPeer(peerAddress, peerPin)
                                    result.onSuccess { summary ->
                                        Toast.makeText(
                                            context,
                                            "Wi-Fi Synced with ${summary.remoteDevice}: ${summary.notesMerged} merged, ${summary.newNotesAdded} added",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, err.message ?: "Wi-Fi sync failed", Toast.LENGTH_SHORT).show()
                                    }
                                    isLanSyncing = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLanSyncing && peerAddress.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentInk),
                            border = BorderStroke(1.dp, obsidianAccent.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = accentInk, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isLanSyncing) "Syncing over Wi-Fi…" else "Sync with Peer over Wi-Fi")
                        }
                    },
                    footerContent = {
                        val lastTime = if (syncPrefs.lastSyncAt > 0L) {
                            NoteJson.formatShortDateTime(syncPrefs.lastSyncAt)
                        } else {
                            "Never"
                        }
                        Text(
                            text = "Device: ${syncPrefs.deviceName} • Last sync: $lastTime • ${syncPrefs.lastSyncMessage}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            color = ObsidianTokens.FooterText
                        )
                    }
                )

                // ─── Vault Backup & Restore (ObsidianCard) ───
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
                                icon = Icons.Rounded.Backup,
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
                                        text = "Portable Vault Archive",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ObsidianTokens.TextPrimary
                                    )
                                    ObsidianMetallicBadge(
                                        text = "ZIP v2",
                                        accentColor = obsidianAccent
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Export & merge notes, folders, ink strokes, boards, checklists & PDF assets via portable .zip across Android, Windows & macOS.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ObsidianTokens.TextSecondary
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isBackingUp) 0.55f else 1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ThemeManager.activeAccentBrush)
                                .clickable(enabled = !isBackingUp) {
                                    backupLauncher.launch("NoteGraph_Vault_Sync_v2.zip")
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
                                    text = if (isBackingUp) "Archiving Vault…" else "Export Sync Package (.zip)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = activePreset.onAccent
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isRestoring,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ObsidianTokens.TextPrimary
                            ),
                            border = BorderStroke(1.dp, obsidianAccent.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Restore,
                                contentDescription = null,
                                tint = accentInk,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isRestoring) "Merging Vault…" else "Merge / Restore from .zip")
                        }
                    },
                    footerContent = {
                        Text(
                            text = "WAL SQLite database • Zero cloud lock-in • Includes Sync v2 + legacy notes.json compatibility.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            color = ObsidianTokens.FooterText
                        )
                    }
                )

                // ─── App Tutorial & Graph Guide (ObsidianCard) ───
                ObsidianCard(
                    accentColor = obsidianAccent,
                    verticalPadding = 2.dp,
                    onClick = onNavigateTutorial,
                    headerContent = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ObsidianIconTile(
                                icon = Icons.Rounded.MenuBook,
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
                                        text = "App Tutorial & Field Guide",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ObsidianTokens.TextPrimary
                                    )
                                    ObsidianMetallicBadge(
                                        text = "GUIDE",
                                        accentColor = obsidianAccent
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Step-by-step walkthrough: connecting Knowledge Graph nodes, Sketch Lasso move, PDF OCR, Voice timestamps & Vault security.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ObsidianTokens.TextSecondary
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = onNavigateTutorial,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = accentInk
                            ),
                            border = BorderStroke(1.dp, obsidianAccent.copy(alpha = 0.55f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MenuBook,
                                contentDescription = null,
                                tint = accentInk,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Open Interactive Tutorial")
                        }
                    },
                    footerContent = {
                        Text(
                            text = "Includes a 1-tap demo cluster generator to visualize linked nodes immediately.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            color = ObsidianTokens.FooterText
                        )
                    }
                )

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
