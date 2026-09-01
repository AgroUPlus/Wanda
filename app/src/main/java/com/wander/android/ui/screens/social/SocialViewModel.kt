package com.wander.android.ui.screens.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.repository.ListenAlongController
import com.wander.android.data.repository.ListenAlongSession
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.repository.SocialRepository
import com.wander.android.data.sources.agro.AgroFeedItem
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import com.wander.android.data.sources.agro.AgroProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel
internal class SocialViewModel @Inject constructor(
    private val repository: SocialRepository,
    private val listenAlong: ListenAlongController,
    secureStorage: SecureStorage
) : ViewModel() {

    private val me: String = secureStorage.agroUsername

    private val _state = MutableStateFlow(SocialUiState())
    val state: StateFlow<SocialUiState> = _state.asStateFlow()

    private val _search = MutableStateFlow(UserSearchState())
    val search: StateFlow<UserSearchState> = _search.asStateFlow()

    private val query = MutableStateFlow("")

    init {
        // The graph is read from Room, so the screen has content before any network call returns.
        combine(
            repository.friends,
            repository.requests,
            repository.nowPlaying,
            repository.isPaired,
            listenAlong.session,
            repository.feed,
            repository.feedLoadingMore,
            repository.feedExhausted
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val friends = values[0] as List<AgroProfile>
            @Suppress("UNCHECKED_CAST")
            val requests = values[1] as List<AgroProfile>
            @Suppress("UNCHECKED_CAST")
            val playing = values[2] as List<AgroFriendNowPlaying>
            val paired = values[3] as Boolean
            val session = values[4] as ListenAlongSession?
            @Suppress("UNCHECKED_CAST")
            val feed = values[5] as List<AgroFeedItem>
            val feedLoadingMore = values[6] as Boolean
            val feedExhausted = values[7] as Boolean
            _state.value.copy(
                loading = false,
                myUsername = me,
                isPaired = paired,
                friends = friends,
                incoming = requests.filter { !it.outgoing },
                outgoing = requests.filter { it.outgoing },
                nowPlaying = playing,
                session = session,
                feed = feed,
                feedLoadingMore = feedLoadingMore,
                feedExhausted = feedExhausted
            )
        }.onEach { _state.value = it }.launchIn(viewModelScope)

        // Typing a username is not a reason to query a public directory on every keystroke.
        query
            .debounce(SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            .onEach { runSearch(it) }
            .launchIn(viewModelScope)

        // Your own picture, for the header. A failure is not worth reporting: without it the
        // generated avatar stands in, which is what an account with no picture gets anyway.
        viewModelScope.launch {
            if (me.isNotBlank()) {
                repository.profile(me).getOrNull()?.let { profile ->
                    _state.value = _state.value.copy(myAvatarUrl = profile.avatarUrl)
                }
            }
        }

        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(isRefreshing = true)
        viewModelScope.launch {
            val result = repository.refresh()
            _state.value = _state.value.copy(
                isRefreshing = false,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    /**
     * Asks for more activity, which is what reaching the end of the list means.
     *
     * Safe to call repeatedly: the repository ignores a request while one is in flight or once
     * the server has run out, so the scroll listener does not need to debounce.
     */
    fun loadMoreFeed() {
        viewModelScope.launch { repository.loadMoreFeed() }
    }

    /** Re-reads only the feed. What a `FRIEND_PRESENCE` frame warrants. */
    fun refreshPresence() {
        viewModelScope.launch { repository.refreshPresence() }
    }

    fun onQueryChange(value: String) {
        query.value = value
        _search.value = _search.value.copy(query = value, error = null)
    }

    fun clearSearch() {
        query.value = ""
        _search.value = UserSearchState()
    }

    /**
     * Shows or hides the QR panel.
     *
     * Hiding revokes rather than only clearing the local copy: a code left live on the server is
     * a code that still works for anyone who photographed it, which is the one thing the short
     * lifetime exists to prevent.
     */
    fun toggleFriendCode() {
        val showing = !_search.value.showingCode
        _search.value = _search.value.copy(showingCode = showing, friendCode = null)
        if (!showing) revokeFriendCode()
    }

    /**
     * Mints a code, replacing whichever one was on screen.
     *
     * Cleared before the call so the panel shows it is working. A failure leaves it null, which
     * the panel renders as still loading — correct, because a code that could not be minted is
     * not a code the user should be shown as if it worked.
     */
    fun refreshFriendCode() {
        _search.value = _search.value.copy(friendCode = null)
        viewModelScope.launch {
            val minted = repository.createFriendCode().getOrNull()
            _search.value = _search.value.copy(friendCode = minted?.code)
        }
    }

    fun revokeFriendCode() {
        viewModelScope.launch { repository.revokeFriendCode() }
    }

    private suspend fun runSearch(term: String) {
        if (term.isBlank()) {
            _search.value = _search.value.copy(results = emptyList(), isSearching = false)
            return
        }
        _search.value = _search.value.copy(isSearching = true)
        repository.search(term).fold(
            onSuccess = { results ->
                _search.value = _search.value.copy(results = results, isSearching = false)
            },
            onFailure = { error ->
                _search.value = _search.value.copy(
                    isSearching = false,
                    error = error.message ?: "Could not search"
                )
            }
        )
    }

    /**
     * Sends a request, and says so at once.
     *
     * The server answers `false` for an existing edge, a block, or no such account without
     * distinguishing them — deliberately, so this cannot be used to probe for accounts — so there
     * is nothing more specific to report than that it did not go through.
     */
    fun sendRequest(username: String) {
        viewModelScope.launch {
            repository.sendRequest(username).onSuccess { sent ->
                _search.value = if (sent) {
                    _search.value.copy(requested = _search.value.requested + username)
                } else {
                    _search.value.copy(error = "That request could not be sent.")
                }
            }
        }
    }

    fun accept(username: String) {
        viewModelScope.launch { repository.accept(username) }
    }

    /** Declines a request, or ends a friendship — the same call for both. */
    fun remove(username: String) {
        viewModelScope.launch { repository.remove(username) }
    }

    fun startListenAlong(host: String) {
        viewModelScope.launch {
            listenAlong.start(host).onFailure { error ->
                _state.value = _state.value.copy(
                    error = error.message ?: "Could not listen along with that friend"
                )
            }
        }
    }

    fun stopListenAlong() {
        viewModelScope.launch { listenAlong.stop() }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
