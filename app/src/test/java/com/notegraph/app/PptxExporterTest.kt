package com.notegraph.app

import com.notegraph.app.data.local.entity.ChecklistItem
import com.notegraph.app.data.local.entity.InkStroke
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.data.local.entity.PdfPageAnnotation
import com.notegraph.app.data.local.entity.StrokePoint
import com.notegraph.app.ui.editor.ExportManager
import com.notegraph.app.ui.editor.PptxExporter
import com.notegraph.app.ui.theme.AppThemeMode
import com.notegraph.app.ui.theme.ThemeManager
import com.notegraph.app.ui.theme.UiEngineMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class PptxExporterTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `exportPptx writes complete Keynote and PowerPoint OOXML package with valid XML`() {
        val strokes = listOf(
            InkStroke(
                points = listOf(StrokePoint(10f, 20f), StrokePoint(140f, 180f)),
                colorHex = "#A855F7",
                strokeWidth = 6f
            )
        )
        val pdfAnnotations = listOf(
            PdfPageAnnotation(
                pageIndex = 0,
                strokes = listOf(
                    InkStroke(
                        points = listOf(StrokePoint(0.1f, 0.2f), StrokePoint(0.7f, 0.4f)),
                        colorHex = "#EF4444",
                        strokeWidth = 4f
                    )
                ),
                pageNote = "Key derivation & proof summary"
            )
        )
        val blocks = listOf(
            NoteBlock.TextBlock(id = "t1", text = "Follow-up theorem proof\nWith indented details", audioTimestampMs = 42000L),
            NoteBlock.ChecklistBlock(
                id = "c1",
                title = "Exam Prep",
                items = listOf(
                    ChecklistItem("i1", "Review lectures", isChecked = true),
                    ChecklistItem("i2", "Solve problem set & practice <edge> cases", isChecked = false)
                )
            ),
            NoteBlock.InkBlock(id = "k1", strokesJson = json.encodeToString(strokes), paperTemplate = "CORNELL"),
            NoteBlock.AudioBlock(id = "a1", filePath = "/tmp/fake.m4a", durationMs = 95000L),
            NoteBlock.ImageBlock(id = "m1", uriString = "content://media/external/images/1", extractedOcrText = "E = mc^2\nConservation law"),
            NoteBlock.PdfBlock(
                id = "p1",
                filePath = "/tmp/missing.pdf",
                fileName = "Lecture_04_Notes.pdf",
                pageCount = 12,
                currentPage = 0,
                annotationsJson = json.encodeToString(pdfAnnotations)
            )
        )

        // Include XML-hostile characters & forbidden control characters to confirm sanitization
        val longContent = (1..24).joinToString("\n") { "Paragraph line $it discussing <physics> & \"chemistry\" notes." } +
            "\u0000\u0007\u000B"

        val note = NoteEntity(
            id = "n1",
            title = "Quantum & Thermo <Overview> \u0000Review",
            content = longContent,
            folderId = null,
            blocksJson = json.encodeToString(blocks)
        )

        val baos = ByteArrayOutputStream()
        PptxExporter.export(context = null, note = note, outputStream = baos)
        val entries = unzipEntries(baos.toByteArray())

        // Core OpenXML hierarchy required by Keynote & PowerPoint
        val requiredParts = listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "ppt/presentation.xml",
            "ppt/_rels/presentation.xml.rels",
            "ppt/presProps.xml",
            "ppt/viewProps.xml",
            "ppt/tableStyles.xml",
            "ppt/theme/theme1.xml",
            "ppt/slideMasters/slideMaster1.xml",
            "ppt/slideMasters/_rels/slideMaster1.xml.rels",
            "ppt/slideLayouts/slideLayout1.xml",
            "ppt/slideLayouts/_rels/slideLayout1.xml.rels"
        )
        for (part in requiredParts) {
            assertTrue("Missing required PPTX part: $part", entries.containsKey(part))
        }

        // Parse & validate XML well-formedness for every XML and RELS entry
        val xmlEntries = entries.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }
        assertTrue("Expected slide XML files to be present", xmlEntries.keys.any { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") })
        val parsedDocs = xmlEntries.mapValues { (name, bytes) -> parseXml(name, bytes) }

        // Check presentation.xml has both sldMasterIdLst and matching sldIdLst
        val presDoc = parsedDocs.getValue("ppt/presentation.xml")
        assertEquals(1, presDoc.getElementsByTagNameNS("*", "sldMasterId").length)
        val slideIdNodes = presDoc.getElementsByTagNameNS("*", "sldId")
        val slideXmlKeys = entries.keys.filter { it.matches(Regex("""ppt/slides/slide\d+\.xml""")) }.sorted()
        assertTrue("Expected multiple paginated slides, got ${slideXmlKeys.size}", slideXmlKeys.size >= 6)
        assertEquals(slideXmlKeys.size, slideIdNodes.length)

        // Every slide must have its `.rels` pointing to ../slideLayouts/slideLayout1.xml AND valid grpSpPr/xfrm
        for (slideIdx in 1..slideXmlKeys.size) {
            val relKey = "ppt/slides/_rels/slide$slideIdx.xml.rels"
            assertTrue("Missing slide rels: $relKey", entries.containsKey(relKey))
            val relText = String(entries.getValue(relKey), Charsets.UTF_8)
            assertTrue(relText.contains("../slideLayouts/slideLayout1.xml"))

            val slideDoc = parsedDocs.getValue("ppt/slides/slide$slideIdx.xml")
            val grpNodes = slideDoc.getElementsByTagNameNS("*", "grpSpPr")
            assertEquals(1, grpNodes.length)
            assertTrue(
                "Slide $slideIdx grpSpPr must include <a:xfrm>",
                slideDoc.getElementsByTagNameNS("*", "xfrm").length >= 2
            )
            assertTrue(
                "Slide $slideIdx must contain rendered text runs",
                slideDoc.getElementsByTagNameNS("*", "t").length >= 2
            )
        }

        // Ensure checklist ticks, PDF notes, and sanitized title are preserved
        val slide1Text = String(entries.getValue("ppt/slides/slide1.xml"), Charsets.UTF_8)
        assertTrue(slide1Text.contains("Quantum &amp; Thermo &lt;Overview&gt; Review"))
        assertFalse(slide1Text.contains("\u0000"))

        val allSlideText = slideXmlKeys.joinToString("\n") { String(entries.getValue(it), Charsets.UTF_8) }
        assertTrue(allSlideText.contains("Exam Prep"))
        assertTrue(allSlideText.contains("Lecture_04_Notes.pdf"))
        assertTrue(allSlideText.contains("Key derivation &amp; proof summary"))
        assertTrue(allSlideText.contains("Audio Memo (01:35)"))
    }

    @Test
    fun `exportPptx adapts colors to active NoteGraph theme`() {
        val prevTheme = ThemeManager.currentTheme
        val prevEngine = ThemeManager.uiEngine
        try {
            val note = NoteEntity(id = "k", title = "Kindle Deck", content = "Paper tone check", folderId = null)

            // 1. Material You (Default) engine curated M3 palette
            ThemeManager.setUiEngine(UiEngineMode.MATERIAL_YOU)
            ThemeManager.setTheme(AppThemeMode.KINDLE_PARCHMENT)
            val myKindleOut = ByteArrayOutputStream()
            ExportManager.exportPptx(note, myKindleOut)
            val myKindleSlide = String(unzipEntries(myKindleOut.toByteArray()).getValue("ppt/slides/slide1.xml"), Charsets.UTF_8)
            assertTrue(myKindleSlide.contains("FFF8F4"))

            ThemeManager.setTheme(AppThemeMode.DEEP_PURPLE_AMOLED)
            val myAmoledOut = ByteArrayOutputStream()
            ExportManager.exportPptx(note, myAmoledOut)
            val myAmoledSlide = String(unzipEntries(myAmoledOut.toByteArray()).getValue("ppt/slides/slide1.xml"), Charsets.UTF_8)
            assertTrue(myAmoledSlide.contains("0F0E13"))
            assertTrue(myAmoledSlide.contains("D0BCFF"))

            // 2. Bespoke Luxury engine curated palette
            ThemeManager.setUiEngine(UiEngineMode.BESPOKE_LUXURY)
            ThemeManager.setTheme(AppThemeMode.KINDLE_PARCHMENT)
            val luxKindleOut = ByteArrayOutputStream()
            ExportManager.exportPptx(note, luxKindleOut)
            val luxKindleSlide = String(unzipEntries(luxKindleOut.toByteArray()).getValue("ppt/slides/slide1.xml"), Charsets.UTF_8)
            assertTrue(luxKindleSlide.contains("F4F1EA"))

            ThemeManager.setTheme(AppThemeMode.DEEP_PURPLE_AMOLED)
            val luxAmoledOut = ByteArrayOutputStream()
            ExportManager.exportPptx(note, luxAmoledOut)
            val luxAmoledSlide = String(unzipEntries(luxAmoledOut.toByteArray()).getValue("ppt/slides/slide1.xml"), Charsets.UTF_8)
            assertTrue(luxAmoledSlide.contains("000000"))
            assertTrue(luxAmoledSlide.contains("8B5CF6"))

            // 3. Classic UI engine gradient palette
            ThemeManager.setUiEngine(UiEngineMode.CLASSIC)
            ThemeManager.setTheme(AppThemeMode.KINDLE_PARCHMENT)
            val kindleOut = ByteArrayOutputStream()
            ExportManager.exportPptx(note, kindleOut)
            val kindleSlide = String(unzipEntries(kindleOut.toByteArray()).getValue("ppt/slides/slide1.xml"), Charsets.UTF_8)
            assertTrue(kindleSlide.contains("F7F4EC"))

            ThemeManager.setTheme(AppThemeMode.DEEP_PURPLE_AMOLED)
            val amoledOut = ByteArrayOutputStream()
            ExportManager.exportPptx(note, amoledOut)
            val amoledSlide = String(unzipEntries(amoledOut.toByteArray()).getValue("ppt/slides/slide1.xml"), Charsets.UTF_8)
            assertTrue(amoledSlide.contains("0B0714"))
        } finally {
            ThemeManager.setUiEngine(prevEngine)
            ThemeManager.setTheme(prevTheme)
        }
    }

    @Test
    fun `exportDocx and exportMarkdown handle rich note blocks cleanly`() {
        val note = NoteEntity(
            id = "d1",
            title = "Export Sanity",
            content = "Main body line",
            folderId = null,
            blocksJson = json.encodeToString(
                listOf<NoteBlock>(
                    NoteBlock.ChecklistBlock(
                        id = "c1",
                        title = "Tasks",
                        items = listOf(ChecklistItem("1", "Verify CI", isChecked = true))
                    )
                )
            )
        )

        val docxOut = ByteArrayOutputStream()
        ExportManager.exportDocx(note, docxOut)
        val docxEntries = unzipEntries(docxOut.toByteArray())
        assertTrue(docxEntries.containsKey("word/document.xml"))
        parseXml("word/document.xml", docxEntries.getValue("word/document.xml"))

        val mdOut = ByteArrayOutputStream()
        ExportManager.exportMarkdown(note, mdOut)
        val md = String(mdOut.toByteArray(), Charsets.UTF_8)
        assertTrue(md.contains("# Export Sanity"))
        assertTrue(md.contains("- [x] Verify CI"))
    }

    private fun unzipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory) {
                    result[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
            }
        }
        return result
    }

    private fun parseXml(name: String, bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw AssertionError("Invalid XML in $name: ${e.message}\n${String(bytes, Charsets.UTF_8)}", e)
        }
    }
}
