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
        val heightDp: Float = 300f
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
}

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
