package com.wander.android.ui.navigation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Something the shell's own chrome should do, asked for from outside it — a launcher shortcut. */
enum class ShellRequest {
    /** Put the cursor in the dock's search field. */
    FOCUS_SEARCH,

    /** Open the listen sheet that recognizes what is playing nearby. */
    LISTEN
}

/**
 * Holds the one [ShellRequest] waiting for the shell, the way [LinkConfirmations] holds a
 * confirmation: a StateFlow, so a shortcut that starts the process cold is still waiting when the
 * dock first composes, and cleared by whoever carries it out so it happens once.
 */
@Singleton
class ShellRequests @Inject constructor() {
    private val _pending = MutableStateFlow<ShellRequest?>(null)
    val pending: StateFlow<ShellRequest?> = _pending.asStateFlow()

    fun request(request: ShellRequest) {
        _pending.value = request
    }

    /** Clears [request] if it is still the pending one. */
    fun consume(request: ShellRequest) {
        _pending.compareAndSet(request, null)
    }
}
