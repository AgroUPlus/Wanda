package com.wander.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.security.ScrobbleAccount
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.sync.ScrobbleForwardScheduler
import com.wander.android.data.repository.ScrobbleForwarding
import com.wander.android.data.repository.ScrobbleService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where a Last.fm sign-in has got to. */
internal sealed interface LastFmStep {
    data object Idle : LastFmStep
    /** This build has no key, so the user's own is needed first. */
    data object NeedsKey : LastFmStep
    /** The approval page is open; [token] is what to exchange once the user is back. */
    data class Approving(val token: String, val url: String) : LastFmStep
}

/** Connecting, pausing and signing out of ListenBrainz and Last.fm. */
@HiltViewModel
internal class ScrobblingViewModel @Inject constructor(
    private val forwarding: ScrobbleForwarding,
    private val scheduler: ScrobbleForwardScheduler,
    private val secureStorage: SecureStorage
) : ViewModel() {

    val listenBrainz: StateFlow<ScrobbleAccount> = secureStorage.scrobbling.listenBrainz
    val lastFm: StateFlow<ScrobbleAccount> = secureStorage.scrobbling.lastFm
    val signedOut: StateFlow<Map<ScrobbleService, String>> = forwarding.signedOut

    private val _lastFmStep = MutableStateFlow<LastFmStep>(LastFmStep.Idle)
    val lastFmStep: StateFlow<LastFmStep> = _lastFmStep.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** What the last connection attempt failed with, for the dialog that made it. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun connectListenBrainz(token: String, onDone: () -> Unit) = working {
        forwarding.connectListenBrainz(token).fold(onSuccess = { onDone() }, onFailure = { _error.value = it.message })
    }

    /** Starts Last.fm's sign-in: the user's own key first if this build has none, then approval. */
    fun startLastFm(onOpen: (String) -> Unit) {
        if (!forwarding.lastFmHasKey) {
            _lastFmStep.value = LastFmStep.NeedsKey
            return
        }
        working {
            forwarding.beginLastFm().fold(
                onSuccess = { (token, url) ->
                    _lastFmStep.value = LastFmStep.Approving(token, url)
                    onOpen(url)
                },
                onFailure = { _error.value = it.message }
            )
        }
    }

    fun saveLastFmKey(key: String, secret: String, onOpen: (String) -> Unit) {
        secureStorage.scrobbling.setLastFmApp(key, secret)
        _lastFmStep.value = LastFmStep.Idle
        startLastFm(onOpen)
    }

    fun finishLastFm() {
        val step = _lastFmStep.value as? LastFmStep.Approving ?: return
        working {
            forwarding.finishLastFm(step.token).fold(
                onSuccess = { _lastFmStep.value = LastFmStep.Idle },
                onFailure = { _error.value = it.message }
            )
        }
    }

    fun cancelLastFm() {
        _lastFmStep.value = LastFmStep.Idle
        _error.value = null
    }

    fun setEnabled(service: ScrobbleService, enabled: Boolean) {
        when (service) {
            ScrobbleService.LISTENBRAINZ -> secureStorage.scrobbling.setListenBrainzEnabled(enabled)
            ScrobbleService.LASTFM -> secureStorage.scrobbling.setLastFmEnabled(enabled)
        }
        // Back on: whatever was played while paused goes now.
        if (enabled) scheduler.forwardSoon()
    }

    fun disconnect(service: ScrobbleService) = when (service) {
        ScrobbleService.LISTENBRAINZ -> secureStorage.scrobbling.disconnectListenBrainz()
        ScrobbleService.LASTFM -> secureStorage.scrobbling.disconnectLastFm()
    }

    fun clearError() {
        _error.value = null
    }

    private fun working(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }
}
