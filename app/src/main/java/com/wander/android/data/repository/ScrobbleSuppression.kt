package com.wander.android.data.repository

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether what is playing right now should count as this account's listening.
 *
 * Incognito mode is a setting the user turns on; this is narrower and automatic. While tuned in to
 * a friend, the track is *their* choice — recording it would put their taste in your play counts,
 * your top artists and your scrobbles, and taste-match would then partly be measuring itself.
 *
 * A separate object rather than a flag on either party because `MusicRepository` has to read it and
 * `ListenAlongController` has to write it, and the controller already depends on the repository
 * through [ListenAlongResolver]. Injecting the controller back into the repository would close that
 * loop; a shared flag does not.
 *
 * In memory only: a process that died mid-session is not still in one.
 */
@Singleton
class ScrobbleSuppression @Inject constructor() {
    private val suppressed = AtomicBoolean(false)

    val isSuppressed: Boolean get() = suppressed.get()

    fun set(active: Boolean) {
        suppressed.set(active)
    }
}
