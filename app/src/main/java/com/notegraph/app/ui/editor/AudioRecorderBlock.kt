package com.notegraph.app.ui.editor

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.notegraph.app.ui.theme.ThemeManager
import java.io.File

/**
 * Self-contained minimalist composable that records audio to `.m4a` and plays it back.
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
    var playbackPosition by remember { mutableIntStateOf(0) }

    LaunchedEffect(seekToRequestMs, audioFilePath) {
        val targetMs = seekToRequestMs
        if (targetMs != null && !isRecording && File(audioFilePath).exists()) {
            try {
                player?.release()
                val mp = MediaPlayer().apply {
                    setDataSource(audioFilePath)
                    prepare()
                    val clamped = targetMs.toInt().coerceIn(0, duration.coerceAtLeast(0))
                    seekTo(clamped)
                    start()
                    setOnCompletionListener {
                        isPlaying = false
                        playbackPosition = 0
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

    val fileExists = remember(audioFilePath) { File(audioFilePath).exists() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text("Voice Recording", style = MaterialTheme.typography.titleSmall)
            if (durationMs > 0) {
                Text(
                    text = "· ${durationMs / 1000}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!hasPermission) {
            OutlinedButton(
                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
            ) {
                Text("Grant Microphone Permission")
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isRecording) {
                    Button(
                        onClick = {
                            val file = File(audioFilePath)
                            file.parentFile?.mkdirs()
                            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                MediaRecorder(context)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaRecorder()
                            }
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
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.FiberManualRecord, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Record")
                    }
                } else {
                    Button(
                        onClick = {
                            recorder?.apply {
                                stop()
                                release()
                            }
                            recorder = null
                            val duration = System.currentTimeMillis() - recordStartTime
                            isRecording = false
                            onRecordingStopped(duration)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                }

                if ((fileExists || !isRecording) && File(audioFilePath).exists()) {
                    if (!isPlaying) {
                        OutlinedButton(
                            onClick = {
                                val mp = MediaPlayer().apply {
                                    setDataSource(audioFilePath)
                                    prepare()
                                    seekTo(playbackPosition)
                                    start()
                                    setOnCompletionListener {
                                        isPlaying = false
                                        playbackPosition = 0
                                    }
                                }
                                player = mp
                                isPlaying = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                player?.let {
                                    playbackPosition = it.currentPosition
                                    it.pause()
                                }
                                isPlaying = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                        ) {
                            Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                    }
                }
            }
        }
    }
}

fun generateAudioFilePath(context: Context, blockId: String): String {
    val dir = File(context.filesDir, "audio")
    dir.mkdirs()
    return File(dir, "$blockId.m4a").absolutePath
}
