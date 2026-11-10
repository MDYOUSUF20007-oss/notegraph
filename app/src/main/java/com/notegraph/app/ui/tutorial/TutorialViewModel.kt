package com.notegraph.app.ui.tutorial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notegraph.app.data.local.dao.LinkDao
import com.notegraph.app.data.local.dao.NoteDao
import com.notegraph.app.data.local.entity.LinkEntity
import com.notegraph.app.data.local.entity.NoteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val noteDao: NoteDao,
    private val linkDao: LinkDao
) : ViewModel() {

    val noteCount: StateFlow<Int> = noteDao.getAllNotes()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Creates a connected 3-node starter cluster so users can immediately inspect
     * [[Wiki-Links]], exact-title mentions, #nested/tags, and glowing graph edges.
     */
    fun createDemoGraphNotes(onComplete: (createdNew: Boolean) -> Unit) {
        viewModelScope.launch {
            val createdNew = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val existingPhysics = noteDao.getNoteByTitle("Physics Overview")
                val existingCalculus = noteDao.getNoteByTitle("Calculus Basics")
                val existingWaves = noteDao.getNoteByTitle("Wave Equations")

                if (existingPhysics != null && existingCalculus != null && existingWaves != null) {
                    return@withContext false
                }

                val physics = existingPhysics ?: NoteEntity(
                    id = UUID.randomUUID().toString(),
                    title = "Physics Overview",
                    content = "Core concepts connect directly to [[Calculus Basics]] and [[Wave Equations]].\n\n" +
                        "Every [[Title]] becomes a clickable chip and draws an edge in the Knowledge Graph.\n\n#study/physics #guide",
                    folderId = null,
                    createdAt = now - 3000L,
                    updatedAt = now - 3000L
                )

                val calculus = existingCalculus ?: NoteEntity(
                    id = UUID.randomUUID().toString(),
                    title = "Calculus Basics",
                    content = "Derivatives and integrals support Physics Overview and help derive [[Wave Equations]].\n\n" +
                        "Mentioning another note's exact title automatically creates a graph connection.\n\n#study/math #guide",
                    folderId = null,
                    createdAt = now - 2000L,
                    updatedAt = now - 2000L
                )

                val waves = existingWaves ?: NoteEntity(
                    id = UUID.randomUUID().toString(),
                    title = "Wave Equations",
                    content = "Built on [[Calculus Basics]] inside [[Physics Overview]].\n\n" +
                        "Check the Graph & Metadata card at the bottom to jump via backlinks.\n\n#study/physics #guide",
                    folderId = null,
                    createdAt = now - 1000L,
                    updatedAt = now - 1000L
                )

                if (existingPhysics == null) noteDao.insertNote(physics)
                if (existingCalculus == null) noteDao.insertNote(calculus)
                if (existingWaves == null) noteDao.insertNote(waves)

                linkDao.deleteLinksForNote(physics.id)
                linkDao.insertLinks(
                    listOf(
                        LinkEntity(
                            sourceNoteId = physics.id,
                            targetNoteTitle = calculus.title,
                            targetNoteId = calculus.id,
                            isResolved = true
                        ),
                        LinkEntity(
                            sourceNoteId = physics.id,
                            targetNoteTitle = waves.title,
                            targetNoteId = waves.id,
                            isResolved = true
                        )
                    )
                )

                linkDao.deleteLinksForNote(calculus.id)
                linkDao.insertLinks(
                    listOf(
                        LinkEntity(
                            sourceNoteId = calculus.id,
                            targetNoteTitle = physics.title,
                            targetNoteId = physics.id,
                            isResolved = true
                        ),
                        LinkEntity(
                            sourceNoteId = calculus.id,
                            targetNoteTitle = waves.title,
                            targetNoteId = waves.id,
                            isResolved = true
                        )
                    )
                )

                linkDao.deleteLinksForNote(waves.id)
                linkDao.insertLinks(
                    listOf(
                        LinkEntity(
                            sourceNoteId = waves.id,
                            targetNoteTitle = calculus.title,
                            targetNoteId = calculus.id,
                            isResolved = true
                        ),
                        LinkEntity(
                            sourceNoteId = waves.id,
                            targetNoteTitle = physics.title,
                            targetNoteId = physics.id,
                            isResolved = true
                        )
                    )
                )

                true
            }
            onComplete(createdNew)
        }
    }
}
