package com.notegraph.app.data.local

import androidx.compose.ui.graphics.Color
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.PdfPageAnnotation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 * Shared thread-safe JSON codecs, color parser cache, and date formatters
 * to eliminate per-item / per-frame object allocations in Compose UI & ViewModels.
 */
object NoteJson {
    val codec: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val prettyCodec: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val shortDateTimeFmt = ThreadLocal.withInitial {
        SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
    }

    private val fullDateFmt = ThreadLocal.withInitial {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    }

    private val isoDateFmt = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    // Bounded color parse cache keyed by raw uppercase hex string
    private val colorCache: MutableMap<String, Color> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Color>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?): Boolean {
                return size > 128
            }
        }
    )

    fun formatShortDateTime(timestampMs: Long): String {
        val fmt = shortDateTimeFmt.get() ?: return ""
        return fmt.format(Date(timestampMs))
    }

    fun formatFullDate(timestampMs: Long): String {
        val fmt = fullDateFmt.get() ?: return ""
        return fmt.format(Date(timestampMs))
    }

    fun formatIsoDate(date: Date = Date()): String {
        val fmt = isoDateFmt.get() ?: return ""
        return fmt.format(date)
    }

    fun formatIsoDate(timestampMs: Long): String = formatIsoDate(Date(timestampMs))

    fun decodeBlocks(blocksJson: String): List<NoteBlock> {
        if (blocksJson.isBlank() || blocksJson == "[]") return emptyList()
        return try {
            codec.decodeFromString<List<NoteBlock>>(blocksJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeBlocks(blocks: List<NoteBlock>): String {
        if (blocks.isEmpty()) return "[]"
        return codec.encodeToString(blocks)
    }

    fun decodeStrokes(strokesJson: String): List<InkStroke> {
        if (strokesJson.isBlank() || strokesJson == "[]") return emptyList()
        return try {
            codec.decodeFromString<List<InkStroke>>(strokesJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeStrokes(strokes: List<InkStroke>): String {
        if (strokes.isEmpty()) return "[]"
        return codec.encodeToString(strokes)
    }

    fun decodePdfAnnotations(annotationsJson: String): List<PdfPageAnnotation> {
        if (annotationsJson.isBlank() || annotationsJson == "[]") return emptyList()
        return try {
            codec.decodeFromString<List<PdfPageAnnotation>>(annotationsJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseColorCached(hex: String, fallback: Color = Color.White): Color {
        if (hex.isBlank()) return fallback
        val key = hex.trim().uppercase(Locale.US)
        colorCache[key]?.let { return it }
        val parsed = try {
            Color(android.graphics.Color.parseColor(key))
        } catch (_: Exception) {
            fallback
        }
        colorCache[key] = parsed
        return parsed
    }
}
