package com.wander.android.data.repository

import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.sources.agro.MissingTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cover art for the tracks another device is offering.
 *
 * The offer itself carries none: Agro indexes a file's tags, not its artwork, and its own cover
 * endpoint only holds pictures for albums it has *archived* — which on a peer-to-peer deployment
 * is none of them. So the pictures have to come from somewhere the app already looked.
 *
 * That somewhere is Room. A track missing as a *file* is very often still known as a *recording* —
 * the same song reached the library from YouTube Music or Navidrome, with a cover, and what the
 * peer is offering is the local copy of it. Matching is by [TrackDeduplicator.isSameRecording]'s
 * own normalisation rather than on raw strings, so "Song (Remastered 2011)" still finds "Song".
 *
 * Whatever is not found is simply absent. A card showing three covers and a gap is honest; an
 * invented placeholder in the fourth slot is not.
 */
@Singleton
class SyncOfferArtwork @Inject constructor(
    private val trackDao: TrackDao
) {

    /**
     * Up to [limit] covers for [tracks], in the order the tracks were offered.
     *
     * Distinct: four copies of one album's cover is not a collage, it is a mistake that looks like
     * a rendering bug.
     */
    suspend fun covers(tracks: List<MissingTrack>, limit: Int = 4): List<String> =
        withContext(Dispatchers.IO) {
            val seen = mutableSetOf<String>()
            val found = mutableListOf<String>()
            for (track in tracks) {
                if (found.size >= limit) break
                val artwork = coverFor(track) ?: continue
                if (seen.add(artwork)) found += artwork
            }
            found
        }

    private suspend fun coverFor(track: MissingTrack): String? {
        val wantedTitle = TrackDeduplicator.normalizeTitle(track.title)
        val wantedVariants = TrackDeduplicator.variantsOf(track.title)
        return trackDao.getTracksByArtistOnce(track.artist)
            .asSequence()
            .filter { candidate ->
                TrackDeduplicator.normalizeTitle(candidate.title) == wantedTitle &&
                    TrackDeduplicator.variantsOf(candidate.title) == wantedVariants
            }
            .mapNotNull(TrackEntity::artworkUrl)
            .firstOrNull { it.isNotBlank() }
    }
}
