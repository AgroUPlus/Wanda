package com.wander.android.data.christian

import android.util.Log
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Songs for the Christian shelf: the curated artists of the chosen languages, found by searching
 * each artist's name. Search results are saved to the library by the search itself, and an artist
 * already looked up this session is not searched again — refreshing only reshuffles.
 */
@Singleton
class ChristianShelfRepository @Inject constructor(private val music: MusicRepository) {

    private val found = ConcurrentHashMap<String, List<UnifiedTrack>>()

    suspend fun tracks(languages: Set<ChristianLanguage>, limit: Int): List<UnifiedTrack> {
        val artists = ChristianArtists.all
            .filter { languages.isEmpty() || it.language in languages }
            .shuffled()
            .take(ARTISTS_PER_SHELF)
        // The limit is per artist, not over the lot: one slow search used to throw away every
        // other artist's results along with its own, and the shelf came back empty.
        val songs = coroutineScope {
            artists.map { artist ->
                async { withTimeoutOrNull(SEARCH_TIMEOUT_MILLIS) { songsBy(artist) } }
            }.map { it.await() }
        }
        val timedOut = songs.count { it == null }
        if (timedOut > 0) Log.w(TAG, "$timedOut of ${artists.size} artist searches timed out")
        return songs.filterNotNull()
            .flatMap { it.shuffled().take(SONGS_PER_ARTIST) }
            .distinctBy { it.id }
            .shuffled()
            .take(limit)
    }

    private suspend fun songsBy(artist: ChristianArtist): List<UnifiedTrack> =
        found[artist.name] ?: music.searchAllSources(artist.name)
            .filter { ChristianArtists.credits(it.artist, artist.name) }
            .also { if (it.isNotEmpty()) found[artist.name] = it }

    private companion object {
        const val ARTISTS_PER_SHELF = 6
        const val SONGS_PER_ARTIST = 3
        const val SEARCH_TIMEOUT_MILLIS = 8_000L
        const val TAG = "ChristianShelf"
    }
}
