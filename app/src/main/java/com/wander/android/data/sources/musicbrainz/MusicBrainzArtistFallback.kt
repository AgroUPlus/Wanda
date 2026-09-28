package com.wander.android.data.sources.musicbrainz

import android.util.Log
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.repository.ArtistFetch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A last-resort artist page for a name no configured source could produce an id for — chiefly
 * local files, which carry no backend `artistId` at all and so never reach
 * `CatalogRepository.artistDetails` in the first place. MusicBrainz is asked by *name* instead,
 * which is the one case this app can still improve on "no id, no page, no bio, forever".
 *
 * Split out of [com.wander.android.data.repository.CatalogRepository], which had grown past the
 * file-size cap. Gated on consent internally, so every caller reads as the one place an outbound
 * MusicBrainz call can happen — the same shape as `LyricsRepository`'s own LRCLIB gate.
 */
@Singleton
class MusicBrainzArtistFallback @Inject constructor(
    private val client: MusicBrainzClient,
    private val secureStorage: SecureStorage
) {
    suspend fun lookup(name: String): ArtistFetch {
        if (!secureStorage.isMusicBrainzLookupEnabled.value) return ArtistFetch.NotFound
        val match = client.searchArtist(name).fold(
            onSuccess = { it },
            onFailure = { error ->
                Log.w(TAG, "MusicBrainz search failed for $name: ${error.javaClass.simpleName}")
                return ArtistFetch.Failed(error)
            }
        ) ?: return ArtistFetch.NotFound
        return ArtistFetch.Found(
            ArtistDetails(
                id = "$MUSICBRAINZ_PREFIX${match.id}",
                name = match.name,
                bio = match.disambiguation?.takeIf { it.isNotBlank() },
                musicBrainzId = match.id,
                // Free: the search response already carries tags, unlike a plain artist lookup.
                genres = match.tags.topGenres()
            )
        )
    }

    /**
     * Genre tags for an artist whose MusicBrainz id is already trusted — Navidrome's own metadata
     * agent linked it, so this is enrichment of a page that already exists, not identification.
     * Empty on any failure, consent-off included: a missing genre line costs nothing the way a
     * missing bio does, so this never needs [ArtistFetch.Failed] the way [lookup] does.
     */
    suspend fun genresFor(musicBrainzId: String): List<String> {
        if (!secureStorage.isMusicBrainzLookupEnabled.value) return emptyList()
        return client.getArtist(musicBrainzId)
            .onFailure { Log.w(TAG, "MusicBrainz genre lookup failed for $musicBrainzId: ${it.javaClass.simpleName}") }
            .getOrNull()
            ?.tags
            ?.topGenres()
            .orEmpty()
    }

    private fun List<MusicBrainzTag>.topGenres(): List<String> =
        sortedByDescending { it.count }.take(MAX_GENRES).map { it.name }

    private companion object {
        const val TAG = "MusicBrainzFallback"
        const val MAX_GENRES = 4
    }
}

/** Namespaced so this never collides with a real backend's own id prefix. */
internal const val MUSICBRAINZ_PREFIX = "mbid:"
