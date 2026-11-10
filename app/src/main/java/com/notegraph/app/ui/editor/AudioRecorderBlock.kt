package com.notegraph.app.ui.editor

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notegraph.app.ui.theme.ThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

private const val VISUALIZER_BAR_COUNT = 44

/**
 * Self-contained minimalist voice block with a real-time theme-matched audio waveform visualizer
 * during both live microphone recording and interactive playback scrubbing.
 */
@Composable
fun AudioRecorderBlock(
    audioFilePath: String,
    durationMs: Long,
    seekToRequestMs: Long? = null,
    onSeekHandled: () -> Unit = {},
    onRecordingStarted: (filePath: String, startTimeMs: Long) -> Unit,
    onRecordingStopped: (durationMs: Long) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var recordStartTime by remember { mutableLongStateOf(0L) }
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }
    var playbackPosition by remember { mutableIntStateOf(0) }
    var mediaDurationMs by remember(audioFilePath, durationMs) { mutableLongStateOf(durationMs) }

    val liveAmplitudes = remember(audioFilePath) { mutableStateListOf<Float>() }
    val sessionSamples = remember(audioFilePath) { mutableListOf<Float>() }
    var savedPeaks by remember(audioFilePath, durationMs) {
        mutableStateOf(loadOrSynthesizePeaks(audioFilePath, durationMs))
    }

    val activePreset = ThemeManager.currentGradient
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens
    val gradientsOn = !isLuxury && ThemeManager.gradientsEnabled

    val infiniteTransition = rememberInfiniteTransition(label = "voiceVizPulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    // Real-time amplitude sampling while recording
    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        while (isActive && isRecording) {
            recordingElapsedMs = (System.currentTimeMillis() - recordStartTime).coerceAtLeast(0L)
            val rawAmp = try {
                recorder?.maxAmplitude ?: 0
            } catch (_: Exception) {
                0
            }
            val compressed = sqrt((rawAmp / 32767f).coerceIn(0f, 1f)).coerceIn(0.08f, 1f)
            sessionSamples.add(compressed)
            liveAmplitudes.add(compressed)
            if (liveAmplitudes.size > VISUALIZER_BAR_COUNT) {
                liveAmplitudes.removeAt(0)
            }
            delay(55L)
        }
    }

    // Playhead polling while playing back
    LaunchedEffect(isPlaying, player) {
        if (!isPlaying) return@LaunchedEffect
        while (isActive && isPlaying) {
            val mp = player ?: break
            try {
                if (mp.isPlaying) {
                    playbackPosition = mp.currentPosition
                    val d = mp.duration
                    if (d > 0) mediaDurationMs = d.toLong()
                }
            } catch (_: Exception) {
            }
            delay(45L)
        }
    }

    LaunchedEffect(seekToRequestMs, audioFilePath) {
        val targetMs = seekToRequestMs
        if (targetMs != null && !isRecording && File(audioFilePath).exists()) {
            try {
                player?.release()
                val mp = MediaPlayer().apply {
                    setDataSource(audioFilePath)
                    prepare()
                    if (duration > 0) mediaDurationMs = duration.toLong()
                    val clamped = targetMs.toInt().coerceIn(0, duration.coerceAtLeast(0))
                    seekTo(clamped)
                    start()
                    setOnCompletionListener {
                        isPlaying = false
                        playbackPosition = 0
                        try {
                            it.release()
                        } catch (_: Exception) {
                        }
                        if (player === it) {
                            player = null
                        }
                    }
                }
                player = mp
                playbackPosition = targetMs.toInt()
                isPlaying = true
            } catch (_: Exception) {
            } finally {
                onSeekHandled()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(Unit) {
        onDispose {
            try { recorder?.release() } catch (_: Exception) {}
            try { player?.release() } catch (_: Exception) {}
        }
    }

    val fileExists = remember(audioFilePath, isRecording, durationMs) {
        audioFilePath.isNotBlank() && File(audioFilePath).exists()
    }
    val effectiveTotalMs = mediaDurationMs.coerceAtLeast(durationMs).coerceAtLeast(0L)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ─── Header with Live / Playback Timer ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isRecording || isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = if (isLuxury) lux.accent else activePreset.accentStart,
                    modifier = Modifier.size(16.dp)
                )
                Text("Voice Recording", style = MaterialTheme.typography.titleSmall)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isRecording) {
                    val dotAlpha = 0.55f + 0.45f * sin(pulsePhase * 2f)
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = dotAlpha.coerceIn(0.3f, 1f)))
                    )
                    Text(
                        text = formatClockMs(recordingElapsedMs),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLuxury) lux.accent else activePreset.accentStart
                    )
                } else if (fileExists && effectiveTotalMs > 0L) {
                    Text(
                        text = if (isPlaying || playbackPosition > 0) {
                            "${formatClockMs(playbackPosition.toLong())} / ${formatClockMs(effectiveTotalMs)}"
                        } else {
                            formatClockMs(effectiveTotalMs)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                        color = if (isLuxury) lux.textMuted else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ─── Theme-Matched Real-Time Voice Visualizer Canvas ───
        val vizShape = RoundedCornerShape(if (isLuxury) 6.dp else 14.dp)
        val vizBgBrush = if (isLuxury) {
            SolidColor(lux.recessedSurface)
        } else if (gradientsOn) {
            Brush.horizontalGradient(
                listOf(
                    activePreset.cardTint.copy(alpha = 0.72f),
                    activePreset.cardSurface
                )
            )
        } else {
            SolidColor(activePreset.cardSurface)
        }
        val activeBarBrush = if (isLuxury) {
            SolidColor(lux.accent)
        } else if (gradientsOn) {
            Brush.verticalGradient(listOf(activePreset.accentStart, activePreset.accentEnd))
        } else {
            SolidColor(activePreset.accentStart)
        }
        val inactiveBarColor = (if (isLuxury) lux.textMuted else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.26f)
        val borderColor = when {
            isRecording || isPlaying -> (if (isLuxury) lux.accent else activePreset.accentStart).copy(alpha = 0.75f)
            isLuxury -> lux.borderHairline
            else -> ThemeManager.cardBorderColor
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(vizShape)
                .background(vizBgBrush)
                .border(if (isLuxury) 1.dp else 0.85.dp, borderColor, vizShape)
                .pointerInput(audioFilePath, fileExists, isRecording, effectiveTotalMs) {
                    if (fileExists && !isRecording && effectiveTotalMs > 0L) {
                        detectTapGestures { tapOffset ->
                            val fraction = (tapOffset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val targetPos = (effectiveTotalMs * fraction).toInt()
                            playbackPosition = targetPos
                            try {
                                player?.seekTo(targetPos)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val playFraction = if (effectiveTotalMs > 0L) {
                (playbackPosition.toFloat() / effectiveTotalMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                val step = w / VISUALIZER_BAR_COUNT
                val barW = (step * 0.56f).coerceIn(2.5f, 7f)
                val centerY = h / 2f
                val corner = CornerRadius(barW / 2f, barW / 2f)

                val displayBars: List<Float> = when {
                    isRecording -> {
                        val padCount = (VISUALIZER_BAR_COUNT - liveAmplitudes.size).coerceAtLeast(0)
                        List(padCount) { 0.08f } + liveAmplitudes.takeLast(VISUALIZER_BAR_COUNT)
                    }
                    fileExists -> savedPeaks
                    else -> List(VISUALIZER_BAR_COUNT) { idx ->
                        0.16f + 0.12f * abs(sin(idx * 0.35f))
                    }
                }

                displayBars.forEachIndexed { idx, rawNorm ->
                    val ratio = idx.toFloat() / (VISUALIZER_BAR_COUNT - 1).coerceAtLeast(1)
                    val isBarActive = when {
                        isRecording -> idx >= (VISUALIZER_BAR_COUNT - liveAmplitudes.size)
                        fileExists && (isPlaying || playbackPosition > 0) -> ratio <= playFraction
                        else -> false
                    }

                    val ripple = if (isPlaying && isBarActive) {
                        val distToHead = abs(ratio - playFraction)
                        if (distToHead < 0.08f) {
                            1f + 0.22f * abs(sin(pulsePhase * 2f + idx * 0.6f))
                        } else 1f
                    } else 1f

                    val clampedH = (rawNorm * ripple).coerceIn(0.10f, 1f)
                    val barH = (h * clampedH).coerceAtLeast(barW * 1.4f)
                    val left = idx * step + (step - barW) / 2f
                    val top = centerY - barH / 2f

                    if (isBarActive) {
                        drawRoundRect(
                            brush = activeBarBrush,
                            topLeft = Offset(left, top),
                            size = Size(barW, barH),
                            cornerRadius = corner
                        )
                    } else {
                        drawRoundRect(
                            color = inactiveBarColor,
                            topLeft = Offset(left, top),
                            size = Size(barW, barH),
                            cornerRadius = corner
                        )
                    }
                }

                // Crisp playhead marker when playing or scrubbed
                if (!isRecording && fileExists && (isPlaying || playbackPosition > 0) && effectiveTotalMs > 0L) {
                    val headX = (playFraction * w).coerceIn(1f, w - 1f)
                    drawLine(
                        color = if (isLuxury) lux.accent else activePreset.accentEnd,
                        start = Offset(headX, 0f),
                        end = Offset(headX, h),
                        strokeWidth = if (isLuxury) 1.5f else 2.2f
                    )
                }
            }

            if (!isRecording && !fileExists) {
                Text(
                    text = if (isLuxury) "MIC LEVELS READY" else "Ready to visualize microphone levels",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                    fontSize = 10.sp,
                    color = (if (isLuxury) lux.textMuted else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.72f)
                )
            }
        }

        // ─── Controls Row ───
        val btnShape = RoundedCornerShape(if (isLuxury) 8.dp else 12.dp)
        val btnBorder = BorderStroke(
            width = if (isLuxury) 1.dp else 0.75.dp,
            color = if (isLuxury) lux.borderHairline else ThemeManager.cardBorderColor
        )
        if (!hasPermission) {
            OutlinedButton(
                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                shape = btnShape,
                border = btnBorder
            ) {
                Text(
                    text = "Grant Microphone Permission",
                    fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isRecording) {
                    Button(
                        onClick = {
                            try {
                                player?.stop()
                                player?.release()
                            } catch (_: Exception) {}
                            player = null
                            isPlaying = false
                            playbackPosition = 0
                            liveAmplitudes.clear()
                            sessionSamples.clear()
                            recordingElapsedMs = 0L

                            val file = File(audioFilePath)
                            file.parentFile?.mkdirs()
                            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                MediaRecorder(context)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaRecorder()
                            }
                            try {
                                mr.apply {
                                    setAudioSource(MediaRecorder.AudioSource.MIC)
                                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                    setOutputFile(file.absolutePath)
                                    prepare()
                                    start()
                                }
                                recorder = mr
                                recordStartTime = System.currentTimeMillis()
                                isRecording = true
                                onRecordingStarted(audioFilePath, recordStartTime)
                            } catch (e: Exception) {
                                try {
                                    mr.release()
                                } catch (_: Exception) {
                                }
                                recorder = null
                                isRecording = false
                                Toast.makeText(
                                    context,
                                    "Could not start recording: ${e.localizedMessage ?: "mic unavailable"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        shape = btnShape,
                        colors = if (isLuxury) {
                            ButtonDefaults.buttonColors(
                                containerColor = lux.accent,
                                contentColor = lux.onAccent
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        elevation = if (isLuxury) ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp) else ButtonDefaults.buttonElevation()
                    ) {
                        Icon(Icons.Rounded.FiberManualRecord, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (fileExists) "Re-record" else "Record",
                            fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            try {
                                recorder?.stop()
                            } catch (_: Exception) {
                            }
                            try {
                                recorder?.release()
                            } catch (_: Exception) {
                            }
                            recorder = null
                            val duration = (System.currentTimeMillis() - recordStartTime).coerceAtLeast(300L)
                            mediaDurationMs = duration
                            val frozen = saveWaveformPeaks(audioFilePath, sessionSamples)
                            savedPeaks = frozen
                            isRecording = false
                            onRecordingStopped(duration)
                        },
                        shape = btnShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        elevation = if (isLuxury) ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp) else ButtonDefaults.buttonElevation()
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Stop",
                            fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default
                        )
                    }
                }

                if (!isRecording && fileExists) {
                    if (!isPlaying) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    player?.release()
                                } catch (_: Exception) {
                                }
                                player = null
                                val mp = MediaPlayer()
                                try {
                                    mp.apply {
                                        setDataSource(audioFilePath)
                                        prepare()
                                        if (duration > 0) mediaDurationMs = duration.toLong()
                                        seekTo(playbackPosition.coerceIn(0, duration.coerceAtLeast(0)))
                                        start()
                                        setOnCompletionListener {
                                            isPlaying = false
                                            playbackPosition = 0
                                            try {
                                                it.release()
                                            } catch (_: Exception) {
                                            }
                                            if (player === it) {
                                                player = null
                                            }
                                        }
                                    }
                                    player = mp
                                    isPlaying = true
                                } catch (e: Exception) {
                                    try {
                                        mp.release()
                                    } catch (_: Exception) {
                                    }
                                    isPlaying = false
                                    Toast.makeText(
                                        context,
                                        "Could not play audio clip",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            shape = btnShape,
                            border = btnBorder
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Play",
                                fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                player?.let {
                                    try {
                                        playbackPosition = it.currentPosition
                                        it.pause()
                                    } catch (_: Exception) {}
                                }
                                isPlaying = false
                            },
                            shape = btnShape,
                            border = btnBorder
                        ) {
                            Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Pause",
                                fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatClockMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs)
}

private fun saveWaveformPeaks(audioPath: String, rawSamples: List<Float>): List<Float> {
    val downsampled = if (rawSamples.isEmpty()) {
        List(VISUALIZER_BAR_COUNT) { 0.16f }
    } else {
        List(VISUALIZER_BAR_COUNT) { barIdx ->
            val start = (barIdx * rawSamples.size) / VISUALIZER_BAR_COUNT
            val end = (((barIdx + 1) * rawSamples.size) / VISUALIZER_BAR_COUNT).coerceAtLeast(start + 1)
            val slice = rawSamples.subList(start.coerceIn(0, rawSamples.size - 1), end.coerceAtMost(rawSamples.size))
            (slice.maxOrNull() ?: 0.16f).coerceIn(0.10f, 1.0f)
        }
    }
    try {
        File("$audioPath.peaks").writeText(downsampled.joinToString(",") { String.format(java.util.Locale.US, "%.3f", it) })
    } catch (_: Exception) {
    }
    return downsampled
}

private fun loadOrSynthesizePeaks(audioPath: String, durationMs: Long): List<Float> {
    if (audioPath.isNotBlank()) {
        val peaksFile = File("$audioPath.peaks")
        if (peaksFile.exists()) {
            try {
                val parsed = peaksFile.readText()
                    .split(',')
                    .mapNotNull { it.trim().toFloatOrNull()?.coerceIn(0.10f, 1.0f) }
                if (parsed.size >= 8) {
                    return if (parsed.size == VISUALIZER_BAR_COUNT) parsed
                    else List(VISUALIZER_BAR_COUNT) { idx -> parsed[(idx * parsed.size) / VISUALIZER_BAR_COUNT] }
                }
            } catch (_: Exception) {
            }
        }
    }
    // Fallback deterministic speech-like contour for older saved clips
    val seed = (audioPath.hashCode() xor durationMs.hashCode()).toLong()
    return List(VISUALIZER_BAR_COUNT) { idx ->
        val wave = abs(sin((idx + (seed and 0xFFL)) * 0.42))
        val envelope = 0.25 + 0.65 * abs(sin(idx * 0.19))
        (wave * envelope).toFloat().coerceIn(0.14f, 0.92f)
    }
}

fun generateAudioFilePath(context: Context, blockId: String): String {
    val dir = File(context.filesDir, "audio")
    dir.mkdirs()
    return File(dir, "$blockId.m4a").absolutePath
}
