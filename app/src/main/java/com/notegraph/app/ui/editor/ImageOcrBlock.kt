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
import androidx.compose.ui.unit.dp
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text("Image & OCR", style = MaterialTheme.typography.titleSmall)
        }

        if (uriString.isBlank()) {
            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
            ) {
                Text("Select Image from Gallery")
            }
        } else {
            Text(
                text = uriString.substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.75.dp, ThemeManager.cardBorderColor)
                ) {
                    Text("Change")
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
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (ocrInProgress) "Scanning…" else "Extract Text")
                }
            }

            ocrError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (!extractedText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                    border = BorderStroke(0.5.dp, ThemeManager.cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Extracted Text",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(extractedText, style = MaterialTheme.typography.bodySmall)
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
    try {
        val uri = Uri.parse(uriString)
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                onResult(Result.success(visionText.text))
            }
            .addOnFailureListener { e ->
                onResult(Result.failure(e))
            }
    } catch (e: Exception) {
        onResult(Result.failure(e))
    }
}
