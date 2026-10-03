package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A cover for a song known only by its title and artist, borrowed from what is already in Room.
 *
 * Tracks that reach the app as bare metadata — another device's offer, an item in a shared
 * playlist — carry no artwork, but the same recording very often reached the library from YouTube
 * Music or Navidrome with a cover. Matching is by [TrackDeduplicator]'s own normalisation rather
 * than raw strings, so "Song (Remastered 2011)" still finds "Song".
 *
 * Failing that, a track off the same album by the same artist lends its cover: an album's tracks
 * share one. Whatever is not found is simply absent — never a picture of some other record.
 */
@Singleton
class RecordingArtwork @Inject constructor(
    private val trackDao: TrackDao
) {
    suspend fun coverFor(title: String, artist: String, album: String? = null): String? {
        if (artist.isBlank()) return null
        val candidates = trackDao.getTracksByArtistOnce(artist)
        val wantedTitle = TrackDeduplicator.normalizeTitle(title)
        val wantedVariants = TrackDeduplicator.variantsOf(title)
        val sameRecording = candidates.firstCover { candidate ->
            TrackDeduplicator.normalizeTitle(candidate.title) == wantedTitle &&
                TrackDeduplicator.variantsOf(candidate.title) == wantedVariants
        }
        if (sameRecording != null || album.isNullOrBlank()) return sameRecording
        return candidates.firstCover { it.album?.equals(album, ignoreCase = true) == true }
    }

    private fun List<TrackEntity>.firstCover(predicate: (TrackEntity) -> Boolean): String? =
        asSequence().filter(predicate).mapNotNull(TrackEntity::artworkUrl).firstOrNull { it.isNotBlank() }
}
