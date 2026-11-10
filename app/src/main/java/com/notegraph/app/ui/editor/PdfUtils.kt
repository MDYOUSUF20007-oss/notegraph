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
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.PdfPageAnnotation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

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
    private val json = Json { ignoreUnknownKeys = true }
    private val renderLock = Any()

    fun parseAnnotations(annotationsJson: String): List<PdfPageAnnotation> {
        if (annotationsJson.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<PdfPageAnnotation>>(annotationsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun encodeAnnotations(list: List<PdfPageAnnotation>): String {
        val cleaned = list.filter { it.strokes.isNotEmpty() || it.pageNote.isNotBlank() }
            .sortedBy { it.pageIndex }
        return json.encodeToString(cleaned)
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

            val path = Path()
            path.moveTo(
                left + stroke.points[0].x * width,
                top + stroke.points[0].y * height
            )
            for (i in 1 until stroke.points.size) {
                path.lineTo(
                    left + stroke.points[i].x * width,
                    top + stroke.points[i].y * height
                )
            }
            canvas.drawPath(path, inkPaint)
        }
    }
}
