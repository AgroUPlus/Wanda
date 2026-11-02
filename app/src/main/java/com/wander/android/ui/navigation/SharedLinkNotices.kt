package com.wander.android.ui.navigation

import androidx.annotation.StringRes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Messages about something shared into the app from another one, for the shell's snackbar.
 *
 * A channel rather than a replaying flow, for the same cold-start reason [DeepLinkRouter] replays:
 * a share can start the process before the shell composes. A buffered channel holds the message
 * until the shell collects it, then hands it over exactly once — a replay would show it again
 * every time the shell started collecting anew.
 */
@Singleton
class SharedLinkNotices @Inject constructor() {
    private val channel = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = channel.receiveAsFlow()

    fun post(@StringRes message: Int) {
        channel.trySend(message)
    }
}
