package com.wander.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.repository.LibrarySyncRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.sources.agro.AgroSessionApi
import com.wander.android.data.sources.agro.MissingTrack
import com.wander.android.data.sources.local.LocalMusicSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WanderAppViewModel @Inject constructor(
    private val localSource: LocalMusicSource,
    musicRepository: MusicRepository,
    shareRepository: ShareRepository,
    private val librarySync: LibrarySyncRepository,
    private val sessionApi: AgroSessionApi,
    private val secureStorage: SecureStorage
) : ViewModel() {

    /** Library writes that failed to reach their backend — shown as a snackbar, not swallowed. */
    val writeErrors = musicRepository.writeErrors

    /**
     * Share links minted anywhere in the app. Collected in one place because turning a URL into a
     * share sheet needs an Activity, which no ViewModel should hold.
     */
    val shareLinks = shareRepository.links

    /** A share the backend refused — usually sharing disabled server-side. */
    val shareErrors = shareRepository.errors

    // ── Library sync offers ─────────────────────────────────────────────────────────────────

    private val _syncOffer = MutableStateFlow<List<MissingTrack>>(emptyList())
    val syncOffer: StateFlow<List<MissingTrack>> = _syncOffer.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetchingSync: StateFlow<Boolean> = _isFetching.asStateFlow()

    /**
     * Dismissed for this visit only, mirroring how the resume card behaves: an offer declined now
     * should be offerable again next time the app is opened, not suppressed forever.
     */
    private var dismissedOffer = false

    /** Asks what this device is missing. Cheap, metadata only. */
    fun refreshSyncOffer() {
        if (dismissedOffer || !librarySync.isEnabled) return
        viewModelScope.launch {
            _syncOffer.value = librarySync.missingHere().getOrDefault(emptyList())
        }
    }

    fun acceptSyncOffer() {
        val tracks = _syncOffer.value
        if (tracks.isEmpty() || _isFetching.value) return
        viewModelScope.launch {
            _isFetching.value = true
            librarySync.fetchMissing(tracks)
                .onSuccess { _syncOffer.value = emptyList() }
                .onFailure { _writeErrors.tryEmit(it.message ?: "Couldn't fetch those tracks.") }
            _isFetching.value = false
        }
    }

    fun dismissSyncOffer() {
        dismissedOffer = true
        _syncOffer.value = emptyList()
    }

    /**
     * Picks up the share-link domain a paired Agro server publishes, so it is set once for the
     * whole fleet rather than typed into every player.
     *
     * Silent on every failure. This is a convenience that Agro *may* provide: an unpaired server,
     * an unreachable one, or one too old to know the field all leave the locally configured
     * domain — or no rewriting at all — exactly as it was. Sharing never depends on Agro.
     */
    fun refreshShareSettings() {
        if (!secureStorage.agroConfigured.value) return
        viewModelScope.launch {
            val settings = sessionApi.syncedSettings().getOrNull() ?: return@launch
            val domain = settings.shareDomain.orEmpty().takeIf { settings.shareEnabled }.orEmpty()
            secureStorage.setAgroShareSettings(domain, settings.shareHosts.orEmpty())
        }
    }

    /** Local failures worth a snackbar, alongside the repository's own. */
    private val _writeErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * Everything the user should hear about, from here and from the sync repository.
     *
     * The repository's errors were emitted but never collected — the comment above claimed they
     * were included and they were not, so a per-file upload failure said nothing at all and sync
     * simply looked like it had stopped.
     */
    val syncErrors: SharedFlow<String> =
        merge(_writeErrors.asSharedFlow(), librarySync.errors)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 0)

    /** Decides whether the app opens on the welcome flow or straight into the library. */
    val hasCompletedSetup: StateFlow<Boolean> = secureStorage.hasCompletedSetup

    /**
     * Runs once the audio permission is granted. The scan is incremental, so calling it on every
     * cold start costs almost nothing after the first time.
     */
    fun onAudioPermissionGranted() {
        viewModelScope.launch { localSource.refresh() }
    }
}
