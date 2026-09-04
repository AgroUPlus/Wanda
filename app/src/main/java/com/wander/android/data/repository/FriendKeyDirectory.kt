package com.wander.android.data.repository

import com.wander.android.data.sources.agro.AgroProfileApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The identity keys this account's friends have published, kept for as long as they are useful.
 *
 * `SocialRepository.deviceKeys` deliberately does not cache: a drop is sealed once, and sealing it
 * to a stale key list produces a message nobody can ever read. Presence is the opposite shape. It
 * is sealed on every track change, so a key list that is a few minutes old costs at worst one
 * track a friend's newest device cannot open — and asking the server for every friend's keys on
 * every track change would be a burst of requests per song, which is exactly the chatter the
 * heartbeat interval exists to avoid.
 *
 * Hence a cache with a short life, and an [invalidate] for the moments when being out of date is
 * not acceptable: the friend list changed, or the account went away.
 */
@Singleton
internal class FriendKeyDirectory @Inject constructor(
    private val profileApi: AgroProfileApi
) {
    private val mutex = Mutex()
    private val cached = mutableMapOf<String, Entry>()

    /**
     * `deviceId -> publicKey` for one friend, from cache when it is fresh enough.
     *
     * An empty map is a real answer — a friend who has published no key — and is cached like any
     * other, so a friend on an older client does not provoke a lookup per track. A *failed* lookup
     * is not cached: it is not an answer, and the next track change should ask again.
     */
    suspend fun keysFor(username: String): Map<String, String> {
        val now = System.currentTimeMillis()
        mutex.withLock {
            cached[username]?.let { if (now - it.fetchedAt < TTL_MS) return it.keys }
        }
        val keys = profileApi.deviceKeys(username).getOrNull() ?: return emptyMap()
        val byDevice = keys
            .filter { it.deviceId.isNotBlank() && it.publicKey.isNotBlank() }
            .associate { it.deviceId to it.publicKey }
        mutex.withLock { cached[username] = Entry(byDevice, now) }
        return byDevice
    }

    /** Forgets everything. Called when the friend list changes and when the account is unpaired. */
    suspend fun invalidate() = mutex.withLock { cached.clear() }

    private data class Entry(val keys: Map<String, String>, val fetchedAt: Long)

    private companion object {
        /**
         * Long enough that a track change does not mean a round trip per friend, short enough that
         * a friend adding a device is visible within a few songs.
         */
        const val TTL_MS = 5 * 60 * 1000L
    }
}
