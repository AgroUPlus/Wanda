package com.wander.android.data.repository

import com.wander.android.core.notification.ListeningTogetherNotifier
import com.wander.android.data.sources.agro.Jam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Announces people joining and leaving the jam this device is in.
 *
 * Only changes *within* one jam are news. Joining a room is not "five people joined", and the room
 * ending is not "everyone left" — so the comparison resets whenever the jam's id changes.
 */
@Singleton
internal class JamMembershipWatcher @Inject constructor(
    private val repository: JamRepository,
    private val notifier: ListeningTogetherNotifier
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val started = AtomicBoolean(false)
    private var previous: Jam? = null

    /** Starts watching. Safe to call repeatedly. */
    fun ensureRunning() {
        if (!started.compareAndSet(false, true)) return
        repository.jam.onEach(::onJam).launchIn(scope)
    }

    private fun onJam(jam: Jam?) {
        val before = previous?.takeIf { it.id == jam?.id }
        previous = jam
        if (jam == null || before == null) return
        val size = jam.members.size
        jam.members.filterNot { it in before.members }.forEach { notifier.jamMemberJoined(it, size) }
        before.members.filterNot { it in jam.members }.forEach { notifier.jamMemberLeft(it, size) }
    }
}
