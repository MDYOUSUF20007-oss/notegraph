package com.notegraph.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.notegraph.app.data.local.dao.NoteDao
import com.notegraph.app.data.local.entity.NoteEntity
import com.notegraph.app.data.sync.LanHostState
import com.notegraph.app.data.sync.LanSyncManager
import com.notegraph.app.data.sync.SyncPreferencesSnapshot
import com.notegraph.app.data.sync.SyncSummary
import com.notegraph.app.data.sync.VaultSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val noteDao: NoteDao,
    private val vaultSyncManager: VaultSyncManager,
    private val lanSyncManager: LanSyncManager
) : ViewModel() {

    val syncPrefs: StateFlow<SyncPreferencesSnapshot> = vaultSyncManager.prefsState
    val lanHostState: StateFlow<LanHostState> = lanSyncManager.hostState

    fun getLastPeerAddress(): String = lanSyncManager.getLastPeerAddress()

    suspend fun getAllNotesOnce(): List<NoteEntity> = noteDao.getAllNotesOnce()

    suspend fun restoreNotes(notes: List<NoteEntity>) {
        for (note in notes) {
            noteDao.insertNote(note)
        }
    }

    suspend fun exportZipBackup(destUri: Uri): Int = vaultSyncManager.exportZipBackup(destUri)

    suspend fun importAndMergeZip(sourceUri: Uri): SyncSummary = vaultSyncManager.importAndMergeZip(sourceUri)

    fun setSavedFolderUri(uri: Uri?) {
        vaultSyncManager.setSavedFolderUri(uri)
    }

    fun setAutoSyncOnOpen(enabled: Boolean) {
        vaultSyncManager.setAutoSyncOnOpen(enabled)
    }

    suspend fun syncWithSafFolder(treeUri: Uri): SyncSummary = vaultSyncManager.syncWithSafFolder(treeUri)

    fun startLanHostServer() {
        lanSyncManager.startHostServer()
    }

    fun stopLanHostServer() {
        lanSyncManager.stopHostServer()
    }

    fun regenerateLanPin(): String = lanSyncManager.regeneratePin()

    suspend fun syncWithLanPeer(hostPort: String, pin: String): Result<SyncSummary> =
        lanSyncManager.syncWithPeer(hostPort, pin)
}
