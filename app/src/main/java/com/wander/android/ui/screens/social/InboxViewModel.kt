package com.wander.android.ui.screens.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.repository.DropsRepository
import com.wander.android.data.repository.ListenAlongResolver
import com.wander.android.data.repository.SocialRepository
import com.wander.android.data.sources.agro.AgroDrop
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The inbox, read from Room and refreshed from the network behind it.
 *
 * The cache is what the screen shows, so it renders offline and renders instantly. [refresh] runs
 * on open because the socket is closed while the app is backgrounded — anything that arrived
 * overnight is learned here, not from a push.
 */
@HiltViewModel
internal class InboxViewModel @Inject constructor(
    private val drops: DropsRepository,
    private val resolver: ListenAlongResolver,
    private val playerConnection: PlayerConnection,
    private val social: SocialRepository,
    secureStorage: SecureStorage
) : ViewModel() {

    private val me: String = secureStorage.agroUsername

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Why a drop could not be played. Nothing is said when one plays — the audio says it. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                drops.threads,
                drops.unreadByFriend,
                drops.unreadCount,
                // The friend list is already cached in Room, so this costs a map rather than a
                // request — and without it every name in the inbox is a bare `@handle`.
                social.friends
            ) { threads, unreadByFriend, unread, friends ->
                InboxSnapshot(threads, unreadByFriend, unread, friends.associateBy { it.username.lowercase() })
            }.collect { snapshot ->
                _state.value = _state.value.copy(
                    threads = snapshot.threads,
                    unreadByFriend = snapshot.unreadByFriend,
                    unread = snapshot.unread,
                    people = snapshot.people,
                    loading = false,
                    me = me
                )
            }
        }
        // Your own avatar, for the bubbles you sent. A failure here is not worth reporting: the
        // fallback is the generated avatar, which is what an account without a picture gets anyway.
        viewModelScope.launch {
            if (me.isNotBlank()) {
                social.profile(me).getOrNull()?.let { profile ->
                    _state.value = _state.value.copy(myAvatarUrl = profile.avatarUrl)
                }
            }
        }
        refresh()
    }

    private data class InboxSnapshot(
        val threads: List<AgroDrop>,
        val unreadByFriend: Map<String, Int>,
        val unread: Int,
        val people: Map<String, com.wander.android.data.sources.agro.AgroProfile>
    )

    fun refresh() {
        viewModelScope.launch { drops.refresh() }
    }

    /**
     * Marks one read.
     *
     * The repository writes the local row first, so the list updates from Room without waiting —
     * a failed call is corrected by the next refresh rather than being reported, because there is
     * nothing the reader could do about it.
     */
    fun markRead(id: String) {
        viewModelScope.launch { drops.markRead(id) }
    }

    /**
     * Takes a message out of the conversation, for this account only.
     *
     * There is no "delete for everyone" behind this and the wording does not pretend otherwise:
     * the row is marked archived locally and the server keeps the other side's copy. It replaced
     * an archive button that sat permanently in every incoming bubble doing exactly this, which
     * spent a control on something people do rarely.
     */
    fun remove(id: String) {
        viewModelScope.launch { drops.archive(id) }
    }

    /**
     * Opens one exchange.
     *
     * Refreshed from the server as well as read from Room, because a thread includes messages
     * this account sent from *another* device — which its own inbox refresh has never seen.
     */
    fun openThread(username: String) {
        _state.value = _state.value.copy(openWith = username, conversation = emptyList())
        viewModelScope.launch {
            drops.refreshConversation(username)
        }
        viewModelScope.launch {
            drops.conversation(username).collect { thread ->
                // Guarded: the previous thread's flow is still running until this coroutine is
                // cancelled, and without this a slow one could overwrite the thread just opened.
                if (_state.value.openWith == username) {
                    _state.value = _state.value.copy(conversation = thread)
                }
            }
        }
    }

    fun closeThread() {
        _state.value = _state.value.copy(openWith = null, conversation = emptyList())
    }

    /**
     * Reacts to something a friend sent, or takes the reaction back.
     *
     * Tapping the emoji already on a message clears it, which is the behaviour every messaging
     * app has taught people to expect and costs nothing to honour.
     */
    fun react(drop: AgroDrop, emoji: String) {
        val next = if (drop.reaction == emoji) null else emoji
        viewModelScope.launch { drops.react(drop.id, next) }
    }

    /**
     * Plays what somebody sent, and marks it read for having been opened.
     *
     * A drop carries a description rather than a reference — the sender may have been playing from
     * a backend this device has never heard of — so the track has to be *found* here. That is the
     * same problem listen-along solves, so it uses the same resolver: your own library first, then
     * YouTube Music, and a real match required rather than the top search hit.
     *
     * Goes through `PlayerConnection.play`, which means that while you are in a jam this proposes
     * the track to the room instead of playing it here. That is the app's rule for every play, and
     * quietly exempting drops from it would be the surprise.
     */
    fun play(drop: AgroDrop) {
        if (drop.isUnread) markRead(drop.id)
        _state.value = _state.value.copy(resolving = drop.id)
        viewModelScope.launch {
            val resolved = resolver.resolve(drop.trackTitle, drop.artistName)
            _state.value = _state.value.copy(resolving = null)
            if (resolved == null) {
                _messages.tryEmit("Couldn't find “${drop.trackTitle}” in your library or on YouTube Music.")
                return@launch
            }
            playerConnection.play(listOf(resolved.track))
        }
    }
}
