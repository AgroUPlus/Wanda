package com.wander.android.ui.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes asked for from outside the composition — a tapped notification, so far.
 *
 * The `NavController` lives inside `WanderApp` and an Activity has no business reaching into it, so
 * the Activity publishes a route here and the shell collects it. The same shape `ShareRepository`
 * uses for share links, and for the same reason: the thing that *decides* and the thing that can
 * *act* are in different places.
 *
 * Replay of 1 rather than 0. A notification can start the process cold, and the Activity resolves
 * the intent before the shell has composed — without a replay the very first request, which is the
 * one the user actually tapped, would be emitted into nothing.
 */
@Singleton
class DeepLinkRouter @Inject constructor() {

    private val _routes = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val routes: SharedFlow<String> = _routes.asSharedFlow()

    fun request(route: String) {
        _routes.tryEmit(route)
    }

    /**
     * Forgets a delivered route.
     *
     * Called once the shell has navigated, so the replayed value cannot send the user back to the
     * inbox every time the app is brought forward again.
     */
    fun consume() {
        _routes.resetReplayCache()
    }
}
