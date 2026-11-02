package com.wander.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.cache.DownloadScheduler
import com.wander.android.core.notification.WorkProgressNotification
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.work.WorkControls
import com.wander.android.data.repository.ManualDownloadRepository
import com.wander.android.data.repository.StorageButlerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [DownloadSettingsCard] and [StreamCacheSizeRow]: what downloads by itself, over which
 * network, how much disk it may take, and wiping it all.
 *
 * Every switch that changes what the periodic sweep does re-applies its schedule at once, so a
 * change never waits behind a sweep queued under the old rules.
 */
@HiltViewModel
internal class DownloadSettingsViewModel @Inject constructor(
    secureStorage: SecureStorage,
    private val downloadScheduler: DownloadScheduler,
    private val workControls: WorkControls,
    private val storageRepository: StorageButlerRepository,
    private val manualDownloads: ManualDownloadRepository
) : ViewModel() {

    private val prefs = secureStorage.downloads

    val autoDownloadLiked: StateFlow<Boolean> = prefs.autoDownloadLiked
    val autoDownloadPredicted: StateFlow<Boolean> = prefs.autoDownloadPredicted
    val wifiOnly: StateFlow<Boolean> = prefs.wifiOnly
    val limitBytes: StateFlow<Long> = prefs.limitBytes
    val streamCacheBytes: StateFlow<Long> = prefs.streamCacheBytes
    val storageFull: StateFlow<Boolean> = prefs.storageFull

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    fun setAutoDownloadLiked(enabled: Boolean) {
        prefs.setAutoDownloadLiked(enabled)
        downloadScheduler.scheduleAutoDownload()
    }

    fun setAutoDownloadPredicted(enabled: Boolean) {
        prefs.setAutoDownloadPredicted(enabled)
        downloadScheduler.scheduleAutoDownload()
    }

    fun setWifiOnly(enabled: Boolean) {
        prefs.setWifiOnly(enabled)
        downloadScheduler.scheduleAutoDownload()
        // The manual job carries the rule it was queued under; see `requeueManual`.
        viewModelScope.launch { manualDownloads.requeueWaiting() }
    }

    /** Read by the worker before each track, so the next sweep honours it; no reschedule needed. */
    fun setLimitBytes(bytes: Long) = prefs.setLimitBytes(bytes)

    fun setStreamCacheBytes(bytes: Long) = prefs.setStreamCacheBytes(bytes)

    /**
     * Stops any download in flight first, so a track finishing as the files go cannot record
     * itself as downloaded afterwards. Cancelling by tag also drops the periodic sweep, which is
     * then scheduled again under the current settings.
     */
    fun deleteAllDownloads(onDone: () -> Unit) {
        if (_isDeleting.value) return
        viewModelScope.launch {
            _isDeleting.value = true
            workControls.cancel(WorkProgressNotification.Kind.DOWNLOAD)
            storageRepository.deleteAllDownloads()
            // The room is back; the next run finds out for itself if it is not enough.
            prefs.setStorageFull(false)
            downloadScheduler.scheduleAutoDownload()
            _isDeleting.value = false
            onDone()
        }
    }
}
