package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** An artist this account follows. */
internal data class AgroArtistSubscription(
    val artistId: String,
    val displayName: String,
    /** What the name normalises to server-side; two spellings of one artist share it. */
    val normName: String,
    /** A namespaced id at the source it came from, such as `ytm:UC…`, when one is known. */
    val externalId: String? = null
)

/** Something a followed artist has put out since the watermark the caller passed. */
internal data class AgroArtistRelease(
    val recordingId: String,
    val artistId: String,
    val artist: String,
    val title: String?,
    val album: String?,
    /** The catalogue position this was published at. The highest one seen is the next watermark. */
    val updatedAt: Long
)

/**
 * Following artists, and asking what they have put out.
 *
 * Subscribing is by name rather than by id, because a name is what the app has: an artist page
 * opened from a track knows what the tag said and nothing more. The server creates the row if it
 * has never seen the name, which matters most for an artist nobody has published yet — exactly the
 * one worth being told about first.
 *
 * [newReleases] is polled rather than pushed. Agro can only reach a device over the sync socket,
 * which is open while the app is on screen, so anything that has to arrive while it is closed has
 * to be fetched by a job on this end — see `ArtistReleaseWorker`.
 */
@Singleton
internal class AgroArtistsApi @Inject constructor(
    private val graphQl: AgroGraphQl
) {
    suspend fun subscribed(): Result<List<AgroArtistSubscription>> = graphQl.execute(
        """
        query Subscribed(${'$'}userId: String!) {
            subscribedArtists(userId: ${'$'}userId) { $ARTIST_FIELDS }
        }
        """.trimIndent(),
        buildJsonObject { put("userId", graphQl.userId) }
    ).map { data ->
        (data["subscribedArtists"] as? JsonArray).orEmpty().map { it.jsonObject.toSubscription() }
    }

    suspend fun isSubscribed(artist: String): Result<Boolean> = graphQl.execute(
        """
        query IsSubscribed(${'$'}userId: String!, ${'$'}artist: String!) {
            isSubscribedToArtist(userId: ${'$'}userId, artist: ${'$'}artist)
        }
        """.trimIndent(),
        buildJsonObject {
            put("userId", graphQl.userId)
            put("artist", artist)
        }
    ).map { data -> data.bool("isSubscribedToArtist") }

    suspend fun subscribe(
        artist: String,
        externalId: String? = null
    ): Result<AgroArtistSubscription?> = graphQl.execute(
        """
        mutation Subscribe(${'$'}userId: String!, ${'$'}artist: String!, ${'$'}externalId: String) {
            subscribeArtist(userId: ${'$'}userId, artist: ${'$'}artist, externalId: ${'$'}externalId) {
                $ARTIST_FIELDS
            }
        }
        """.trimIndent(),
        buildJsonObject {
            put("userId", graphQl.userId)
            put("artist", artist)
            put("externalId", externalId)
        }
    ).map { data ->
        data["subscribeArtist"]?.jsonObject?.toSubscription()
    }

    suspend fun unsubscribe(artistId: String): Result<Boolean> = graphQl.execute(
        """
        mutation Unsubscribe(${'$'}userId: String!, ${'$'}artistId: String!) {
            unsubscribeArtist(userId: ${'$'}userId, artistId: ${'$'}artistId)
        }
        """.trimIndent(),
        buildJsonObject {
            put("userId", graphQl.userId)
            put("artistId", artistId)
        }
    ).map { data -> data.bool("unsubscribeArtist") }

    /**
     * Releases published after [since], oldest first.
     *
     * Oldest first is the server's ordering and the reason the watermark works: a client that has
     * been away walks forward through everything it missed instead of seeing only the newest and
     * silently skipping the rest.
     */
    suspend fun newReleases(since: Long, limit: Int = 100): Result<List<AgroArtistRelease>> =
        graphQl.execute(
            """
            query NewReleases(${'$'}userId: String!, ${'$'}since: Int!, ${'$'}limit: Int) {
                newReleases(userId: ${'$'}userId, since: ${'$'}since, limit: ${'$'}limit) {
                    recordingId artistId artist title album updatedAt
                }
            }
            """.trimIndent(),
            buildJsonObject {
                put("userId", graphQl.userId)
                put("since", since)
                put("limit", limit)
            }
        ).map { data ->
            (data["newReleases"] as? JsonArray).orEmpty().map { it.jsonObject.toRelease() }
        }
}

private const val ARTIST_FIELDS = "artistId displayName normName externalId"

private fun JsonObject.toSubscription() = AgroArtistSubscription(
    artistId = str("artistId").orEmpty(),
    displayName = str("displayName").orEmpty(),
    normName = str("normName").orEmpty(),
    externalId = str("externalId")
)

private fun JsonObject.toRelease() = AgroArtistRelease(
    recordingId = str("recordingId").orEmpty(),
    artistId = str("artistId").orEmpty(),
    artist = str("artist").orEmpty(),
    title = str("title"),
    album = str("album"),
    updatedAt = long("updatedAt")
)
