package com.wander.android.core.playback.browse

import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.LibraryBrowseRepository
import com.wander.android.data.repository.MusicRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The tracks a [VoiceQuery] plays.
 *
 * The library first, from Room: what someone asks their own player for by name is most often
 * something they already have, and Room answers instantly and offline. Only when the library has
 * nothing does the query go to the connected sources, the same search the search screen runs —
 * which persists what it finds, as every search does.
 *
 * An empty list means nothing matched anywhere; the caller says so rather than playing
 * something else in its place.
 */
@Singleton
internal class VoiceSearch @Inject constructor(
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val libraryBrowse: LibraryBrowseRepository,
    private val musicRepository: MusicRepository
) {

    suspend fun tracksFor(query: VoiceQuery): List<UnifiedTrack> = when (query) {
        // "Play some music" is a shuffle of the library, not a guess at a song.
        VoiceQuery.Anything -> libraryBrowse.randomTracks(emptySet(), MAX_TRACKS)
        is VoiceQuery.Album -> libraryAlbum(query.album) ?: byText(query.album)
        is VoiceQuery.Artist -> libraryArtist(query.artist) ?: byText(query.artist)
        is VoiceQuery.Text -> byText(query.query)
    }

    /** The best-matching library album, in its own order. */
    private suspend fun libraryAlbum(name: String): List<UnifiedTrack>? =
        albumDao.searchLibraryAlbums(name, 1).firstOrNull()
            ?.let { trackDao.getTracksInAlbum(it.id) }
            ?.takeIf { it.isNotEmpty() }
            ?.map(TrackEntity::toUnifiedTrack)

    /** The artist's library tracks, most played first. */
    private suspend fun libraryArtist(name: String): List<UnifiedTrack>? =
        trackDao.getTracksByArtistOnce(name)
            .filterNot(TrackEntity::isEpisode)
            .sortedByDescending(TrackEntity::playCount)
            .take(MAX_TRACKS)
            .takeIf { it.isNotEmpty() }
            ?.map(TrackEntity::toUnifiedTrack)

    private suspend fun byText(query: String): List<UnifiedTrack> {
        val library = trackDao.searchTracks(query, MAX_TRACKS).map(TrackEntity::toUnifiedTrack)
        return library.ifEmpty { musicRepository.searchAllSources(query).take(MAX_TRACKS) }
    }

    private companion object {
        const val MAX_TRACKS = 50
    }
}
