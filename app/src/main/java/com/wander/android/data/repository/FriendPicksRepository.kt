package com.wander.android.data.repository

import com.wander.android.core.database.dao.DropDao
import com.wander.android.core.database.entity.DropEntity
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The songs friends sent, as playable tracks. A drop is only a title and an artist, so each is
 * looked up on the connected sources and kept only if a result has the same title. A song already
 * resolved this session is not searched again.
 */
@Singleton
class FriendPicksRepository @Inject constructor(
    private val drops: DropDao,
    private val music: MusicRepository
) {
    private val resolved = ConcurrentHashMap<String, UnifiedTrack>()

    suspend fun tracks(limit: Int): List<UnifiedTrack> {
        val recent = drops.recentIncoming(limit).distinctBy { it.trackTitle.lowercase() to it.artistName.lowercase() }
        return withTimeoutOrNull(SEARCH_TIMEOUT_MILLIS) {
            coroutineScope { recent.map { drop -> async { resolve(drop) } }.map { it.await() } }
        }.orEmpty().filterNotNull()
    }

    private suspend fun resolve(drop: DropEntity): UnifiedTrack? =
        resolved[drop.id] ?: music.searchAllSources("${drop.trackTitle} ${drop.artistName}")
            .firstOrNull { it.title.equals(drop.trackTitle, ignoreCase = true) }
            ?.also { resolved[drop.id] = it }

    private companion object {
        const val SEARCH_TIMEOUT_MILLIS = 8_000L
    }
}
