package com.notegraph.app.ui.editor

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Multi-format export engine for NoteGraph notes.
 * Generates PDF, OpenXML DOCX, OpenXML PPTX, Lossless PNG, and Markdown
 * using standard Android / Java APIs without third-party heavy dependencies.
 */
object ExportManager {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseBlocks(note: NoteEntity): List<NoteBlock> = try {
        json.decodeFromString<List<NoteBlock>>(note.blocksJson)
    } catch (_: Exception) {
        emptyList()
    }

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
                    val strokes = try {
                        json.decodeFromString<List<InkStroke>>(block.strokesJson)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (strokes.isNotEmpty()) {
                        ensureSpace(220f)
                        canvas.drawText("[Drawing / Ink]", margin, yPos, labelPaint)
                        yPos += 14f

                        val inkPaint = Paint().apply {
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        // Scale strokes from screen coordinates (~1080px) to usable PDF width
                        val scale = (usableWidth / 1000f).coerceIn(0.2f, 1f)
                        for (stroke in strokes) {
                            if (stroke.points.size < 2) continue
                            inkPaint.strokeWidth = (stroke.strokeWidth * scale).coerceAtLeast(1f)
                            inkPaint.alpha = if (stroke.isHighlighter) 110 else 255
                            inkPaint.color = try {
                                Color.parseColor(stroke.colorHex)
                            } catch (_: Exception) {
                                Color.BLACK
                            }
                            val path = android.graphics.Path()
                            path.moveTo(
                                margin + stroke.points[0].x * scale,
                                yPos + stroke.points[0].y * scale
                            )
                            for (i in 1 until stroke.points.size) {
                                path.lineTo(
                                    margin + stroke.points[i].x * scale,
                                    yPos + stroke.points[i].y * scale
                                )
                            }
                            canvas.drawPath(path, inkPaint)
                        }
                        yPos += (block.heightDp * scale) + 16f
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
                        } catch (_: Exception) {}
                    }
                    if (!block.extractedOcrText.isNullOrBlank()) {
                        drawWrappedText("OCR Text: ${block.extractedOcrText}", labelPaint, 4f)
                        yPos += 8f
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
            }
        }

        sb.append("""</w:body></w:document>""")
        zos.putNextEntry(ZipEntry("word/document.xml"))
        zos.write(sb.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.close()
    }

    // ─── 3. PPTX Export (Valid OpenXML via ZipOutputStream) ───────

    fun exportPptx(note: NoteEntity, outputStream: OutputStream) {
        val blocks = parseBlocks(note)
        val zos = ZipOutputStream(outputStream)

        data class SlideData(val title: String, val body: String)
        val slides = mutableListOf<SlideData>()
        slides.add(SlideData(note.title.ifEmpty { "Note" }, note.content.take(800)))

        for (block in blocks) {
            when (block) {
                is NoteBlock.TextBlock -> {
                    if (block.text.isNotBlank()) {
                        slides.add(SlideData("Text Block", block.text))
                    }
                }
                is NoteBlock.InkBlock -> {
                    slides.add(SlideData("Ink Canvas", "Drawing / Handwritten ink note"))
                }
                is NoteBlock.AudioBlock -> {
                    slides.add(SlideData("Voice Recording", "Audio duration: ${block.durationMs / 1000}s"))
                }
                is NoteBlock.ImageBlock -> {
                    val desc = if (!block.extractedOcrText.isNullOrBlank()) {
                        "OCR Extracted Text:\n${block.extractedOcrText}"
                    } else {
                        "Embedded Image"
                    }
                    slides.add(SlideData("Image Block", desc))
                }
            }
        }

        // [Content_Types].xml
        val ctSb = StringBuilder()
        ctSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
        for (i in slides.indices) {
            ctSb.append("""
  <Override PartName="/ppt/slides/slide${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
        }
        ctSb.append("\n</Types>")
        zos.putNextEntry(ZipEntry("[Content_Types].xml"))
        zos.write(ctSb.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // _rels/.rels
        zos.putNextEntry(ZipEntry("_rels/.rels"))
        zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // ppt/presentation.xml
        val presSb = StringBuilder()
        presSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<p:sldIdLst>""")
        for (i in slides.indices) {
            presSb.append("""<p:sldId id="${256 + i}" r:id="rId${i + 2}"/>""")
        }
        presSb.append("""</p:sldIdLst><p:sldSz cx="9144000" cy="6858000" type="screen4x3"/><p:notesSz cx="6858000" cy="9144000"/></p:presentation>""")
        zos.putNextEntry(ZipEntry("ppt/presentation.xml"))
        zos.write(presSb.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // ppt/_rels/presentation.xml.rels
        val presRelsSb = StringBuilder()
        presRelsSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in slides.indices) {
            presRelsSb.append("""<Relationship Id="rId${i + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${i + 1}.xml"/>""")
        }
        presRelsSb.append("""</Relationships>""")
        zos.putNextEntry(ZipEntry("ppt/_rels/presentation.xml.rels"))
        zos.write(presRelsSb.toString().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // Slide XMLs
        for (i in slides.indices) {
            val slide = slides[i]
            val slideXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<p:cSld>
<p:spTree>
<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
<p:grpSpPr/>
<p:sp>
<p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
<p:spPr><a:xfrm><a:off x="457200" y="274638"/><a:ext cx="8229600" cy="1143000"/></a:xfrm></p:spPr>
<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang="en-US" dirty="0"/><a:t>${escapeXml(slide.title)}</a:t></a:r></a:p></p:txBody>
</p:sp>
<p:sp>
<p:nvSpPr><p:cNvPr id="3" name="Content"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr>
<p:spPr><a:xfrm><a:off x="457200" y="1600200"/><a:ext cx="8229600" cy="4525963"/></a:xfrm></p:spPr>
<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang="en-US" dirty="0"/><a:t>${escapeXml(slide.body)}</a:t></a:r></a:p></p:txBody>
</p:sp>
</p:spTree>
</p:cSld>
</p:sld>"""
            zos.putNextEntry(ZipEntry("ppt/slides/slide${i + 1}.xml"))
            zos.write(slideXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        zos.close()
    }

    // ─── 4. Lossless PNG Export ──────────────────────────────────

    fun exportPng(context: Context, note: NoteEntity, outputStream: OutputStream) {
        val blocks = parseBlocks(note)
        val width = 1080
        var estimatedHeight = 180

        if (note.content.isNotBlank()) {
            estimatedHeight += ((note.content.length / 45) + 2) * 32
        }

        for (block in blocks) {
            estimatedHeight += when (block) {
                is NoteBlock.TextBlock -> ((block.text.length / 45) + 2) * 30 + 16
                is NoteBlock.InkBlock -> block.heightDp.toInt() + 40
                is NoteBlock.AudioBlock -> 60
                is NoteBlock.ImageBlock -> 120 + if (!block.extractedOcrText.isNullOrBlank()) {
                    ((block.extractedOcrText.length / 45) + 2) * 28
                } else 0
            }
        }
        val totalHeight = estimatedHeight.coerceIn(400, 10000)

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val margin = 50f
        val usableWidth = width - (2 * margin)
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

        // Draw title
        drawTextWrapped(note.title.ifEmpty { "Untitled" }, titlePaint, 12f)
        yPos += 16f

        // Draw content
        if (note.content.isNotBlank()) {
            drawTextWrapped(note.content, bodyPaint, 8f)
            yPos += 14f
        }

        // Draw blocks
        for (block in blocks) {
            when (block) {
                is NoteBlock.TextBlock -> {
                    if (block.text.isNotBlank()) {
                        drawTextWrapped(block.text, bodyPaint, 8f)
                        yPos += 10f
                    }
                }
                is NoteBlock.InkBlock -> {
                    val strokes = try {
                        json.decodeFromString<List<InkStroke>>(block.strokesJson)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (strokes.isNotEmpty()) {
                        canvas.drawText("[Drawing]", margin, yPos, labelPaint)
                        yPos += 24f
                        val inkPaint = Paint().apply {
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        for (stroke in strokes) {
                            if (stroke.points.size < 2) continue
                            inkPaint.strokeWidth = stroke.strokeWidth
                            inkPaint.alpha = if (stroke.isHighlighter) 110 else 255
                            inkPaint.color = try {
                                Color.parseColor(stroke.colorHex)
                            } catch (_: Exception) {
                                Color.BLACK
                            }
                            val path = android.graphics.Path()
                            path.moveTo(margin + stroke.points[0].x, yPos + stroke.points[0].y)
                            for (i in 1 until stroke.points.size) {
                                path.lineTo(margin + stroke.points[i].x, yPos + stroke.points[i].y)
                            }
                            canvas.drawPath(path, inkPaint)
                        }
                        yPos += block.heightDp + 20f
                    }
                }
                is NoteBlock.AudioBlock -> {
                    canvas.drawText("🎙 Voice Recording (${block.durationMs / 1000}s)", margin, yPos, labelPaint)
                    yPos += 36f
                }
                is NoteBlock.ImageBlock -> {
                    canvas.drawText("🖼 Embedded Image", margin, yPos, labelPaint)
                    yPos += 26f
                    if (!block.extractedOcrText.isNullOrBlank()) {
                        drawTextWrapped("OCR: ${block.extractedOcrText}", labelPaint, 6f)
                        yPos += 10f
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
            }
        }
        outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
    }
}
