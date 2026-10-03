package com.wander.android.data.repository

import com.wander.android.core.notification.ListeningTogetherNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who is listening along with *you* — the host's side of a listen-along session.
 *
 * The server pushes the full list to the host whenever someone starts or stops following, so this
 * only ever replaces it. Held in memory: after a restart it is empty until the next change, which
 * errs toward showing nothing rather than naming someone who has already left.
 */
@Singleton
internal class ListenerPresence @Inject constructor(
    private val incognito: IncognitoRepository,
    private val notifier: ListeningTogetherNotifier
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _listeners = MutableStateFlow<List<String>>(emptyList())

    /** The people following this account. */
    val listeners: StateFlow<List<String>> = _listeners.asStateFlow()

    init {
        // Incognito ends every session server-side, but the server tells only the *listeners* —
        // so a switch flipped in Settings or on another device would otherwise leave the bar
        // naming them, and bring them back the moment incognito was turned off again.
        incognito.isIncognito
            .filter { it }
            .onEach { forgetAll() }
            .launchIn(scope)
    }

    /** One `LISTEN_ALONG` frame addressed to this account as the host. */
    fun onListeners(names: List<String>) {
        val before = _listeners.value
        _listeners.value = names
        names.filterNot { it in before }.forEach(notifier::listenerJoined)
        before.filterNot { it in names }.forEach(notifier::listenerLeft)
    }

    /** Ends every session following this account, by going incognito. */
    suspend fun goIncognito(): Result<Unit> = incognito.set(true).onSuccess { forgetAll() }

    private fun forgetAll() {
        notifier.clearListeners(_listeners.value)
        _listeners.value = emptyList()
    }
}
