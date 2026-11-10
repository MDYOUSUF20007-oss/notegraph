package com.notegraph.app.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.ui.theme.AppThemeMode
import com.notegraph.app.ui.theme.ThemeManager
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/**
 * Remade OpenXML (.pptx) slide deck generator built for strict compatibility with
 * Microsoft PowerPoint (desktop, web, mobile) and Apple Keynote (macOS, iOS, iPadOS).
 *
 * Includes complete PresentationML hierarchy (slideMaster, slideLayout, DrawingML theme,
 * presProps, viewProps, tableStyles, per-slide `.rels`, valid `grpSpPr` transforms, XML control
 * character sanitization, auto-paginated cards, and embedded PNG media for sketches, images, and PDFs).
 */
object PptxExporter {

    private val json = Json { ignoreUnknownKeys = true }

    // Widescreen 16:9 canvas in EMUs (13.333" x 7.5" at 914400 EMU/inch)
    private const val SLIDE_W = 12192000L
    private const val SLIDE_H = 6858000L

    private data class DeckPalette(
        val bgHex: String,
        val surfaceHex: String,
        val borderHex: String,
        val titleHex: String,
        val bodyHex: String,
        val mutedHex: String,
        val accentStartHex: String,
        val accentEndHex: String
    )

    private data class EmbeddedMedia(
        val fileName: String,
        val bytes: ByteArray,
        val pixelW: Int,
        val pixelH: Int
    )

    private sealed class DeckSlide {
        abstract val kicker: String
        abstract val title: String

        data class Cover(
            override val kicker: String,
            override val title: String,
            val summaryLines: List<String>,
            val footerMeta: String
        ) : DeckSlide()

        data class BulletCard(
            override val kicker: String,
            override val title: String,
            val lines: List<String>,
            val highlightFirstLine: Boolean = false
        ) : DeckSlide()

        data class MediaVisual(
            override val kicker: String,
            override val title: String,
            val media: EmbeddedMedia,
            val sideTitle: String,
            val sideLines: List<String>
        ) : DeckSlide()
    }

    fun export(context: Context?, note: NoteEntity, outputStream: OutputStream) {
        val palette = resolvePalette()
        val mediaList = mutableListOf<EmbeddedMedia>()
        val slides = buildSlides(context, note, mediaList)

        ZipOutputStream(outputStream).use { zos ->
            // 1. [Content_Types].xml
            writeEntry(zos, "[Content_Types].xml", buildContentTypesXml(slides.size, mediaList.isNotEmpty()))

            // 2. _rels/.rels
            writeEntry(zos, "_rels/.rels", ROOT_RELS_XML)

            // 3. Presentation properties, view properties, table styles, and theme
            writeEntry(zos, "ppt/presProps.xml", PRES_PROPS_XML)
            writeEntry(zos, "ppt/viewProps.xml", VIEW_PROPS_XML)
            writeEntry(zos, "ppt/tableStyles.xml", TABLE_STYLES_XML)
            writeEntry(zos, "ppt/theme/theme1.xml", buildThemeXml(palette))

            // 4. SlideMaster & SlideLayout (strictly required by Keynote & PowerPoint)
            writeEntry(zos, "ppt/slideMasters/slideMaster1.xml", buildSlideMasterXml(palette))
            writeEntry(zos, "ppt/slideMasters/_rels/slideMaster1.xml.rels", SLIDE_MASTER_RELS_XML)
            writeEntry(zos, "ppt/slideLayouts/slideLayout1.xml", SLIDE_LAYOUT_XML)
            writeEntry(zos, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", SLIDE_LAYOUT_RELS_XML)

            // 5. presentation.xml & presentation.xml.rels
            writeEntry(zos, "ppt/presentation.xml", buildPresentationXml(slides.size))
            writeEntry(zos, "ppt/_rels/presentation.xml.rels", buildPresentationRelsXml(slides.size))

            // 6. Embedded PNG media bytes
            for (media in mediaList) {
                zos.putNextEntry(ZipEntry("ppt/media/${media.fileName}"))
                zos.write(media.bytes)
                zos.closeEntry()
            }

            // 7. Individual Slide XML + per-slide `.rels`
            slides.forEachIndexed { index, slide ->
                val slideNumber = index + 1
                var slideMediaRelId: String? = null
                val relsSb = StringBuilder()
                relsSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                relsSb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                relsSb.append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>""")
                if (slide is DeckSlide.MediaVisual) {
                    slideMediaRelId = "rId2"
                    relsSb.append("""<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/${slide.media.fileName}"/>""")
                }
                relsSb.append("""</Relationships>""")

                writeEntry(zos, "ppt/slides/_rels/slide${slideNumber}.xml.rels", relsSb.toString())
                writeEntry(
                    zos,
                    "ppt/slides/slide${slideNumber}.xml",
                    buildSlideXml(slide, slideNumber, slides.size, palette, slideMediaRelId)
                )
            }
        }
    }

    private fun resolvePalette(): DeckPalette {
        val mode = ThemeManager.currentTheme
        val preset = ThemeManager.currentGradient
        val accentStart = colorToHex6(preset.accentStart)
        val accentEnd = colorToHex6(preset.accentEnd)

        return when (mode) {
            AppThemeMode.KINDLE_PARCHMENT -> DeckPalette(
                bgHex = colorToHex6(preset.backgroundColors.firstOrNull() ?: androidx.compose.ui.graphics.Color(0xFFF7F4EC)),
                surfaceHex = "FFFFFF",
                borderHex = colorToHex6(preset.borderColor),
                titleHex = "1C1917",
                bodyHex = "332E28",
                mutedHex = "6B655D",
                accentStartHex = accentStart,
                accentEndHex = accentEnd
            )
            AppThemeMode.MATTE_STUDIO -> DeckPalette(
                bgHex = colorToHex6(preset.backgroundColors.firstOrNull() ?: androidx.compose.ui.graphics.Color(0xFF1A2230)),
                surfaceHex = colorToHex6(preset.cardSurface),
                borderHex = colorToHex6(preset.borderColor),
                titleHex = "F8FAFC",
                bodyHex = "E2E8F0",
                mutedHex = "94A3B8",
                accentStartHex = accentStart,
                accentEndHex = accentEnd
            )
            AppThemeMode.DEEP_PURPLE_AMOLED -> DeckPalette(
                bgHex = "0B0714",
                surfaceHex = colorToHex6(preset.cardSurface),
                borderHex = colorToHex6(preset.borderColor),
                titleHex = "F8FAFC",
                bodyHex = "E5E7EB",
                mutedHex = "9CA3AF",
                accentStartHex = accentStart,
                accentEndHex = accentEnd
            )
        }
    }

    private fun buildSlides(
        context: Context?,
        note: NoteEntity,
        mediaSink: MutableList<EmbeddedMedia>
    ): List<DeckSlide> {
        val slides = mutableListOf<DeckSlide>()
        val blocks = ExportManager.parseBlocks(note)
        val dateText = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(note.updatedAt))
        val cleanTitle = note.title.trim().ifBlank { "Untitled Note" }

        // Prepare main text lines wrapped nicely for slide layout
        val mainBodyRawLines = note.content
            .replace("\r\n", "\n")
            .split("\n")
            .flatMap { wrapLine(it, 78) }

        val firstTextFallback = blocks.filterIsInstance<NoteBlock.TextBlock>()
            .firstOrNull { it.text.isNotBlank() }
            ?.text
            ?.replace("\r\n", "\n")
            ?.split("\n")
            ?.flatMap { wrapLine(it, 78) }
            .orEmpty()

        val coverExcerpt = if (mainBodyRawLines.any { it.isNotBlank() }) {
            mainBodyRawLines.filter { it.isNotBlank() }.take(5)
        } else {
            firstTextFallback.filter { it.isNotBlank() }.take(5)
        }

        val blockCountLabel = if (blocks.isEmpty()) "Note Summary" else "${blocks.size} content block${if (blocks.size == 1) "" else "s"}"
        slides.add(
            DeckSlide.Cover(
                kicker = "NOTEGRAPH PRESENTATION",
                title = cleanTitle,
                summaryLines = coverExcerpt.ifEmpty {
                    listOf("Exported from NoteGraph Knowledge Vault.")
                },
                footerMeta = "$dateText  •  $blockCountLabel"
            )
        )

        // Paginate full note content across dedicated Notes slides if content exceeds cover preview
        val nonBlankMainLines = mainBodyRawLines.filter { it.isNotBlank() }
        if (nonBlankMainLines.size > 5 || (nonBlankMainLines.isNotEmpty() && blocks.isEmpty())) {
            val pages = nonBlankMainLines.chunked(11)
            pages.forEachIndexed { pIdx, chunk ->
                val partSuffix = if (pages.size > 1) " (${pIdx + 1}/${pages.size})" else ""
                slides.add(
                    DeckSlide.BulletCard(
                        kicker = "NOTE CONTENT",
                        title = "$cleanTitle$partSuffix",
                        lines = chunk
                    )
                )
            }
        }

        var mediaCounter = 1

        // Build slides per note block
        blocks.forEachIndexed { idx, block ->
            when (block) {
                is NoteBlock.TextBlock -> {
                    val wrapped = block.text.replace("\r\n", "\n")
                        .split("\n")
                        .flatMap { wrapLine(it, 78) }
                        .filter { it.isNotBlank() }
                    if (wrapped.isNotEmpty()) {
                        val pages = wrapped.chunked(11)
                        pages.forEachIndexed { pIdx, chunk ->
                            val part = if (pages.size > 1) " (${pIdx + 1}/${pages.size})" else ""
                            val audioTag = block.audioTimestampMs?.let { "  [Audio @ ${it / 1000}s]" } ?: ""
                            slides.add(
                                DeckSlide.BulletCard(
                                    kicker = "SECTION ${idx + 1}$audioTag",
                                    title = "Notes$part",
                                    lines = chunk
                                )
                            )
                        }
                    }
                }

                is NoteBlock.ChecklistBlock -> {
                    val listTitle = block.title.ifBlank { "To-Do Checklist" }
                    val doneCount = block.items.count { it.isChecked }
                    val itemsFormatted = block.items.map { item ->
                        val prefix = if (item.isChecked) "☑" else "☐"
                        "$prefix  ${item.text.ifBlank { "(empty item)" }}"
                    }.ifEmpty { listOf("(No tasks added yet)") }

                    val chunks = itemsFormatted.chunked(10)
                    chunks.forEachIndexed { pIdx, chunk ->
                        val part = if (chunks.size > 1) " (${pIdx + 1}/${chunks.size})" else ""
                        slides.add(
                            DeckSlide.BulletCard(
                                kicker = "CHECKLIST • $doneCount/${block.items.size} COMPLETED",
                                title = "$listTitle$part",
                                lines = chunk
                            )
                        )
                    }
                }

                is NoteBlock.InkBlock -> {
                    val strokes = try {
                        json.decodeFromString<List<InkStroke>>(block.strokesJson)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val renderedPng = renderInkToPngSafely(strokes)
                    if (renderedPng != null) {
                        val media = EmbeddedMedia(
                            fileName = "image${mediaCounter++}.png",
                            bytes = renderedPng.first,
                            pixelW = renderedPng.second,
                            pixelH = renderedPng.third
                        )
                        mediaSink.add(media)
                        slides.add(
                            DeckSlide.MediaVisual(
                                kicker = "HANDWRITTEN SKETCH",
                                title = "Ink Diagram (${strokes.size} strokes)",
                                media = media,
                                sideTitle = "Canvas Details",
                                sideLines = listOf(
                                    "Total strokes: ${strokes.size}",
                                    "Template: ${block.paperTemplate}",
                                    "Rendered directly from vector ink"
                                )
                            )
                        )
                    } else {
                        slides.add(
                            DeckSlide.BulletCard(
                                kicker = "HANDWRITTEN SKETCH",
                                title = "Ink Canvas (${strokes.size} strokes)",
                                lines = listOf(
                                    "Handwritten sketch captured inside note",
                                    "Total ink strokes: ${strokes.size}",
                                    "Paper template: ${block.paperTemplate}"
                                )
                            )
                        )
                    }
                }

                is NoteBlock.ImageBlock -> {
                    val decoded = if (context != null && block.uriString.isNotBlank()) {
                        loadUriImageAsPngSafely(context, block.uriString)
                    } else null
                    val ocrLines = block.extractedOcrText
                        ?.replace("\r\n", "\n")
                        ?.split("\n")
                        ?.flatMap { wrapLine(it, 42) }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()

                    if (decoded != null) {
                        val media = EmbeddedMedia(
                            fileName = "image${mediaCounter++}.png",
                            bytes = decoded.first,
                            pixelW = decoded.second,
                            pixelH = decoded.third
                        )
                        mediaSink.add(media)
                        slides.add(
                            DeckSlide.MediaVisual(
                                kicker = "IMAGE & OCR",
                                title = "Embedded Image",
                                media = media,
                                sideTitle = if (ocrLines.isNotEmpty()) "Extracted Text (OCR)" else "Attachment Details",
                                sideLines = ocrLines.take(12).ifEmpty {
                                    listOf("High-resolution image attachment", "Source: ${block.uriString.substringAfterLast('/')}")
                                }
                            )
                        )
                        if (ocrLines.size > 12) {
                            ocrLines.drop(12).chunked(11).forEachIndexed { extraIdx, extraChunk ->
                                slides.add(
                                    DeckSlide.BulletCard(
                                        kicker = "IMAGE OCR (CONT. ${extraIdx + 2})",
                                        title = "Extracted Image Text",
                                        lines = extraChunk
                                    )
                                )
                            }
                        }
                    } else {
                        val fallbackLines = mutableListOf("Embedded Image Attachment")
                        if (ocrLines.isNotEmpty()) {
                            fallbackLines.add("Extracted OCR Text:")
                            fallbackLines.addAll(ocrLines.take(10))
                        }
                        slides.add(
                            DeckSlide.BulletCard(
                                kicker = "IMAGE & OCR",
                                title = "Image Attachment",
                                lines = fallbackLines
                            )
                        )
                    }
                }

                is NoteBlock.AudioBlock -> {
                    val secs = (block.durationMs / 1000L).coerceAtLeast(0L)
                    val mins = secs / 60L
                    val rem = secs % 60L
                    val clock = String.format(Locale.US, "%02d:%02d", mins, rem)
                    val stampedNotes = blocks.filterIsInstance<NoteBlock.TextBlock>()
                        .filter { it.audioTimestampMs != null && it.text.isNotBlank() }
                        .take(7)
                        .map { tb -> "[${(tb.audioTimestampMs ?: 0L) / 1000L}s]  ${tb.text.lines().firstOrNull().orEmpty()}" }
                    val lines = mutableListOf(
                        "Voice Memo Duration: $clock ($secs seconds)",
                        "Recording Format: AAC Audio (.m4a)"
                    )
                    if (stampedNotes.isNotEmpty()) {
                        lines.add("Timestamped Note Markers:")
                        lines.addAll(stampedNotes)
                    }
                    slides.add(
                        DeckSlide.BulletCard(
                            kicker = "VOICE RECORDING",
                            title = "Audio Memo ($clock)",
                            lines = lines
                        )
                    )
                }

                is NoteBlock.PdfBlock -> {
                    val docName = block.fileName.ifBlank { "Document.pdf" }
                    val totalPages = block.pageCount.coerceAtLeast(1)
                    val annotations = PdfHelper.parseAnnotations(block.annotationsJson)
                    val annByPage = annotations.associateBy { it.pageIndex }

                    if (context != null && block.filePath.isNotBlank()) {
                        val priorityPages = annotations
                            .filter { it.strokes.isNotEmpty() || it.pageNote.isNotBlank() }
                            .map { it.pageIndex }
                        val targetPages = (priorityPages + (0 until totalPages).toList())
                            .distinct()
                            .filter { it in 0 until totalPages }
                            .sorted()
                            .take(8)

                        var anyRendered = false
                        for (pIdx in targetPages) {
                            val pageAnn = annByPage[pIdx]
                            val pagePng = renderPdfPageWithMarkupSafely(block.filePath, pIdx, pageAnn?.strokes.orEmpty())
                            if (pagePng != null) {
                                anyRendered = true
                                val media = EmbeddedMedia(
                                    fileName = "image${mediaCounter++}.png",
                                    bytes = pagePng.first,
                                    pixelW = pagePng.second,
                                    pixelH = pagePng.third
                                )
                                mediaSink.add(media)
                                val noteLines = pageAnn?.pageNote
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { wrapLine(it, 42) }
                                    .orEmpty()
                                val sideLines = mutableListOf(
                                    "Document: $docName",
                                    "Page ${pIdx + 1} of $totalPages",
                                    "Markup strokes: ${pageAnn?.strokes?.size ?: 0}"
                                )
                                if (noteLines.isNotEmpty()) {
                                    sideLines.add("Margin Note:")
                                    sideLines.addAll(noteLines.take(8))
                                }
                                slides.add(
                                    DeckSlide.MediaVisual(
                                        kicker = "PDF DOCUMENT & MARKUP",
                                        title = "$docName — Page ${pIdx + 1}",
                                        media = media,
                                        sideTitle = "Page ${pIdx + 1} Notes",
                                        sideLines = sideLines
                                    )
                                )
                            }
                        }

                        if (!anyRendered) {
                            slides.add(
                                DeckSlide.BulletCard(
                                    kicker = "PDF ATTACHMENT",
                                    title = docName,
                                    lines = buildPdfSummaryLines(docName, totalPages, annotations)
                                )
                            )
                        } else if (totalPages > targetPages.size) {
                            slides.add(
                                DeckSlide.BulletCard(
                                    kicker = "PDF APPENDIX",
                                    title = "$docName — Overview",
                                    lines = buildPdfSummaryLines(docName, totalPages, annotations)
                                )
                            )
                        }
                    } else {
                        slides.add(
                            DeckSlide.BulletCard(
                                kicker = "PDF ATTACHMENT",
                                title = docName,
                                lines = buildPdfSummaryLines(docName, totalPages, annotations)
                            )
                        )
                    }
                }
            }
        }

        return slides
    }

    private fun buildPdfSummaryLines(
        docName: String,
        totalPages: Int,
        annotations: List<com.notegraph.app.data.local.entity.PdfPageAnnotation>
    ): List<String> {
        val lines = mutableListOf(
            "File: $docName",
            "Total pages: $totalPages",
            "Annotated pages: ${annotations.count { it.strokes.isNotEmpty() || it.pageNote.isNotBlank() }}"
        )
        annotations.take(8).forEach { ann ->
            if (ann.strokes.isNotEmpty() || ann.pageNote.isNotBlank()) {
                val notePart = if (ann.pageNote.isNotBlank()) " — ${ann.pageNote}" else ""
                lines.add("Page ${ann.pageIndex + 1}: ${ann.strokes.size} ink stroke(s)$notePart")
            }
        }
        return lines
    }

    // ─── Bitmap Helpers (Safe for headless JVM unit tests) ──────

    private fun renderInkToPngSafely(strokes: List<InkStroke>): Triple<ByteArray, Int, Int>? {
        val allPts = strokes.flatMap { it.points }
        if (allPts.isEmpty()) return null
        return try {
            val w = 1280
            val h = 760
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)

            val pad = 56f
            val minX = allPts.minOf { it.x }
            val maxX = allPts.maxOf { it.x }
            val minY = allPts.minOf { it.y }
            val maxY = allPts.maxOf { it.y }
            val rawW = (maxX - minX).coerceAtLeast(10f)
            val rawH = (maxY - minY).coerceAtLeast(10f)
            val scale = minOf((w - pad * 2f) / rawW, (h - pad * 2f) / rawH)
            val ox = (w - rawW * scale) / 2f
            val oy = (h - rawH * scale) / 2f

            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }

            for (s in strokes) {
                if (s.points.size < 2) continue
                paint.strokeWidth = (s.strokeWidth * scale).coerceIn(2.5f, 24f)
                paint.alpha = if (s.isHighlighter) 115 else 255
                val hex = if (s.colorHex.equals("#FFFFFF", ignoreCase = true)) "#111827" else s.colorHex
                paint.color = try {
                    Color.parseColor(hex)
                } catch (_: Exception) {
                    Color.rgb(17, 24, 39)
                }
                val path = Path().apply {
                    moveTo(ox + (s.points[0].x - minX) * scale, oy + (s.points[0].y - minY) * scale)
                    for (i in 1 until s.points.size) {
                        lineTo(ox + (s.points[i].x - minX) * scale, oy + (s.points[i].y - minY) * scale)
                    }
                }
                canvas.drawPath(path, paint)
            }

            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 95, baos)
            bmp.recycle()
            Triple(baos.toByteArray(), w, h)
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadUriImageAsPngSafely(context: Context, uriString: String): Triple<ByteArray, Int, Int>? {
        return try {
            val uri = Uri.parse(uriString)
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val origW = boundsOpts.outWidth
            val origH = boundsOpts.outHeight
            if (origW <= 0 || origH <= 0) return null

            var sampleSize = 1
            while (origW / sampleSize > 1600 || origH / sampleSize > 1600) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val outW = bmp.width
            val outH = bmp.height
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 95, baos)
            bmp.recycle()
            Triple(baos.toByteArray(), outW, outH)
        } catch (_: Throwable) {
            null
        }
    }

    private fun renderPdfPageWithMarkupSafely(
        filePath: String,
        pageIndex: Int,
        strokes: List<InkStroke>
    ): Triple<ByteArray, Int, Int>? {
        return try {
            val bmp = PdfHelper.renderPageBitmap(filePath, pageIndex, 1200) ?: return null
            if (strokes.isNotEmpty()) {
                val canvas = Canvas(bmp)
                PdfHelper.drawNormalizedStrokesOnCanvas(
                    canvas = canvas,
                    strokes = strokes,
                    left = 0f,
                    top = 0f,
                    width = bmp.width.toFloat(),
                    height = bmp.height.toFloat()
                )
            }
            val outW = bmp.width
            val outH = bmp.height
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 92, baos)
            bmp.recycle()
            Triple(baos.toByteArray(), outW, outH)
        } catch (_: Throwable) {
            null
        }
    }

    // ─── Slide & OpenXML Package Builders ────────────────────────

    private fun buildSlideXml(
        slide: DeckSlide,
        slideNumber: Int,
        totalSlides: Int,
        palette: DeckPalette,
        mediaRelId: String?
    ): String {
        val shapesSb = StringBuilder()
        var shapeId = 2

        // 1. Top Accent Gradient Strip across full slide width
        shapesSb.append(
            """
            <p:sp>
              <p:nvSpPr>
                <p:cNvPr id="${shapeId++}" name="Accent Bar"/>
                <p:cNvSpPr/>
                <p:nvPr/>
              </p:nvSpPr>
              <p:spPr>
                <a:xfrm>
                  <a:off x="0" y="0"/>
                  <a:ext cx="$SLIDE_W" cy="109728"/>
                </a:xfrm>
                <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                <a:gradFill>
                  <a:gsLst>
                    <a:gs pos="0"><a:srgbClr val="${palette.accentStartHex}"/></a:gs>
                    <a:gs pos="100000"><a:srgbClr val="${palette.accentEndHex}"/></a:gs>
                  </a:gsLst>
                  <a:lin ang="0" scaled="1"/>
                </a:gradFill>
                <a:ln><a:noFill/></a:ln>
              </p:spPr>
            </p:sp>
            """.trimIndent()
        )

        when (slide) {
            is DeckSlide.Cover -> {
                // Vertical left accent spine
                shapesSb.append(
                    buildRectShape(
                        id = shapeId++,
                        name = "Cover Spine",
                        x = 685800L,
                        y = 914400L,
                        cx = 91440L,
                        cy = 4800000L,
                        fillHex = palette.accentStartHex,
                        borderHex = null
                    )
                )

                // Kicker pill label
                shapesSb.append(
                    buildTextBox(
                        id = shapeId++,
                        name = "Kicker",
                        x = 1005840L,
                        y = 914400L,
                        cx = 10400000L,
                        cy = 457200L,
                        paragraphs = listOf(TextPara(slide.kicker, 1300, true, palette.accentStartHex)),
                        fillHex = null,
                        borderHex = null
                    )
                )

                // Large Cover Title
                shapesSb.append(
                    buildTextBox(
                        id = shapeId++,
                        name = "Cover Title",
                        x = 1005840L,
                        y = 1417320L,
                        cx = 10400000L,
                        cy = 1371600L,
                        paragraphs = listOf(TextPara(slide.title, 4000, true, palette.titleHex)),
                        fillHex = null,
                        borderHex = null
                    )
                )

                // Summary Card Panel
                val summaryParas = slide.summaryLines.map { line ->
                    TextPara(line, 2000, false, palette.bodyHex)
                }
                shapesSb.append(
                    buildTextBox(
                        id = shapeId++,
                        name = "Cover Summary Card",
                        x = 1005840L,
                        y = 2971800L,
                        cx = 10400000L,
                        cy = 2286000L,
                        paragraphs = summaryParas,
                        fillHex = palette.surfaceHex,
                        borderHex = palette.borderHex,
                        isRounded = true
                    )
                )

                // Footer Metadata
                shapesSb.append(
                    buildTextBox(
                        id = shapeId++,
                        name = "Cover Footer",
                        x = 1005840L,
                        y = 5486400L,
                        cx = 10400000L,
                        cy = 457200L,
                        paragraphs = listOf(TextPara(slide.footerMeta, 1300, false, palette.mutedHex)),
                        fillHex = null,
                        borderHex = null
                    )
                )
            }

            is DeckSlide.BulletCard -> {
                // Header Kicker + Slide Title
                shapesSb.append(
                    buildTextBox(
                        id = shapeId++,
                        name = "Header Kicker",
                        x = 685800L,
                        y = 320040L,
                        cx = 10820400L,
                        cy = 342900L,
                        paragraphs = listOf(TextPara(slide.kicker, 1100, true, palette.accentStartHex)),
                        fillHex = null,
                        borderHex = null
                    )
                )
                shapesSb.append(
                    buildTextBox(
                        id = shapeId++,
                        name = "Slide Title",
                        x = 685800L,
                        y = 640080L,
                        cx = 10820400L,
                        cy = 685800L,
                        paragraphs = listOf(TextPara(slide.title, 2800, true, palette.titleHex)),
                        fillHex = null,
                        borderHex = null
                    )
                )

                // Content Card
                val bodyParas = slide.lines.mapIndexed { idx, line ->
                    val sz = if (slide.highlightFirstLine && idx == 0) 2100 else 1900
                    val bold = slide.highlightFirstLine && idx == 0
                    val col = if (line.startsWith("☑")) palette.mutedHex else palette.bodyHex
                    TextPara(line, sz, bold, col)
                }.ifEmpty {
                    listOf(TextPara("(No additional content)", 1800, false, palette.mutedHex))
                }

                shapesSb.append(
                    buildTextBox(
                        id = shapeId++,
                        name = "Content Card",
                        x = 685800L,
                        y = 1463040L,
                        cx = 10820400L,
                        cy = 4754880L,
                        paragraphs = bodyParas,
                        fillHex = palette.surfaceHex,
                        borderHex = palette.borderHex,
                        isRounded = true
                    )
                )
            }

            is DeckSlide.MediaVisual -> {
                // Header Kicker + Title
                shapesSb.append(
                    buildTextBox(
                        id = shapeId++,
                        name = "Header Kicker",
                        x = 685800L,
                        y = 320040L,
                        cx = 10820400L,
                        cy = 342900L,
                        paragraphs = listOf(TextPara(slide.kicker, 1100, true, palette.accentStartHex)),
                        fillHex = null,
                        borderHex = null
                    )
                )
                shapesSb.append(
                    buildTextBox(
                        id = shapeId++,
                        name = "Slide Title",
                        x = 685800L,
                        y = 640080L,
                        cx = 10820400L,
                        cy = 685800L,
                        paragraphs = listOf(TextPara(slide.title, 2600, true, palette.titleHex)),
                        fillHex = null,
                        borderHex = null
                    )
                )

                // Fit image cleanly preserving aspect ratio inside left 60% visual viewport
                val maxBoxX = 685800L
                val maxBoxY = 1463040L
                val maxBoxW = 6583680L
                val maxBoxH = 4754880L

                val imgW = slide.media.pixelW.coerceAtLeast(1).toDouble()
                val imgH = slide.media.pixelH.coerceAtLeast(1).toDouble()
                val scale = minOf(maxBoxW / imgW, maxBoxH / imgH)
                val finalW = (imgW * scale).roundToInt().toLong().coerceAtLeast(91440L)
                val finalH = (imgH * scale).roundToInt().toLong().coerceAtLeast(91440L)
                val finalX = maxBoxX + (maxBoxW - finalW) / 2L
                val finalY = maxBoxY + (maxBoxH - finalH) / 2L

                if (mediaRelId != null) {
                    shapesSb.append(
                        """
                        <p:pic>
                          <p:nvPicPr>
                            <p:cNvPr id="${shapeId++}" name="Visual Media"/>
                            <p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr>
                            <p:nvPr/>
                          </p:nvPicPr>
                          <p:blipFill>
                            <a:blip r:embed="$mediaRelId"/>
                            <a:stretch><a:fillRect/></a:stretch>
                          </p:blipFill>
                          <p:spPr>
                            <a:xfrm>
                              <a:off x="$finalX" y="$finalY"/>
                              <a:ext cx="$finalW" cy="$finalH"/>
                            </a:xfrm>
                            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                            <a:ln w="12700"><a:solidFill><a:srgbClr val="${palette.borderHex}"/></a:solidFill></a:ln>
                          </p:spPr>
                        </p:pic>
                        """.trimIndent()
                    )
                }

                // Right-hand companion notes panel
                val sideParas = mutableListOf(
                    TextPara(slide.sideTitle, 1900, true, palette.titleHex)
                )
                slide.sideLines.forEach { line ->
                    sideParas.add(TextPara(line, 1500, false, palette.bodyHex))
                }
                shapesSb.append(
                    buildTextBox(
                        id = shapeId++,
                        name = "Companion Notes Card",
                        x = 7589520L,
                        y = 1463040L,
                        cx = 3916680L,
                        cy = 4754880L,
                        paragraphs = sideParas,
                        fillHex = palette.surfaceHex,
                        borderHex = palette.borderHex,
                        isRounded = true
                    )
                )
            }
        }

        // Slide Counter Footer
        shapesSb.append(
            buildTextBox(
                id = shapeId,
                name = "Slide Counter",
                x = 685800L,
                y = 6355080L,
                cx = 10820400L,
                cy = 320040L,
                paragraphs = listOf(TextPara("NoteGraph  •  $slideNumber / $totalSlides", 1100, false, palette.mutedHex, "r")),
                fillHex = null,
                borderHex = null
            )
        )

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:bg>
      <p:bgPr>
        <a:solidFill><a:srgbClr val="${palette.bgHex}"/></a:solidFill>
        <a:effectLst/>
      </p:bgPr>
    </p:bg>
    <p:spTree>
      <p:nvGrpSpPr>
        <p:cNvPr id="1" name=""/>
        <p:cNvGrpSpPr/>
        <p:nvPr/>
      </p:nvGrpSpPr>
      <p:grpSpPr>
        <a:xfrm>
          <a:off x="0" y="0"/>
          <a:ext cx="0" cy="0"/>
          <a:chOff x="0" y="0"/>
          <a:chExt cx="0" cy="0"/>
        </a:xfrm>
      </p:grpSpPr>
      $shapesSb
    </p:spTree>
  </p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sld>"""
    }

    private data class TextPara(
        val text: String,
        val sizeHundredthsPt: Int,
        val bold: Boolean,
        val colorHex: String,
        val align: String = "l"
    )

    private fun buildTextBox(
        id: Int,
        name: String,
        x: Long,
        y: Long,
        cx: Long,
        cy: Long,
        paragraphs: List<TextPara>,
        fillHex: String?,
        borderHex: String?,
        isRounded: Boolean = false
    ): String {
        val geom = if (isRounded) "roundRect" else "rect"
        val avLst = if (isRounded) """<a:avLst><a:gd name="adj" fmla="val 4000"/></a:avLst>""" else "<a:avLst/>"
        val fillXml = if (fillHex != null) {
            """<a:solidFill><a:srgbClr val="$fillHex"/></a:solidFill>"""
        } else {
            "<a:noFill/>"
        }
        val lnXml = if (borderHex != null) {
            """<a:ln w="12700"><a:solidFill><a:srgbClr val="$borderHex"/></a:solidFill></a:ln>"""
        } else {
            """<a:ln><a:noFill/></a:ln>"""
        }

        val parasXml = paragraphs.joinToString("\n") { p ->
            val safe = escapeXml(sanitizeXmlText(p.text))
            val bAttr = if (p.bold) """ b="1"""" else ""
            """
            <a:p>
              <a:pPr algn="${p.align}">
                <a:lnSpc><a:spcPct val="120000"/></a:lnSpc>
                <a:spcAft><a:spcPts val="600"/></a:spcAft>
              </a:pPr>
              <a:r>
                <a:rPr lang="en-US" sz="${p.sizeHundredthsPt}"$bAttr dirty="0">
                  <a:solidFill><a:srgbClr val="${p.colorHex}"/></a:solidFill>
                  <a:latin typeface="Calibri"/>
                  <a:ea typeface="Calibri"/>
                  <a:cs typeface="Calibri"/>
                </a:rPr>
                <a:t xml:space="preserve">$safe</a:t>
              </a:r>
              <a:endParaRPr lang="en-US" sz="${p.sizeHundredthsPt}" dirty="0"/>
            </a:p>
            """.trimIndent()
        }

        return """
        <p:sp>
          <p:nvSpPr>
            <p:cNvPr id="$id" name="${escapeXml(sanitizeXmlText(name))}"/>
            <p:cNvSpPr txBox="1"/>
            <p:nvPr/>
          </p:nvSpPr>
          <p:spPr>
            <a:xfrm>
              <a:off x="$x" y="$y"/>
              <a:ext cx="$cx" cy="$cy"/>
            </a:xfrm>
            <a:prstGeom prst="$geom">$avLst</a:prstGeom>
            $fillXml
            $lnXml
          </p:spPr>
          <p:txBody>
            <a:bodyPr wrap="square" lIns="182880" tIns="137160" rIns="182880" bIns="137160" rtlCol="0">
              <a:normAutofit/>
            </a:bodyPr>
            <a:lstStyle/>
            $parasXml
          </p:txBody>
        </p:sp>
        """.trimIndent()
    }

    private fun buildRectShape(
        id: Int,
        name: String,
        x: Long,
        y: Long,
        cx: Long,
        cy: Long,
        fillHex: String,
        borderHex: String?
    ): String {
        val lnXml = if (borderHex != null) {
            """<a:ln w="12700"><a:solidFill><a:srgbClr val="$borderHex"/></a:solidFill></a:ln>"""
        } else {
            """<a:ln><a:noFill/></a:ln>"""
        }
        return """
        <p:sp>
          <p:nvSpPr>
            <p:cNvPr id="$id" name="${escapeXml(name)}"/>
            <p:cNvSpPr/>
            <p:nvPr/>
          </p:nvSpPr>
          <p:spPr>
            <a:xfrm>
              <a:off x="$x" y="$y"/>
              <a:ext cx="$cx" cy="$cy"/>
            </a:xfrm>
            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
            <a:solidFill><a:srgbClr val="$fillHex"/></a:solidFill>
            $lnXml
          </p:spPr>
        </p:sp>
        """.trimIndent()
    }

    private fun buildContentTypesXml(slideCount: Int, hasPng: Boolean): String {
        val sb = StringBuilder()
        sb.append(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>"""
        )
        if (hasPng) {
            sb.append("""
  <Default Extension="png" ContentType="image/png"/>""")
        }
        sb.append(
            """
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/presProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presProps+xml"/>
  <Override PartName="/ppt/viewProps.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml"/>
  <Override PartName="/ppt/tableStyles.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml"/>
  <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
  <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
  <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>"""
        )
        for (i in 1..slideCount) {
            sb.append("""
  <Override PartName="/ppt/slides/slide$i.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
        }
        sb.append("\n</Types>")
        return sb.toString()
    }

    private fun buildPresentationXml(slideCount: Int): String {
        val slideIds = (0 until slideCount).joinToString("") { idx ->
            """<p:sldId id="${256 + idx}" r:id="rId${6 + idx}"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" saveSubsetFonts="1">
  <p:sldMasterIdLst>
    <p:sldMasterId id="2147483648" r:id="rId1"/>
  </p:sldMasterIdLst>
  <p:sldIdLst>
    $slideIds
  </p:sldIdLst>
  <p:sldSz cx="$SLIDE_W" cy="$SLIDE_H"/>
  <p:notesSz cx="6858000" cy="9144000"/>
  <p:defaultTextStyle>
    <a:defPPr>
      <a:defRPr lang="en-US"/>
    </a:defPPr>
  </p:defaultTextStyle>
</p:presentation>"""
    }

    private fun buildPresentationRelsXml(slideCount: Int): String {
        val slideRels = (0 until slideCount).joinToString("\n") { idx ->
            """  <Relationship Id="rId${6 + idx}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${idx + 1}.xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/presProps" Target="presProps.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/viewProps" Target="viewProps.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>
  <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/tableStyles" Target="tableStyles.xml"/>
$slideRels
</Relationships>"""
    }

    private fun buildSlideMasterXml(palette: DeckPalette): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:bg>
      <p:bgRef idx="1001">
        <a:schemeClr val="bg1"/>
      </p:bgRef>
    </p:bg>
    <p:spTree>
      <p:nvGrpSpPr>
        <p:cNvPr id="1" name=""/>
        <p:cNvGrpSpPr/>
        <p:nvPr/>
      </p:nvGrpSpPr>
      <p:grpSpPr>
        <a:xfrm>
          <a:off x="0" y="0"/>
          <a:ext cx="0" cy="0"/>
          <a:chOff x="0" y="0"/>
          <a:chExt cx="0" cy="0"/>
        </a:xfrm>
      </p:grpSpPr>
    </p:spTree>
  </p:cSld>
  <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
  <p:sldLayoutIdLst>
    <p:sldLayoutId id="2147483649" r:id="rId1"/>
  </p:sldLayoutIdLst>
  <p:txStyles>
    <p:titleStyle>
      <a:lvl1pPr algn="l"><a:defRPr sz="3200"><a:solidFill><a:srgbClr val="${palette.titleHex}"/></a:solidFill></a:defRPr></a:lvl1pPr>
    </p:titleStyle>
    <p:bodyStyle>
      <a:lvl1pPr algn="l"><a:defRPr sz="1800"><a:solidFill><a:srgbClr val="${palette.bodyHex}"/></a:solidFill></a:defRPr></a:lvl1pPr>
    </p:bodyStyle>
    <p:otherStyle>
      <a:lvl1pPr algn="l"><a:defRPr sz="1600"><a:solidFill><a:srgbClr val="${palette.bodyHex}"/></a:solidFill></a:defRPr></a:lvl1pPr>
    </p:otherStyle>
  </p:txStyles>
</p:sldMaster>"""
    }

    private fun buildThemeXml(palette: DeckPalette): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="NoteGraph Theme">
  <a:themeElements>
    <a:clrScheme name="NoteGraph Palette">
      <a:dk1><a:srgbClr val="111827"/></a:dk1>
      <a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>
      <a:dk2><a:srgbClr val="334155"/></a:dk2>
      <a:lt2><a:srgbClr val="F4F1EA"/></a:lt2>
      <a:accent1><a:srgbClr val="${palette.accentStartHex}"/></a:accent1>
      <a:accent2><a:srgbClr val="${palette.accentEndHex}"/></a:accent2>
      <a:accent3><a:srgbClr val="10B981"/></a:accent3>
      <a:accent4><a:srgbClr val="F59E0B"/></a:accent4>
      <a:accent5><a:srgbClr val="0EA5E9"/></a:accent5>
      <a:accent6><a:srgbClr val="F43F5E"/></a:accent6>
      <a:hlink><a:srgbClr val="${palette.accentStartHex}"/></a:hlink>
      <a:folHlink><a:srgbClr val="${palette.accentEndHex}"/></a:folHlink>
    </a:clrScheme>
    <a:fontScheme name="NoteGraph Sans">
      <a:majorFont>
        <a:latin typeface="Calibri"/>
        <a:ea typeface=""/>
        <a:cs typeface=""/>
      </a:majorFont>
      <a:minorFont>
        <a:latin typeface="Calibri"/>
        <a:ea typeface=""/>
        <a:cs typeface=""/>
      </a:minorFont>
    </a:fontScheme>
    <a:fmtScheme name="NoteGraph Clean">
      <a:fillStyleLst>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
      </a:fillStyleLst>
      <a:lnStyleLst>
        <a:ln w="9525" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
        <a:ln w="25400" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
        <a:ln w="38100" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
      </a:lnStyleLst>
      <a:effectStyleLst>
        <a:effectStyle><a:effectLst/></a:effectStyle>
        <a:effectStyle><a:effectLst/></a:effectStyle>
        <a:effectStyle><a:effectLst/></a:effectStyle>
      </a:effectStyleLst>
      <a:bgFillStyleLst>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
      </a:bgFillStyleLst>
    </a:fmtScheme>
  </a:themeElements>
  <a:objectDefaults/>
  <a:extraClrSchemeLst/>
</a:theme>"""
    }

    // ─── Text Wrapping & XML Utilities ───────────────────────────

    private fun wrapLine(line: String, maxChars: Int): List<String> {
        val clean = line.trimEnd()
        if (clean.isBlank()) return emptyList()
        if (clean.length <= maxChars) return listOf(clean)

        val result = mutableListOf<String>()
        val words = clean.split(Regex("\\s+"))
        val current = StringBuilder()
        for (word in words) {
            if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length <= maxChars) {
                current.append(' ').append(word)
            } else {
                result.add(current.toString())
                current.clear()
                current.append(word)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun colorToHex6(color: androidx.compose.ui.graphics.Color): String {
        val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
        val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
        val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
        return String.format(Locale.US, "%02X%02X%02X", r, g, b)
    }

    private fun sanitizeXmlText(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            val code = ch.code
            if (Character.isHighSurrogate(ch) && i + 1 < input.length && Character.isLowSurrogate(input[i + 1])) {
                sb.append(ch).append(input[i + 1])
                i += 2
                continue
            }
            if (code == 0x9 || code == 0xA || code == 0xD || (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)) {
                sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun writeEntry(zos: ZipOutputStream, path: String, content: String) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""

    private const val SLIDE_MASTER_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
</Relationships>"""

    private const val SLIDE_LAYOUT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>"""

    private const val SLIDE_LAYOUT_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1">
  <p:cSld name="Blank">
    <p:spTree>
      <p:nvGrpSpPr>
        <p:cNvPr id="1" name=""/>
        <p:cNvGrpSpPr/>
        <p:nvPr/>
      </p:nvGrpSpPr>
      <p:grpSpPr>
        <a:xfrm>
          <a:off x="0" y="0"/>
          <a:ext cx="0" cy="0"/>
          <a:chOff x="0" y="0"/>
          <a:chExt cx="0" cy="0"/>
        </a:xfrm>
      </p:grpSpPr>
    </p:spTree>
  </p:cSld>
  <p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>
</p:sldLayout>"""

    private const val PRES_PROPS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentationPr xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"/>"""

    private const val VIEW_PROPS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:viewPr xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:normalViewPr>
    <p:restoredLeft sz="15620"/>
    <p:restoredTop sz="94660"/>
  </p:normalViewPr>
  <p:slideViewPr>
    <p:cSldViewPr>
      <p:cViewPr varScale="1">
        <p:scale><a:sx n="100" d="100"/><a:sy n="100" d="100"/></p:scale>
        <p:origin x="0" y="0"/>
      </p:cViewPr>
    </p:cSldViewPr>
  </p:slideViewPr>
</p:viewPr>"""

    private const val TABLE_STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:tblStyleLst xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" def="{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}"/>"""
}
