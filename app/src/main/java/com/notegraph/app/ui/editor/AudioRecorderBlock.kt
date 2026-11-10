package com.notegraph.app.ui.editor

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * A self-contained composable that records audio to `.m4a`, plays it back,
 * and handles runtime RECORD_AUDIO permission.
 *
 * @param audioFilePath   the full path to the audio file (may not exist yet).
 * @param recordingStartMs system-clock millis when recording began (set by caller).
 * @param onRecordingStarted callback fired when recording actually starts, passing the path
 *                           and `System.currentTimeMillis()` at that instant.
 * @param onRecordingStopped callback fired when recording stops, passing the duration in ms.
 * @param onSeekRequest      callback fired when the user taps a text block recorded during
 *                           audio capture; the parent should call [seekTo] on the player.
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

    // Seek & play from timestamped text block taps
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

    // Check permission on first composition
    LaunchedEffect(Unit) {
        hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(Unit) {
        onDispose {
            try { recorder?.release() } catch (e: Exception) {}
            try { player?.release() } catch (e: Exception) {}
        }
    }

    val fileExists = remember(audioFilePath) { File(audioFilePath).exists() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text("🎙 Audio Block", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))

        if (!hasPermission) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Grant Microphone Permission")
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isRecording) {
                    Button(onClick = {
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
                    }) {
                        Text("⏺ Record")
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("⏹ Stop")
                    }
                }

                if ((fileExists || !isRecording) && File(audioFilePath).exists()) {
                    if (!isPlaying) {
                        OutlinedButton(onClick = {
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
                        }) {
                            Text("▶ Play")
                        }
                    } else {
                        OutlinedButton(onClick = {
                            player?.let {
                                playbackPosition = it.currentPosition
                                it.pause()
                            }
                            isPlaying = false
                        }) {
                            Text("⏸ Pause")
                        }
                    }
                }
            }

            if (durationMs > 0) {
                Text(
                    "Duration: ${durationMs / 1000}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * Utility to generate a unique audio file path inside the app's files directory.
 */
fun generateAudioFilePath(context: Context, blockId: String): String {
    val dir = File(context.filesDir, "audio")
    dir.mkdirs()
    return File(dir, "$blockId.m4a").absolutePath
}
