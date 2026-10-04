package com.wander.android.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.R
import com.wander.android.core.backup.CloudBackupScheduler
import com.wander.android.core.backup.CloudBackupStore
import com.wander.android.core.security.AgroVault
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.VaultBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * The Agro vault in Settings: the daily backup toggle, whether sign-ins go with it, backing up on
 * demand, and restoring one by hand. Restoring is never automatic — devices already share what
 * Agro syncs, so a backup is for a phone starting over, and that is the user's call to make.
 */
@HiltViewModel
internal class CloudBackupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: CloudBackupStore,
    private val scheduler: CloudBackupScheduler,
    private val secureStorage: SecureStorage
) : ViewModel() {

    val isAvailable: Boolean get() = store.isAvailable
    val auto: StateFlow<Boolean> = secureStorage.cloudBackup
    val includeAccounts: StateFlow<Boolean> = secureStorage.cloudBackupAccounts

    /** What Agro holds, newest first; null until the first answer. */
    private val _backups = MutableStateFlow<List<VaultBackup>?>(null)
    val backups: StateFlow<List<VaultBackup>?> = _backups.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _status = MutableStateFlow<BackupStatus?>(null)
    val status: StateFlow<BackupStatus?> = _status.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (!store.isAvailable) return
        viewModelScope.launch { store.list().onSuccess { _backups.value = it } }
    }

    /** Turning it on also backs up now, so the first one is not a day away. */
    fun setAuto(enabled: Boolean) {
        secureStorage.setCloudBackup(enabled)
        scheduler.apply(enabled)
        if (enabled) backUpNow()
    }

    fun setIncludeAccounts(enabled: Boolean) = secureStorage.setCloudBackupAccounts(enabled)

    fun backUpNow() = run {
        store.backUpNow().fold(
            onSuccess = {
                refresh()
                BackupStatus.Done(context.getString(R.string.cloud_backup_done))
            },
            onFailure = { BackupStatus.Failed(context.getString(R.string.cloud_backup_failed, it.message.orEmpty())) }
        )
    }

    fun restore(id: String) = run {
        val contents = store.restore(id)
        BackupStatus.Imported(contents.settings, contents.plays, contents.recaps, contents.tracks, contents.playlists)
    }

    private fun run(work: suspend () -> BackupStatus) {
        if (_busy.value) return
        _busy.value = true
        _status.value = null
        viewModelScope.launch {
            _status.value = try {
                work()
            } catch (_: AgroVault.VaultException) {
                BackupStatus.Failed(context.getString(R.string.cloud_backup_restore_failed, context.getString(R.string.cloud_backup_wrong_key)))
            } catch (e: IOException) {
                BackupStatus.Failed(context.getString(R.string.cloud_backup_restore_failed, e.message.orEmpty()))
            } finally {
                _busy.value = false
            }
        }
    }
}
