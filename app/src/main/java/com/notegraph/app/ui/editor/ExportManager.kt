package com.notegraph.app.ui.editor

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.notegraph.app.data.local.NoteJson
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Multi-format export engine for NoteGraph notes.
 * Generates PDF, OpenXML DOCX, OpenXML PPTX, Lossless PNG, and Markdown
 * using standard Android / Java APIs without third-party heavy dependencies.
 */
object ExportManager {

    fun parseBlocks(note: NoteEntity): List<NoteBlock> = NoteJson.decodeBlocks(note.blocksJson)

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    // ─── 1. PDF Export ───────────────────────────────────────────

    fun exportPdf(context: Context, note: NoteEntity, outputStream: OutputStream) {
        val blocks = parseBlocks(note)
        val pageWidth = 595   // Standard A4 in PostScript points (72 dpi)
        val pageHeight = 842
        val margin = 40f
        val usableWidth = pageWidth - (2 * margin)

        val doc = PdfDocument()
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        var yPos = margin + 20f

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 13f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val labelPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }

        fun ensureSpace(neededHeight: Float): Canvas {
            if (yPos + neededHeight > pageHeight - margin) {
                doc.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                yPos = margin + 20f
            }
            return canvas
        }

        fun drawWrappedText(text: String, paint: Paint, lineSpacing: Float = 4f) {
            if (text.isBlank()) return
            val lines = text.split("\n")
            for (line in lines) {
                if (line.isBlank()) {
                    yPos += paint.textSize + lineSpacing
                    continue
                }
                val words = line.split(" ")
                var currentLine = ""
                for (word in words) {
                    val test = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (paint.measureText(test) > usableWidth && currentLine.isNotEmpty()) {
                        ensureSpace(paint.textSize + lineSpacing)
                        canvas.drawText(currentLine, margin, yPos, paint)
                        yPos += paint.textSize + lineSpacing
                        currentLine = word
                    } else {
                        currentLine = test
                    }
                }
                if (currentLine.isNotEmpty()) {
                    ensureSpace(paint.textSize + lineSpacing)
                    canvas.drawText(currentLine, margin, yPos, paint)
                    yPos += paint.textSize + lineSpacing
                }
            }
        }

        // Draw Title
        drawWrappedText(note.title.ifEmpty { "Untitled Note" }, titlePaint, 8f)
        yPos += 10f

        // Draw Main Body Content
        if (note.content.isNotBlank()) {
            drawWrappedText(note.content, bodyPaint, 5f)
            yPos += 12f
        }

        // Draw Multimedia Blocks
        for (block in blocks) {
            when (block) {
                is NoteBlock.TextBlock -> {
                    if (block.text.isNotBlank()) {
                        drawWrappedText(block.text, bodyPaint, 5f)
                        yPos += 8f
                    }
                }
                is NoteBlock.InkBlock -> {
                    val strokes = NoteJson.decodeStrokes(block.strokesJson)
                    val allPoints = strokes.flatMap { it.points }
                    if (allPoints.isNotEmpty()) {
                        val minX = allPoints.minOf { it.x }
                        val maxX = allPoints.maxOf { it.x }
                        val minY = allPoints.minOf { it.y }
                        val maxY = allPoints.maxOf { it.y }

                        val rawW = (maxX - minX).coerceAtLeast(10f)
                        val rawH = (maxY - minY).coerceAtLeast(10f)

                        // Fit bounded drawing within PDF usable width
                        val scale = (usableWidth / rawW).coerceAtMost(0.55f)
                        val scaledW = rawW * scale
                        val scaledH = rawH * scale
                        val xOffset = margin + (usableWidth - scaledW) / 2f

                        ensureSpace(scaledH + 30f)
                        canvas.drawText("[Drawing / Ink]", margin, yPos, labelPaint)
                        yPos += 16f

                        val inkPaint = Paint().apply {
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }

                        for (stroke in strokes) {
                            if (stroke.points.size < 2) continue
                            inkPaint.strokeWidth = (stroke.strokeWidth * scale).coerceAtLeast(1f)
                            inkPaint.alpha = if (stroke.isHighlighter) 110 else 255
                            inkPaint.color = try {
                                Color.parseColor(stroke.colorHex)
                            } catch (e: Exception) {
                                Color.BLACK
                            }
                            val path = android.graphics.Path()
                            path.moveTo(
                                xOffset + (stroke.points[0].x - minX) * scale,
                                yPos + (stroke.points[0].y - minY) * scale
                            )
                            for (i in 1 until stroke.points.size) {
                                path.lineTo(
                                    xOffset + (stroke.points[i].x - minX) * scale,
                                    yPos + (stroke.points[i].y - minY) * scale
                                )
                            }
                            canvas.drawPath(path, inkPaint)
                        }
                        yPos += scaledH + 16f
                    }
                }
                is NoteBlock.AudioBlock -> {
                    ensureSpace(25f)
                    canvas.drawText("🎙 Audio Recording (${block.durationMs / 1000}s)", margin, yPos, labelPaint)
                    yPos += 20f
                }
                is NoteBlock.ImageBlock -> {
                    ensureSpace(30f)
                    canvas.drawText("🖼 Embedded Image", margin, yPos, labelPaint)
                    yPos += 12f
                    if (block.uriString.isNotBlank()) {
                        try {
                            val uri = Uri.parse(block.uriString)
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                val bmp = BitmapFactory.decodeStream(inputStream)
                                if (bmp != null) {
                                    val imgWidth = usableWidth
                                    val imgHeight = ((bmp.height.toFloat() / bmp.width) * imgWidth).coerceAtMost(320f)
                                    ensureSpace(imgHeight + 10f)
                                    val dest = RectF(margin, yPos, margin + imgWidth, yPos + imgHeight)
                                    canvas.drawBitmap(bmp, null, dest, null)
                                    yPos += imgHeight + 12f
                                    bmp.recycle()
                                }
                            }
                        } catch (e: Exception) {}
                    }
                    if (!block.extractedOcrText.isNullOrBlank()) {
                        drawWrappedText("OCR Text: ${block.extractedOcrText}", labelPaint, 4f)
                        yPos += 8f
                    }
                }
                is NoteBlock.ChecklistBlock -> {
                    ensureSpace(28f)
                    drawWrappedText(block.title.ifBlank { "To-Do Checklist" }, bodyPaint, 6f)
                    for (item in block.items) {
                        val prefix = if (item.isChecked) "[✓]" else "[ ]"
                        val lineText = "$prefix ${item.text.ifBlank { "(empty task)" }}"
                        val itemPaint = Paint(bodyPaint).apply {
                            isStrikeThruText = item.isChecked
                            color = if (item.isChecked) Color.GRAY else Color.DKGRAY
                        }
                        drawWrappedText(lineText, itemPaint, 5f)
                    }
                    yPos += 10f
                }
                is NoteBlock.PdfBlock -> {
                    val annotationsByPage = PdfHelper.parseAnnotations(block.annotationsJson).associateBy { it.pageIndex }
                    val pCount = block.pageCount.coerceAtLeast(PdfHelper.getPageCount(block.filePath))
                    ensureSpace(34f)
                    canvas.drawText("📄 PDF Attachment: ${block.fileName.ifBlank { "Document.pdf" }} ($pCount pages)", margin, yPos, labelPaint)
                    yPos += 18f

                    for (pIdx in 0 until pCount) {
                        val pageBmp = PdfHelper.renderPageBitmap(block.filePath, pIdx, 1080)
                        val pageAnn = annotationsByPage[pIdx]
                        if (pageBmp != null) {
                            val bmpAspect = pageBmp.height.toFloat() / pageBmp.width.coerceAtLeast(1).toFloat()
                            val maxAvailH = pageHeight - (2 * margin) - 36f
                            var drawW = usableWidth
                            var drawH = drawW * bmpAspect
                            if (drawH > maxAvailH) {
                                drawH = maxAvailH
                                drawW = drawH / bmpAspect
                            }
                            ensureSpace(drawH + 24f)
                            val xLeft = margin + (usableWidth - drawW) / 2f
                            val dest = RectF(xLeft, yPos, xLeft + drawW, yPos + drawH)
                            canvas.drawBitmap(pageBmp, null, dest, null)
                            pageBmp.recycle()

                            if (pageAnn != null && pageAnn.strokes.isNotEmpty()) {
                                PdfHelper.drawNormalizedStrokesOnCanvas(canvas, pageAnn.strokes, xLeft, yPos, drawW, drawH)
                            }
                            yPos += drawH + 14f
                        }
                        if (pageAnn != null && pageAnn.pageNote.isNotBlank()) {
                            drawWrappedText("Page ${pIdx + 1} Note: ${pageAnn.pageNote}", labelPaint, 4f)
                            yPos += 6f
                        }
                    }
                }
            }
        }

        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
    }

    // ─── 2. DOCX Export (Valid OpenXML via ZipOutputStream) ───────

    fun exportDocx(note: NoteEntity, outputStream: OutputStream) {
        val blocks = parseBlocks(note)
        val zos = ZipOutputStream(outputStream)

        // [Content_Types].xml
        zos.putNextEntry(ZipEntry("[Content_Types].xml"))
        zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // _rels/.rels
        zos.putNextEntry(ZipEntry("_rels/.rels"))
        zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // word/document.xml
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>""")

        // Title
        sb.append("""<w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="52"/></w:rPr><w:t>${escapeXml(note.title.ifEmpty { "Untitled" })}</w:t></w:r></w:p>""")

        // Content
        if (note.content.isNotBlank()) {
            for (line in note.content.split("\n")) {
                sb.append("""<w:p><w:r><w:sz w:val="26"/><w:t>${escapeXml(line)}</w:t></w:r></w:p>""")
            }
        }

        // Multimedia Blocks
        for (block in blocks) {
            when (block) {
                is NoteBlock.TextBlock -> {
                    for (line in block.text.split("\n")) {
                        sb.append("""<w:p><w:r><w:sz w:val="26"/><w:t>${escapeXml(line)}</w:t></w:r></w:p>""")
                    }
                }
                is NoteBlock.InkBlock -> {
                    sb.append("""<w:p><w:r><w:rPr><w:i/><w:color w:val="777777"/></w:rPr><w:t>[Drawing / Ink Canvas]</w:t></w:r></w:p>""")
                }
                is NoteBlock.AudioBlock -> {
                    sb.append("""<w:p><w:r><w:rPr><w:i/><w:color w:val="777777"/></w:rPr><w:t>[Audio Recording: ${block.durationMs / 1000}s]</w:t></w:r></w:p>""")
                }
                is NoteBlock.ImageBlock -> {
                    sb.append("""<w:p><w:r><w:rPr><w:i/><w:color w:val="777777"/></w:rPr><w:t>[Embedded Image]</w:t></w:r></w:p>""")
                    if (!block.extractedOcrText.isNullOrBlank()) {
                        for (line in block.extractedOcrText.split("\n")) {
                            sb.append("""<w:p><w:r><w:rPr><w:i/></w:rPr><w:t>OCR: ${escapeXml(line)}</w:t></w:r></w:p>""")
                        }
                    }
                }
                is NoteBlock.ChecklistBlock -> {
                    val title = block.title.ifBlank { "To-Do Checklist" }
                    sb.append("""<w:p><w:r><w:rPr><w:b/><w:sz w:val="28"/></w:rPr><w:t>${escapeXml(title)}</w:t></w:r></w:p>""")
                    for (item in block.items) {
                        val prefix = if (item.isChecked) "☑" else "☐"
                        val strikeTag = if (item.isChecked) """<w:strike/><w:color w:val="777777"/>""" else ""
                        sb.append("""<w:p><w:r><w:rPr>$strikeTag<w:sz w:val="24"/></w:rPr><w:t>${escapeXml("$prefix ${item.text}")}</w:t></w:r></w:p>""")
                    }
                }
                is NoteBlock.PdfBlock -> {
                    val anns = PdfHelper.parseAnnotations(block.annotationsJson)
                    sb.append("""<w:p><w:r><w:rPr><w:b/><w:sz w:val="26"/></w:rPr><w:t>${escapeXml("📄 PDF: ${block.fileName.ifBlank { "Document.pdf" }} (${block.pageCount} pages)")}</w:t></w:r></w:p>""")
                    for (ann in anns) {
                        if (ann.pageNote.isNotBlank() || ann.strokes.isNotEmpty()) {
                            val summary = buildString {
                                append("Page ${ann.pageIndex + 1}")
                                if (ann.strokes.isNotEmpty()) append(" [${ann.strokes.size} ink stroke(s)]")
                                if (ann.pageNote.isNotBlank()) append(": ${ann.pageNote}")
                            }
                            sb.append("""<w:p><w:r><w:sz w:val="24"/><w:t>${escapeXml(summary)}</w:t></w:r></w:p>""")
                        }
                    }
                }
            }
        }

        sb.append("""</w:body></w:document>""")
        zos.putNextEntry(ZipEntry("word/document.xml"))
        zos.write(sb.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.close()
    }

    // ─── 3. PPTX Export (PowerPoint & Keynote Compatible) ────────

    fun exportPptx(context: Context?, note: NoteEntity, outputStream: OutputStream) {
        PptxExporter.export(context, note, outputStream)
    }

    fun exportPptx(note: NoteEntity, outputStream: OutputStream) {
        PptxExporter.export(null, note, outputStream)
    }

    // ─── 4. Lossless PNG Export ──────────────────────────────────

    fun exportPng(context: Context, note: NoteEntity, outputStream: OutputStream) {
        val blocks = parseBlocks(note)
        val width = 1080
        val margin = 50f
        val usableWidth = width - (2 * margin)

        // Measure exact heights of all blocks to allocate proper bitmap dimensions
        var calculatedHeight = 160f
        if (note.content.isNotBlank()) {
            calculatedHeight += ((note.content.length / 40) + 2) * 36f
        }

        for (block in blocks) {
            calculatedHeight += when (block) {
                is NoteBlock.TextBlock -> ((block.text.length / 40) + 2) * 34f + 20f
                is NoteBlock.InkBlock -> {
                    val strokes = NoteJson.decodeStrokes(block.strokesJson)
                    val allPts = strokes.flatMap { it.points }
                    if (allPts.isNotEmpty()) {
                        val rawW = (allPts.maxOf { it.x } - allPts.minOf { it.x }).coerceAtLeast(10f)
                        val rawH = (allPts.maxOf { it.y } - allPts.minOf { it.y }).coerceAtLeast(10f)
                        val scale = (usableWidth / rawW).coerceAtMost(1.0f)
                        (rawH * scale) + 60f
                    } else {
                        60f
                    }
                }
                is NoteBlock.AudioBlock -> 70f
                is NoteBlock.ImageBlock -> {
                    var h = 80f
                    if (block.uriString.isNotBlank()) h += 360f
                    if (!block.extractedOcrText.isNullOrBlank()) {
                        h += ((block.extractedOcrText.length / 40) + 2) * 30f
                    }
                    h
                }
                is NoteBlock.ChecklistBlock -> {
                    val itemLines = block.items.sumOf { ((it.text.length / 36) + 1) }.coerceAtLeast(1)
                    60f + (itemLines * 36f)
                }
                is NoteBlock.PdfBlock -> {
                    val anns = PdfHelper.parseAnnotations(block.annotationsJson)
                    val previewPages = anns.filter { it.strokes.isNotEmpty() || it.pageNote.isNotBlank() }
                        .map { it.pageIndex }
                        .ifEmpty { listOf(block.currentPage.coerceIn(0, (block.pageCount - 1).coerceAtLeast(0))) }
                        .take(4)
                    60f + (previewPages.size * 760f)
                }
            }
        }

        val totalHeight = calculatedHeight.toInt().coerceIn(400, 15000)
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var yPos = 70f

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 24f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val labelPaint = Paint().apply {
            color = Color.GRAY
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }

        fun drawTextWrapped(text: String, paint: Paint, spacing: Float = 8f) {
            for (line in text.split("\n")) {
                if (line.isBlank()) {
                    yPos += paint.textSize + spacing
                    continue
                }
                val words = line.split(" ")
                var cur = ""
                for (w in words) {
                    val test = if (cur.isEmpty()) w else "$cur $w"
                    if (paint.measureText(test) > usableWidth && cur.isNotEmpty()) {
                        canvas.drawText(cur, margin, yPos, paint)
                        yPos += paint.textSize + spacing
                        cur = w
                    } else {
                        cur = test
                    }
                }
                if (cur.isNotEmpty()) {
                    canvas.drawText(cur, margin, yPos, paint)
                    yPos += paint.textSize + spacing
                }
            }
        }

        // Draw Title
        drawTextWrapped(note.title.ifEmpty { "Untitled" }, titlePaint, 12f)
        yPos += 16f

        // Draw Content
        if (note.content.isNotBlank()) {
            drawTextWrapped(note.content, bodyPaint, 8f)
            yPos += 16f
        }

        // Draw Blocks
        for (block in blocks) {
            when (block) {
                is NoteBlock.TextBlock -> {
                    if (block.text.isNotBlank()) {
                        drawTextWrapped(block.text, bodyPaint, 8f)
                        yPos += 12f
                    }
                }
                is NoteBlock.InkBlock -> {
                    val strokes = NoteJson.decodeStrokes(block.strokesJson)
                    val allPoints = strokes.flatMap { it.points }
                    if (allPoints.isNotEmpty()) {
                        canvas.drawText("[Drawing / Ink]", margin, yPos, labelPaint)
                        yPos += 26f

                        val minX = allPoints.minOf { it.x }
                        val maxX = allPoints.maxOf { it.x }
                        val minY = allPoints.minOf { it.y }
                        val maxY = allPoints.maxOf { it.y }

                        val rawW = (maxX - minX).coerceAtLeast(10f)
                        val rawH = (maxY - minY).coerceAtLeast(10f)

                        // Fit completely inside usable width without ever exceeding the right margin
                        val scale = (usableWidth / rawW).coerceAtMost(1.0f)
                        val scaledW = rawW * scale
                        val scaledH = rawH * scale
                        // Center horizontally
                        val xOffset = margin + (usableWidth - scaledW) / 2f

                        val inkPaint = Paint().apply {
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }

                        for (stroke in strokes) {
                            if (stroke.points.size < 2) continue
                            inkPaint.strokeWidth = (stroke.strokeWidth * scale).coerceAtLeast(2f)
                            inkPaint.alpha = if (stroke.isHighlighter) 110 else 255
                            inkPaint.color = try {
                                Color.parseColor(stroke.colorHex)
                            } catch (e: Exception) {
                                Color.BLACK
                            }
                            val path = android.graphics.Path()
                            path.moveTo(
                                xOffset + (stroke.points[0].x - minX) * scale,
                                yPos + (stroke.points[0].y - minY) * scale
                            )
                            for (i in 1 until stroke.points.size) {
                                path.lineTo(
                                    xOffset + (stroke.points[i].x - minX) * scale,
                                    yPos + (stroke.points[i].y - minY) * scale
                                )
                            }
                            canvas.drawPath(path, inkPaint)
                        }
                        // Advance yPos by exactly the scaled height of the drawing
                        yPos += scaledH + 30f
                    }
                }
                is NoteBlock.AudioBlock -> {
                    canvas.drawText("🎙 Voice Recording (${block.durationMs / 1000}s)", margin, yPos, labelPaint)
                    yPos += 36f
                }
                is NoteBlock.ImageBlock -> {
                    canvas.drawText("🖼 Embedded Image", margin, yPos, labelPaint)
                    yPos += 26f
                    if (block.uriString.isNotBlank()) {
                        try {
                            val uri = Uri.parse(block.uriString)
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                val bmp = BitmapFactory.decodeStream(inputStream)
                                if (bmp != null) {
                                    val imgWidth = usableWidth
                                    val imgHeight = ((bmp.height.toFloat() / bmp.width) * imgWidth).coerceAtMost(450f)
                                    val dest = RectF(margin, yPos, margin + imgWidth, yPos + imgHeight)
                                    canvas.drawBitmap(bmp, null, dest, null)
                                    yPos += imgHeight + 16f
                                    bmp.recycle()
                                }
                            }
                        } catch (e: Exception) {}
                    }
                    if (!block.extractedOcrText.isNullOrBlank()) {
                        drawTextWrapped("OCR: ${block.extractedOcrText}", labelPaint, 6f)
                        yPos += 10f
                    }
                }
                is NoteBlock.ChecklistBlock -> {
                    drawTextWrapped(block.title.ifBlank { "To-Do Checklist" }, bodyPaint, 8f)
                    for (item in block.items) {
                        val prefix = if (item.isChecked) "☑" else "☐"
                        val itemPaint = Paint(bodyPaint).apply {
                            isStrikeThruText = item.isChecked
                            color = if (item.isChecked) Color.GRAY else Color.rgb(33, 33, 33)
                        }
                        drawTextWrapped("$prefix ${item.text.ifBlank { "(task)" }}", itemPaint, 8f)
                    }
                    yPos += 14f
                }
                is NoteBlock.PdfBlock -> {
                    canvas.drawText("📄 PDF: ${block.fileName.ifBlank { "Document.pdf" }} (${block.pageCount} pages)", margin, yPos, labelPaint)
                    yPos += 28f
                    val anns = PdfHelper.parseAnnotations(block.annotationsJson).associateBy { it.pageIndex }
                    val targetPages = anns.values
                        .filter { it.strokes.isNotEmpty() || it.pageNote.isNotBlank() }
                        .map { it.pageIndex }
                        .ifEmpty { listOf(block.currentPage.coerceIn(0, (block.pageCount - 1).coerceAtLeast(0))) }
                        .take(4)
                    for (pIdx in targetPages) {
                        val bmp = PdfHelper.renderPageBitmap(block.filePath, pIdx, 960) ?: continue
                        val drawW = usableWidth
                        val drawH = ((bmp.height.toFloat() / bmp.width.coerceAtLeast(1)) * drawW).coerceAtMost(640f)
                        if (yPos + drawH + 40f > totalHeight) {
                            bmp.recycle()
                            break
                        }
                        val dest = RectF(margin, yPos, margin + drawW, yPos + drawH)
                        canvas.drawBitmap(bmp, null, dest, null)
                        bmp.recycle()
                        val ann = anns[pIdx]
                        if (ann != null && ann.strokes.isNotEmpty()) {
                            PdfHelper.drawNormalizedStrokesOnCanvas(canvas, ann.strokes, margin, yPos, drawW, drawH)
                        }
                        yPos += drawH + 14f
                        if (ann != null && ann.pageNote.isNotBlank()) {
                            drawTextWrapped("Page ${pIdx + 1}: ${ann.pageNote}", labelPaint, 6f)
                            yPos += 10f
                        }
                    }
                }
            }
        }

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        bitmap.recycle()
    }

    // ─── 5. Markdown Export ──────────────────────────────────────

    fun exportMarkdown(note: NoteEntity, outputStream: OutputStream) {
        val blocks = parseBlocks(note)
        val sb = StringBuilder()
        sb.appendLine("# ${note.title.ifEmpty { "Untitled" }}")
        sb.appendLine()
        if (note.content.isNotBlank()) {
            sb.appendLine(note.content)
            sb.appendLine()
        }
        for (block in blocks) {
            when (block) {
                is NoteBlock.TextBlock -> {
                    if (block.text.isNotBlank()) {
                        sb.appendLine(block.text)
                        sb.appendLine()
                    }
                }
                is NoteBlock.InkBlock -> {
                    sb.appendLine("*[Drawing / Ink Canvas]*")
                    sb.appendLine()
                }
                is NoteBlock.AudioBlock -> {
                    sb.appendLine("*[Audio Recording: ${block.durationMs / 1000}s]*")
                    sb.appendLine()
                }
                is NoteBlock.ImageBlock -> {
                    if (block.uriString.isNotBlank()) {
                        sb.appendLine("![Image](${block.uriString})")
                    }
                    if (!block.extractedOcrText.isNullOrBlank()) {
                        sb.appendLine()
                        sb.appendLine("> OCR Text: ${block.extractedOcrText}")
                    }
                    sb.appendLine()
                }
                is NoteBlock.ChecklistBlock -> {
                    sb.appendLine("### ${block.title.ifBlank { "To-Do Checklist" }}")
                    for (item in block.items) {
                        val mark = if (item.isChecked) "x" else " "
                        sb.appendLine("- [$mark] ${item.text}")
                    }
                    sb.appendLine()
                }
                is NoteBlock.PdfBlock -> {
                    sb.appendLine("### 📄 PDF: ${block.fileName.ifBlank { "Document.pdf" }} (${block.pageCount} pages)")
                    val anns = PdfHelper.parseAnnotations(block.annotationsJson)
                    for (ann in anns) {
                        if (ann.strokes.isNotEmpty() || ann.pageNote.isNotBlank()) {
                            sb.appendLine("- **Page ${ann.pageIndex + 1}** (${ann.strokes.size} strokes): ${ann.pageNote}")
                        }
                    }
                    sb.appendLine()
                }
            }
        }
        outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
    }
}
