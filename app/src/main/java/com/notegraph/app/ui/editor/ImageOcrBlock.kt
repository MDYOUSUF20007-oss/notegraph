package com.notegraph.app.ui.editor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.notegraph.app.ui.theme.ThemeManager

/**
 * Minimalist Image + ML Kit offline OCR block.
 */
@Composable
fun ImageOcrBlock(
    uriString: String,
    extractedText: String?,
    onImagePicked: (Uri) -> Unit,
    onOcrResult: (String) -> Unit
) {
    val context = LocalContext.current
    var ocrInProgress by remember { mutableStateOf(false) }
    var ocrError by remember { mutableStateOf<String?>(null) }
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens
    val btnShape = RoundedCornerShape(if (isLuxury) 8.dp else 12.dp)
    val btnBorder = BorderStroke(
        width = if (isLuxury) 1.dp else 0.75.dp,
        color = if (isLuxury) lux.borderHairline else ThemeManager.cardBorderColor
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImagePicked(it) }
    }

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
                imageVector = Icons.Rounded.Image,
                contentDescription = null,
                tint = if (isLuxury) lux.accent else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text("Image & OCR", style = MaterialTheme.typography.titleSmall)
        }

        if (uriString.isBlank()) {
            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                shape = btnShape,
                border = btnBorder
            ) {
                Text(
                    text = "Select Image from Gallery",
                    fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default
                )
            }
        } else {
            Text(
                text = uriString.substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                fontSize = if (isLuxury) 11.sp else MaterialTheme.typography.bodySmall.fontSize,
                color = if (isLuxury) lux.textMuted else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    shape = btnShape,
                    border = btnBorder
                ) {
                    Text(
                        text = "Change",
                        fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default
                    )
                }
                Button(
                    onClick = {
                        ocrInProgress = true
                        ocrError = null
                        runOcr(context, uriString) { result ->
                            ocrInProgress = false
                            result.onSuccess { text -> onOcrResult(text) }
                            result.onFailure { e -> ocrError = e.localizedMessage ?: "OCR failed" }
                        }
                    },
                    enabled = !ocrInProgress,
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
                    Icon(Icons.Rounded.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (ocrInProgress) "Scanning…" else "Extract Text",
                        fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default
                    )
                }
            }

            ocrError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (!extractedText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(if (isLuxury) 6.dp else 10.dp),
                    color = if (isLuxury) lux.recessedSurface else MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                    border = BorderStroke(if (isLuxury) 1.dp else 0.5.dp, if (isLuxury) lux.borderHairline else ThemeManager.cardBorderColor),
                    shadowElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (isLuxury) "EXTRACTED TEXT" else "Extracted Text",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                            fontSize = if (isLuxury) 10.sp else MaterialTheme.typography.labelSmall.fontSize,
                            color = if (isLuxury) lux.accent else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = extractedText,
                            style = if (isLuxury) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                            color = if (isLuxury) lux.textPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun runOcr(
    context: Context,
    uriString: String,
    onResult: (Result<String>) -> Unit
) {
    var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
    try {
        val uri = Uri.parse(uriString)
        val image = InputImage.fromFilePath(context, uri)
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                try {
                    recognizer.close()
                } catch (_: Exception) {
                }
                onResult(Result.success(visionText.text))
            }
            .addOnFailureListener { e ->
                try {
                    recognizer.close()
                } catch (_: Exception) {
                }
                onResult(Result.failure(e))
            }
    } catch (e: Exception) {
        try {
            recognizer?.close()
        } catch (_: Exception) {
        }
        onResult(Result.failure(e))
    }
}
