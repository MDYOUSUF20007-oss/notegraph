package com.notegraph.app

import com.notegraph.app.data.local.entity.ChecklistItem
import com.notegraph.app.data.local.entity.FolderEntity
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.data.sync.DesktopNoteSidecar
import com.notegraph.app.data.sync.SyncEngine
import com.notegraph.app.data.sync.SyncEngine.toSyncFolder
import com.notegraph.app.data.sync.SyncEngine.toSyncNote
import com.notegraph.app.data.sync.SyncFolder
import com.notegraph.app.data.sync.SyncNote
import com.notegraph.app.data.sync.SyncPackageV2
import com.notegraph.app.data.sync.SyncTombstone
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {

    @Test
    fun `mergePackages applies deterministic symmetric LWW across devices`() {
        val localNote = NoteEntity(
            id = "n1",
            title = "Local Title",
            content = "Local Body",
            folderId = null,
            createdAt = 100L,
            updatedAt = 500L
        )
        val remoteOlder = SyncNote(
            id = "n1",
            title = "Old Remote",
            content = "Stale",
            createdAt = 100L,
            updatedAt = 300L
        )
        val remoteNewer = SyncNote(
            id = "n2",
            title = "New From Windows",
            content = "Links to [[Local Title]]",
            createdAt = 200L,
            updatedAt = 900L
        )

        val res = SyncEngine.mergePackages(
            localFolders = emptyList(),
            localNotes = listOf(localNote),
            localTombstones = emptyList(),
            localSidecar = emptyMap(),
            remote = SyncPackageV2(
                platform = "win32",
                deviceName = "Windows PC",
                notes = listOf(remoteOlder, remoteNewer)
            ),
            nowMs = 1000L
        )

        assertEquals(2, res.notes.size)
        assertEquals("Local Title", res.notes.first { it.id == "n1" }.title)
        assertEquals("New From Windows", res.notes.first { it.id == "n2" }.title)
        assertEquals(1, res.summary.newNotesAdded)
        assertEquals(1, res.summary.notesMerged)

        // Symmetric equal-timestamp tie-breaking check: both orders pick identical winner
        val eqA = NoteEntity(id = "eq1", title = "Alpha", content = "A", folderId = null, createdAt = 100L, updatedAt = 500L)
        val eqB = NoteEntity(id = "eq1", title = "Zeta", content = "Z", folderId = null, createdAt = 100L, updatedAt = 500L)
        val side1 = SyncEngine.mergePackages(
            localFolders = emptyList(),
            localNotes = listOf(eqA),
            localTombstones = emptyList(),
            localSidecar = emptyMap(),
            remote = SyncPackageV2(notes = listOf(eqB.toSyncNote(null))),
            nowMs = 1000L
        ).notes.first().title
        val side2 = SyncEngine.mergePackages(
            localFolders = emptyList(),
            localNotes = listOf(eqB),
            localTombstones = emptyList(),
            localSidecar = emptyMap(),
            remote = SyncPackageV2(notes = listOf(eqA.toSyncNote(null))),
            nowMs = 1000L
        ).notes.first().title
        assertEquals(side1, side2)
        assertEquals("Zeta", side1)
    }

    @Test
    fun `tombstone deletes older note and folder cleanly and unfiles notes in deleted folder`() {
        val folder = FolderEntity(id = "f1", name = "Work", createdAt = 100L, updatedAt = 200L)
        val childFolder = FolderEntity(id = "f2", name = "Sub", parentId = "f1", createdAt = 150L, updatedAt = 200L)
        val noteInDeletedFolder = NoteEntity(id = "n1", title = "Task", content = "Body", folderId = "f1", createdAt = 100L, updatedAt = 250L)
        val deletedNote = NoteEntity(id = "n2", title = "Old Draft", content = "Bye", folderId = null, createdAt = 100L, updatedAt = 250L)

        val remote = SyncPackageV2(
            folders = listOf(folder.toSyncFolder(), childFolder.toSyncFolder()),
            notes = listOf(noteInDeletedFolder.toSyncNote(null), deletedNote.toSyncNote(null)),
            tombstones = listOf(
                SyncTombstone(id = "f1", kind = "folder", deletedAt = 500L),
                SyncTombstone(id = "n2", kind = "note", deletedAt = 500L)
            )
        )

        val res = SyncEngine.mergePackages(
            localFolders = listOf(folder, childFolder),
            localNotes = listOf(noteInDeletedFolder, deletedNote),
            localTombstones = emptyList(),
            localSidecar = emptyMap(),
            remote = remote,
            nowMs = 600L
        )

        assertTrue(res.deletedFolderIds.contains("f1"))
        assertTrue(res.deletedNoteIds.contains("n2"))
        assertEquals(1, res.folders.size)
        assertEquals("f2", res.folders[0].id)
        assertNull(res.folders[0].parentId)
        assertEquals(1, res.notes.size)
        assertNull(res.notes[0].folderId)
        assertEquals(2, res.summary.deletionsApplied)
    }

    @Test
    fun `note updated strictly after tombstone supersedes deletion and revives`() {
        val revivedNote = NoteEntity(
            id = "n_revive",
            title = "Revived",
            content = "Fresh edits",
            folderId = null,
            createdAt = 100L,
            updatedAt = 900L
        )
        val res = SyncEngine.mergePackages(
            localFolders = emptyList(),
            localNotes = listOf(revivedNote),
            localTombstones = listOf(SyncTombstone(id = "n_revive", kind = "note", deletedAt = 400L)),
            localSidecar = emptyMap(),
            remote = SyncPackageV2(),
            nowMs = 1000L
        )

        assertEquals(1, res.notes.size)
        assertFalse(res.deletedNoteIds.contains("n_revive"))
        assertTrue(res.tombstones.none { it.id == "n_revive" })
    }

    @Test
    fun `desktop board json converts to Android NoteBlocks and preserves sidecar on round-trip`() {
        val desktopBoard: JsonObject = buildJsonObject {
            put("gridStyle", "DOTS")
            put("activePageIndex", 0)
            put("pages", buildJsonArray {
                add(buildJsonObject {
                    put("id", "pg1")
                    put("name", "Slide 1")
                    put("cards", buildJsonArray {
                        add(buildJsonObject {
                            put("id", "chk_card")
                            put("kind", "checklist")
                            put("title", "Sprint Goals")
                            put("items", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", "it1")
                                    put("text", "Optimize Laser")
                                    put("isChecked", true)
                                })
                            })
                        })
                        add(buildJsonObject {
                            put("id", "extra_txt")
                            put("kind", "note")
                            put("title", "Sidebar")
                            put("content", "Hello Mac")
                        })
                    })
                    put("strokes", buildJsonArray {
                        add(buildJsonObject {
                            put("colorHex", "#38BDF8")
                            put("strokeWidth", 5f)
                            put("points", buildJsonArray {
                                add(buildJsonObject { put("x", 10f); put("y", 20f) })
                                add(buildJsonObject { put("x", 50f); put("y", 60f) })
                            })
                        })
                    })
                })
            })
        }

        val remoteDesktopNote = SyncNote(
            id = "desk1",
            title = "Mac Board",
            content = "Main body",
            followUpAt = "2026-12-01",
            createdAt = 100L,
            updatedAt = 500L,
            blocksJson = "[]",
            board = desktopBoard
        )

        val merged = SyncEngine.mergePackages(
            localFolders = emptyList(),
            localNotes = emptyList(),
            localTombstones = emptyList(),
            localSidecar = emptyMap(),
            remote = SyncPackageV2(platform = "darwin", notes = listOf(remoteDesktopNote)),
            nowMs = 600L
        )

        val androidNote = merged.notes.single()
        val blocks = SyncEngine.codec.decodeFromString<List<NoteBlock>>(androidNote.blocksJson)
        assertTrue(blocks.any { it is NoteBlock.ChecklistBlock && it.title == "Sprint Goals" })
        assertTrue(blocks.any { it is NoteBlock.TextBlock && it.text.contains("Hello Mac") })
        assertTrue(blocks.any { it is NoteBlock.InkBlock })

        val sidecar = merged.sidecar["desk1"]
        assertNotNull(sidecar)
        assertEquals("2026-12-01", sidecar?.followUpAt)
        assertNotNull(sidecar?.board)

        // When Android does NOT edit after boardUpdatedAt, outgoing SyncNote retains desktop board intact
        val roundTripClean = androidNote.toSyncNote(sidecar)
        assertNotNull(roundTripClean.board)

        // When Android edits note at newer updatedAt, stale board is dropped so Android blocks become authoritative
        val androidEdited = androidNote.copy(updatedAt = 800L)
        val roundTripEdited = androidEdited.toSyncNote(sidecar)
        assertNull(roundTripEdited.board)
    }

    @Test
    fun `extractLinks rebuilds wiki links and mentions while ignoring trashed notes`() {
        val checklistJson = SyncEngine.codec.encodeToString<List<NoteBlock>>(
            listOf(
                NoteBlock.ChecklistBlock(
                    id = "c1",
                    title = "Read",
                    items = listOf(ChecklistItem(id = "i1", text = "Review [[Quantum Mechanics]]"))
                )
            )
        )
        val n1 = NoteEntity(id = "n1", title = "Physics", content = "See Thermodynamics and [[Missing Topic]]", folderId = null, blocksJson = checklistJson)
        val n2 = NoteEntity(id = "n2", title = "Quantum Mechanics", content = "", folderId = null)
        val n3 = NoteEntity(id = "n3", title = "Thermodynamics", content = "", folderId = null)
        val nTrash = NoteEntity(id = "n4", title = "Archived", content = "Links [[Physics]]", folderId = null, isTrash = true)

        val links = SyncEngine.extractLinks(listOf(n1, n2, n3, nTrash).filter { !it.isTrash })

        assertTrue(links.any { it.sourceNoteId == "n1" && it.targetNoteId == "n2" && it.isResolved })
        assertTrue(links.any { it.sourceNoteId == "n1" && it.targetNoteId == "n3" && it.isResolved })
        assertTrue(links.any { it.sourceNoteId == "n1" && it.targetNoteId == null && it.targetNoteTitle == "Missing Topic" && !it.isResolved })
        assertFalse(links.any { it.sourceNoteId == "n4" })
    }

    @Test
    fun `parsePackageOrLegacy decodes both SyncPackageV2 object and legacy notes array`() {
        val v2Json = SyncEngine.codec.encodeToString(
            SyncPackageV2(
                schemaVersion = 2,
                deviceName = "Studio Mac",
                platform = "darwin",
                folders = listOf(SyncFolder(id = "f1", name = "Study", createdAt = 1L, updatedAt = 1L)),
                notes = listOf(SyncNote(id = "n1", title = "Hello", createdAt = 1L, updatedAt = 2L))
            )
        )
        val parsedV2 = SyncEngine.parsePackageOrLegacy(v2Json)
        assertEquals(2, parsedV2.schemaVersion)
        assertEquals(1, parsedV2.folders.size)
        assertEquals(1, parsedV2.notes.size)

        val legacyJson = SyncEngine.codec.encodeToString(
            listOf(NoteEntity(id = "leg1", title = "Legacy Note", content = "Hi", folderId = null, createdAt = 5L, updatedAt = 6L))
        )
        val parsedLegacy = SyncEngine.parsePackageOrLegacy(legacyJson)
        assertEquals(1, parsedLegacy.schemaVersion)
        assertEquals("legacy", parsedLegacy.platform)
        assertEquals("leg1", parsedLegacy.notes.single().id)
    }
}
