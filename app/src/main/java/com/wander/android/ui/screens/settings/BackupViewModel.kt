package com.wander.android.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.backup.SettingsBackupStore
import com.wander.android.core.security.AgroVault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * Backup and restore, as its own ViewModel.
 *
 * Not folded into [SettingsViewModel], which is 558 lines and owns the settings themselves. This
 * owns a short-lived operation over all of them, which is a different job with a different
 * lifetime — and adding a seventh concern to a file already twice the project's size limit would
 * be the wrong direction.
 */
@HiltViewModel
internal class BackupViewModel @Inject constructor(
    private val store: SettingsBackupStore
) : ViewModel() {

    private val _status = MutableStateFlow<BackupStatus?>(null)
    val status: StateFlow<BackupStatus?> = _status.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    /**
     * [includeHistory] carries the listening history and the saved recaps as well as the settings.
     *
     * On by default, because a restore that silently loses a lifetime of listening is the bug this
     * was added to fix. It is still a switch: the history is the bulk of the file and the most
     * personal thing in it, so somebody handing a backup to a new phone in a shop can leave it out.
     */
    fun export(target: Uri, passphrase: String, includeHistory: Boolean = true) {
        run(
            work = { store.export(target, passphrase, includeHistory); BackupStatus.Exported },
            failure = { "Export failed: ${it.readableMessage()}" }
        )
    }

    fun import(source: Uri, passphrase: String) {
        run(
            work = {
                val contents = store.import(source, passphrase)
                BackupStatus.Imported(contents.settings, contents.plays, contents.recaps)
            },
            failure = { "Import failed: ${it.readableMessage()}" }
        )
    }

    /**
     * Argon2id takes hundreds of milliseconds by design, so both operations are slow enough to see.
     * The busy flag is what stops a second tap starting a concurrent write to the same file.
     */
    private fun run(
        work: suspend () -> BackupStatus,
        failure: (Throwable) -> String
    ) {
        if (_isBusy.value) return
        _isBusy.value = true
        _status.value = null
        viewModelScope.launch {
            _status.value = try {
                work()
            } catch (e: AgroVault.VaultException) {
                BackupStatus.Failed(failure(e))
            } catch (e: IOException) {
                BackupStatus.Failed(failure(e))
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun clearStatus() {
        _status.value = null
    }
}

/**
 * The message text is built here rather than carrying the exception to the UI, so nothing that
 * reaches the screen can carry a path, a URL or a key — see the logging rule in AGENTS.md.
 *
 * A [AgroVault.VaultException] is reported as a wrong passphrase because that is overwhelmingly
 * what it is. GCM genuinely cannot distinguish a wrong key from a tampered file, and saying
 * "wrong passphrase, or the file is damaged" is both honest and the order the reader should try.
 */
private fun Throwable.readableMessage(): String = when (this) {
    is AgroVault.VaultException -> "wrong passphrase, or the file is damaged"
    else -> message ?: "the file could not be read"
}

internal sealed interface BackupStatus {
    data object Exported : BackupStatus

    /**
     * [settings] is what was actually written, not what the file held: entries this build does not
     * understand are skipped, and reporting the file's own count would overstate the restore.
     *
     * [plays] is likewise what the file carried, which is not the same as what was inserted — a
     * play this device already had is not added twice. Both numbers are zero for a backup written
     * before listening history travelled, which is a true statement about that file.
     */
    data class Imported(val settings: Int, val plays: Int = 0, val recaps: Int = 0) : BackupStatus

    data class Failed(val message: String) : BackupStatus
}
