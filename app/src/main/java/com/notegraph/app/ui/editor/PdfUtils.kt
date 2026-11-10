package com.notegraph.app.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.notegraph.app.data.local.NoteJson
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.PdfPageAnnotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume

data class ImportedPdfInfo(
    val filePath: String,
    val fileName: String,
    val pageCount: Int
)

/**
 * Thread-safe offline PDF importer and PdfRenderer page rasterizer.
 * Copies SAF-picked PDFs into internal filesDir/pdfs so permissions never expire,
 * and serializes PdfRenderer access to respect Android's single-open-page constraint.
 */
object PdfHelper {
    private val renderLock = Any()

    // Bounded in-memory bitmap LRU cache (up to 24 MB or 1/8 of heap) for instant page flipping
    private val maxCacheKb: Int = minOf(
        (Runtime.getRuntime().maxMemory() / 1024L).toInt() / 8,
        24 * 1024
    ).coerceAtLeast(4 * 1024)

    private val pageBitmapCache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    fun parseAnnotations(annotationsJson: String): List<PdfPageAnnotation> {
        return NoteJson.decodePdfAnnotations(annotationsJson)
    }

    fun encodeAnnotations(list: List<PdfPageAnnotation>): String {
        val cleaned = list.filter { it.strokes.isNotEmpty() || it.pageNote.isNotBlank() }
            .sortedBy { it.pageIndex }
        if (cleaned.isEmpty()) return "[]"
        return NoteJson.codec.encodeToString(cleaned)
    }

    fun getDisplayName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) result = cursor.getString(idx)
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (result.isNullOrBlank()) {
            result = uri.lastPathSegment?.substringAfterLast('/')
        }
        val clean = (result ?: "Document.pdf").trim()
        return if (clean.endsWith(".pdf", ignoreCase = true)) clean else "$clean.pdf"
    }

    fun importPdfFromUri(context: Context, uri: Uri): ImportedPdfInfo? {
        return try {
            val pdfDir = File(context.filesDir, "pdfs")
            pdfDir.mkdirs()
            val fileName = getDisplayName(context, uri)
            val destFile = File(pdfDir, "${UUID.randomUUID()}.pdf")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (!destFile.exists() || destFile.length() == 0L) {
                destFile.delete()
                return null
            }

            val pageCount = getPageCount(destFile.absolutePath)
            if (pageCount <= 0) {
                destFile.delete()
                return null
            }

            ImportedPdfInfo(
                filePath = destFile.absolutePath,
                fileName = fileName,
                pageCount = pageCount
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getPageCount(filePath: String): Int {
        if (filePath.isBlank()) return 0
        val file = File(filePath)
        if (!file.exists()) return 0
        return synchronized(renderLock) {
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        renderer.pageCount
                    }
                }
            } catch (_: Exception) {
                0
            }
        }
    }

    fun renderPageBitmap(filePath: String, pageIndex: Int, targetWidthPx: Int = 1080): Bitmap? {
        if (filePath.isBlank()) return null
        val file = File(filePath)
        if (!file.exists()) return null

        return synchronized(renderLock) {
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
                        renderer.openPage(pageIndex).use { page ->
                            val safeTargetWidth = targetWidthPx.coerceIn(320, 1800)
                            val aspect = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                            val bmpW = safeTargetWidth
                            val bmpH = (bmpW * aspect).toInt().coerceIn(200, 3200)
                            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            // PDF pages render transparently by default; paint crisp white paper backing
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Cached page rasterizer for interactive viewer UI. Never recycle the returned bitmap.
     */
    fun renderPageBitmapCached(filePath: String, pageIndex: Int, targetWidthPx: Int = 1080): Bitmap? {
        if (filePath.isBlank()) return null
        val safeTargetWidth = targetWidthPx.coerceIn(320, 1800)
        val key = "$filePath#$pageIndex#$safeTargetWidth"
        pageBitmapCache.get(key)?.let { cached ->
            if (!cached.isRecycled) return cached
            pageBitmapCache.remove(key)
        }
        val rendered = renderPageBitmap(filePath, pageIndex, safeTargetWidth) ?: return null
        pageBitmapCache.put(key, rendered)
        return rendered
    }

    fun clearPdfCache(filePath: String? = null) {
        if (filePath == null) {
            pageBitmapCache.evictAll()
        } else {
            val prefix = "$filePath#"
            val keysToRemove = pageBitmapCache.snapshot().keys.filter { it.startsWith(prefix) }
            for (k in keysToRemove) {
                pageBitmapCache.remove(k)
            }
        }
    }

    /**
     * Composites normalized [0..1] PDF ink annotations onto an Android Canvas region.
     */
    fun drawNormalizedStrokesOnCanvas(
        canvas: Canvas,
        strokes: List<InkStroke>,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        if (strokes.isEmpty() || width <= 0f || height <= 0f) return
        val inkPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        val reusablePath = Path()
        val widthScale = width / 1000f

        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            inkPaint.strokeWidth = (stroke.strokeWidth * widthScale.coerceAtLeast(0.6f)).coerceAtLeast(1.2f)
            inkPaint.alpha = if (stroke.isHighlighter) 105 else 255
            inkPaint.color = try {
                Color.parseColor(stroke.colorHex)
            } catch (_: Exception) {
                Color.BLACK
            }

            reusablePath.rewind()
            reusablePath.moveTo(
                left + stroke.points[0].x * width,
                top + stroke.points[0].y * height
            )
            for (i in 1 until stroke.points.size) {
                reusablePath.lineTo(
                    left + stroke.points[i].x * width,
                    top + stroke.points[i].y * height
                )
            }
            canvas.drawPath(reusablePath, inkPaint)
        }
    }

    /**
     * Runs offline ML Kit text recognition on a single rasterized PDF page.
     * Renders on [Dispatchers.IO], extracts structured text blocks, and recycles the bitmap.
     */
    suspend fun ocrPdfPageBlocks(
        filePath: String,
        pageIndex: Int,
        targetWidthPx: Int = 1500,
        sharedRecognizer: TextRecognizer? = null
    ): List<String> {
        val bitmap = withContext(Dispatchers.IO) {
            renderPageBitmap(filePath, pageIndex, targetWidthPx)
        } ?: return emptyList()

        val ownsRecognizer = sharedRecognizer == null
        val recognizer = sharedRecognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        return try {
            suspendCancellableCoroutine { cont ->
                try {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val blocks = visionText.textBlocks
                                .map { it.text.trim() }
                                .filter { it.isNotBlank() }
                            val result = if (blocks.isNotEmpty()) {
                                blocks
                            } else {
                                visionText.text.trim()
                                    .takeIf { it.isNotBlank() }
                                    ?.let { listOf(it) }
                                    ?: emptyList()
                            }
                            if (cont.isActive) cont.resume(result)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(emptyList())
                        }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(emptyList())
                }
            }
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            if (ownsRecognizer) {
                try {
                    recognizer.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun createOcrRecognizer(): TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
}
