package com.notegraph.app.ui.editor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Composable that shows an image from a URI, lets the user pick from gallery,
 * and provides an "Extract Text (OCR)" button using ML Kit.
 *
 * @param uriString         current image URI string (may be empty).
 * @param extractedText     previously extracted OCR text (if any).
 * @param onImagePicked     callback when a new image is selected from gallery.
 * @param onOcrResult       callback when OCR completes with recognised text.
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
            .padding(8.dp)
    ) {
        Text("🖼 Image Block", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))

        if (uriString.isBlank()) {
            Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                Text("Pick Image from Gallery")
            }
        } else {
            Text(
                "Image: ${uriString.substringAfterLast('/')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Text("Change Image")
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
                    enabled = !ocrInProgress
                ) {
                    Text(if (ocrInProgress) "Recognising…" else "Extract Text (OCR)")
                }
            }

            ocrError?.let {
                Spacer(Modifier.height(4.dp))
                Text("Error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (!extractedText.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Extracted Text:", style = MaterialTheme.typography.labelMedium)
                Text(extractedText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Runs ML Kit text recognition on an image URI and returns the result
 * via [onResult] callback.
 */
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
