package com.wander.android.data.sources.musicbrainz

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

@Serializable
data class MusicBrainzSearchResponse(val artists: List<MusicBrainzArtist> = emptyList())

@Serializable
data class MusicBrainzArtist(
    val id: String,
    val name: String,
    /** MusicBrainz's own relevance score, 0..100 — see [MusicBrainzClient.searchArtist]. */
    val score: Int = 0,
    val disambiguation: String? = null,
    val tags: List<MusicBrainzTag> = emptyList()
)

@Serializable
data class MusicBrainzTag(val name: String, val count: Int = 0)

/**
 * MusicBrainz's public artist search — the one place in the app that can name an artist by a
 * stable, cross-backend id rather than by fuzzy name matching alone. See `ArtistIdentity` for why
 * name matching cannot always tell two same-named artists apart, and why a track with no backend
 * `artistId` at all (most local files) gets no bio or portrait today.
 *
 * Never called without `SecureStorage.isMusicBrainzLookupEnabled`: this sends the artist's name to
 * a third party, the same consent shape as LRCLIB lyrics lookups — see [CatalogRepository].
 */
@Singleton
class MusicBrainzClient @Inject constructor(
    private val client: HttpClient
) {
    /**
     * The single best match for [name], or null below [MIN_CONFIDENCE_SCORE].
     *
     * A low-confidence guess is worse than admitting nothing was found: it would hand the artist
     * screen somebody else's genre tags and disambiguation as if they were certain, the exact
     * failure mode `ArtistIdentity` already goes out of its way to avoid for name matching.
     */
    suspend fun searchArtist(name: String): Result<MusicBrainzArtist?> = runCatching {
        val response: MusicBrainzSearchResponse = client.get(BASE_URL) {
            // Required by MusicBrainz's API usage policy, so a misbehaving client can be traced
            // back to a real maintainer rather than blocked anonymously for everyone using it.
            header("User-Agent", USER_AGENT)
            parameter("query", "artist:\"$name\"")
            parameter("fmt", "json")
            parameter("limit", 1)
        }.body()
        response.artists.firstOrNull()?.takeIf { it.score >= MIN_CONFIDENCE_SCORE }
    }

    /**
     * The artist behind a MusicBrainz id already known to be right — Navidrome's own metadata
     * agent linked it, so there is no name to mismatch here the way [searchArtist] has to guard
     * against. Fetched with `inc=tags` so [MusicBrainzArtist.tags] arrives populated; the search
     * endpoint above already includes tags too, so this is only needed when the id came from
     * somewhere else.
     */
    suspend fun getArtist(mbid: String): Result<MusicBrainzArtist> = runCatching {
        client.get("$BASE_URL/$mbid") {
            header("User-Agent", USER_AGENT)
            parameter("fmt", "json")
            parameter("inc", "tags")
        }.body()
    }

    private companion object {
        const val BASE_URL = "https://musicbrainz.org/ws/2/artist"
        const val MIN_CONFIDENCE_SCORE = 90
        const val USER_AGENT = "Wanda-Android-Music-Player/1.0"
    }
}
