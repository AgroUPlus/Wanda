package com.wander.android.ui.screens.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.R
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.podcast.PodcastHit
import com.wander.android.data.podcast.PodcastIndexClient
import com.wander.android.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the search screen shows. The first two are the gates the listener must pass before anything leaves the device. */
sealed interface PodcastSearchState {
    data object NeedsConsent : PodcastSearchState
    data object NeedsKey : PodcastSearchState
    data object Idle : PodcastSearchState
    data object Searching : PodcastSearchState

    @Immutable
    data class Results(val hits: List<PodcastHit>) : PodcastSearchState

    @Immutable
    data class Failed(val reason: String) : PodcastSearchState
}

@HiltViewModel
class PodcastSearchViewModel @Inject constructor(
    private val index: PodcastIndexClient,
    private val podcasts: PodcastRepository,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val lastSearch = MutableStateFlow<PodcastSearchState>(PodcastSearchState.Idle)

    /** The two gates take precedence over any search result: switching the setting off clears the screen at once. */
    val state: StateFlow<PodcastSearchState> = combine(
        secureStorage.isPodcastIndexEnabled,
        secureStorage.podcastIndexConfigured,
        lastSearch
    ) { enabled, configured, current ->
        when {
            !enabled -> PodcastSearchState.NeedsConsent
            !configured -> PodcastSearchState.NeedsKey
            else -> current
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PodcastSearchState.NeedsConsent)

    /** Feed addresses already subscribed, so a result can say so instead of offering to subscribe again. */
    val subscribed: StateFlow<Set<String>> = podcasts.subscriptions
        .map { list -> list.mapTo(mutableSetOf()) { it.feedUrl } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /** The listener's explicit yes to sending searches to PodcastIndex. */
    fun allowSearch() = secureStorage.setPodcastIndexEnabled(true)

    fun saveKey(key: String, secret: String) = secureStorage.setPodcastIndexCredentials(key, secret)

    fun search(term: String) {
        if (term.isBlank()) return
        lastSearch.value = PodcastSearchState.Searching
        viewModelScope.launch {
            lastSearch.value = index.search(term.trim()).fold(
                onSuccess = { PodcastSearchState.Results(it) },
                onFailure = { PodcastSearchState.Failed(it.message.orEmpty()) }
            )
        }
    }

    fun subscribe(hit: PodcastHit) {
        viewModelScope.launch {
            podcasts.subscribe(hit.feedUrl)
                .onSuccess { say(R.string.podcasts_added, it.title) }
                .onFailure { say(R.string.podcasts_add_failed, it.message.orEmpty()) }
        }
    }

    private fun say(@StringRes res: Int, vararg args: Any) {
        _messages.tryEmit(UiMessage(res, args.toList()))
    }
}
