package com.notegraph.app.data.local.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy of block types that can be serialized into the
 * `blocksJson` column of [NoteEntity] via kotlinx.serialization.
 */
@Serializable
sealed class NoteBlock {
    abstract val id: String

    @Serializable
    @SerialName("text")
    data class TextBlock(
        override val id: String,
        val text: String = "",
        val audioTimestampMs: Long? = null
    ) : NoteBlock()

    @Serializable
    @SerialName("ink")
    data class InkBlock(
        override val id: String,
        val strokesJson: String = "[]",
        val heightDp: Float = 300f,
        val paperTemplate: String = "DEFAULT"
    ) : NoteBlock()

    @Serializable
    @SerialName("audio")
    data class AudioBlock(
        override val id: String,
        val filePath: String = "",
        val durationMs: Long = 0L
    ) : NoteBlock()

    @Serializable
    @SerialName("image")
    data class ImageBlock(
        override val id: String,
        val uriString: String = "",
        val extractedOcrText: String? = null
    ) : NoteBlock()

    @Serializable
    @SerialName("checklist")
    data class ChecklistBlock(
        override val id: String,
        val title: String = "To-Do Checklist",
        val items: List<ChecklistItem> = emptyList()
    ) : NoteBlock()

    @Serializable
    @SerialName("pdf")
    data class PdfBlock(
        override val id: String,
        val filePath: String = "",
        val fileName: String = "",
        val pageCount: Int = 0,
        val currentPage: Int = 0,
        val annotationsJson: String = "[]",
        val invertDark: Boolean = false
    ) : NoteBlock()
}

/**
 * Per-page annotation data stored inside a [NoteBlock.PdfBlock].
 * Ink coordinates are normalized to [0f..1f] relative to page width/height
 * so annotations stay pixel-aligned across inline preview, fullscreen zoom, and PDF export.
 */
@Serializable
data class PdfPageAnnotation(
    val pageIndex: Int,
    val strokes: List<InkStroke> = emptyList(),
    val pageNote: String = ""
)

/**
 * A single checkable item inside a [NoteBlock.ChecklistBlock].
 */
@Serializable
data class ChecklistItem(
    val id: String,
    val text: String = "",
    val isChecked: Boolean = false
)

/**
 * A single stroke point captured by the ink canvas.
 */
@Serializable
data class StrokePoint(
    val x: Float,
    val y: Float
)

/**
 * A complete stroke drawn on the ink canvas.
 */
@Serializable
data class InkStroke(
    val points: List<StrokePoint> = emptyList(),
    val colorHex: String = "#FFFFFF",
    val strokeWidth: Float = 4f,
    val isHighlighter: Boolean = false,
    val isEraser: Boolean = false
)
