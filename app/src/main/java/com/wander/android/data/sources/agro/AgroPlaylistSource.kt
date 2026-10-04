package com.wander.android.data.sources.agro

import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistDiscovery
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistMirror
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistRepository
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.SourceCapabilities
import com.wander.android.data.sources.StreamInfo
import android.os.SystemClock
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Agro playlists that are not a Wanda playlist's copy: those shared with this account that it
 * follows, and its own made in Agro's dashboard.
 *
 * A source in its own right so that a followed playlist is handled like a YouTube Music or Deezer
 * one: listed in the library, opened by id, and copied into a Wanda playlist by the same Convert
 * action. It serves no tracks of its own: each track of a followed playlist belongs to whichever
 * source it was matched to. Everything is read from the copies in Room; see
 * [SharedPlaylistRepository]. Edits go through `SharedPlaylistEditor`, which knows each
 * playlist's permissions, so this source does not claim [SourceCapabilities.playlistWrite].
 */
@Singleton
class AgroPlaylistSource @Inject constructor(
    secureStorage: SecureStorage,
    private val shared: SharedPlaylistRepository,
    private val discovery: SharedPlaylistDiscovery
) : IMusicSource {
    override val sourceType = SourceType.AGRO
    override val displayName = SourceType.AGRO.displayName
    override val capabilities = SourceCapabilities(playlists = true, holdsTracks = false)
    override val isConfigured: StateFlow<Boolean> = secureStorage.agroConfigured

    override suspend fun search(query: String): Result<List<UnifiedTrack>> = Result.success(emptyList())

    override suspend fun getStreamInfo(trackId: String): Result<StreamInfo> =
        Result.failure(UnsupportedOperationException("$displayName holds playlists, not tracks"))

    /**
     * What Room holds, after picking up any playlist made in Agro's dashboard since the last
     * look. Offline, the copies already kept are still listed; only with none at all is the
     * failure to reach Agro the answer.
     */
    override suspend fun getPlaylists(): Result<List<UnifiedPlaylist>> {
        val discovered = if (discoveryDue()) discovery.discover() else Result.success(Unit)
        val kept = shared.followed()
        return discovered.exceptionOrNull()?.takeIf { kept.isEmpty() }?.let { Result.failure(it) } ?: Result.success(kept)
    }

    /** Straight from the copy in Room: no discovery, and nothing else listed along the way. */
    override suspend fun getPlaylist(playlistId: String): Result<UnifiedPlaylist?> =
        Result.success(SharedPlaylistMirror.agroIdOf(playlistId)?.let { shared.followedPlaylist(it) })

    /**
     * Looking for playlists made or followed elsewhere asks Agro for every playlist this account can
     * open, every public one on the server included. Worth doing now and then, not on every visit
     * to the library — a playlist made elsewhere is rare, and the next look finds it.
     */
    private fun discoveryDue(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastDiscovery.get()
        return (last == 0L || now - last >= DISCOVERY_INTERVAL_MS) && lastDiscovery.compareAndSet(last, now)
    }

    private val lastDiscovery = AtomicLong(0L)

    override suspend fun getPlaylistTracks(playlistId: String): Result<List<UnifiedTrack>> {
        val agroId = SharedPlaylistMirror.agroIdOf(playlistId) ?: return Result.success(emptyList())
        return Result.success(shared.tracks(agroId).map { it.track })
    }

    /** Deletes this account's own playlist from Agro; unfollows anyone else's. */
    override suspend fun deletePlaylist(playlistId: String): Result<Unit> {
        val agroId = SharedPlaylistMirror.agroIdOf(playlistId)
            ?: return Result.failure(IllegalArgumentException("Not a shared playlist"))
        return shared.remove(agroId)
    }

    private companion object {
        const val DISCOVERY_INTERVAL_MS = 10 * 60 * 1000L
    }
}
