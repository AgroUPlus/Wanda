package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.SearchKind
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal InnerTube (YouTube Music private API) client. Search, browse and library calls carry
 * the user's own cookie and a SAPISID hash, exactly as the web player does. Playback (`/player`)
 * calls are anonymous and delegated to [InnerTubePlayerResolver].
 */
@Singleton
class InnerTubeClient @Inject internal constructor(
    private val transport: InnerTubeTransport,
    private val playerResolver: InnerTubePlayerResolver
) {
    constructor(accountManager: GoogleAccountManager, client: HttpClient) : this(
        InnerTubeTransport(accountManager, client),
        InnerTubePlayerResolver(accountManager, InnerTubeTransport(accountManager, client))
    )

    suspend fun search(query: String, kind: SearchKind = SearchKind.TRACKS): Result<JsonObject> =
        transport.post(
            "search",
            buildJsonObject {
                put("context", transport.webContext())
                put("query", query)
                put("params", kind.filterParam())
            }
        )

    internal suspend fun player(videoId: String): Result<PlayerResponse> =
        playerResolver.resolvePlayer(videoId)

    /**
     * The account's own home feed. `FEmusic_home` is the browse id behind music.youtube.com's
     * front page, so this returns exactly the shelves YouTube Music would show that user.
     */
    suspend fun home(): Result<JsonObject> = browse(HOME_BROWSE_ID)

    /**
     * The display name of the signed-in account, or null if the response does not carry one.
     */
    suspend fun accountName(): Result<String?> = transport.post(
        "account/account_menu",
        buildJsonObject { put("context", transport.webContext()) }
    ).map { body -> body.activeAccountName() }

    /**
     * [params] is the opaque blob a "more" button carries alongside its browse id. Browsing with
     * it returns the *full* shelf. Absent, this is an ordinary browse.
     */
    suspend fun browse(browseId: String, params: String? = null): Result<JsonObject> =
        transport.post(
            "browse",
            buildJsonObject {
                put("context", transport.webContext())
                put("browseId", browseId)
                params?.takeIf { it.isNotBlank() }?.let { put("params", it) }
            }
        )

    /**
     * The radio queue for a track.
     */
    suspend fun next(videoId: String): Result<JsonObject> = transport.post(
        "next",
        buildJsonObject {
            put("context", transport.webContext())
            put("enablePersistentPlaylistPanel", true)
            put("isAudioOnly", true)
            put("videoId", videoId)
            put("playlistId", "$RADIO_PREFIX$videoId")
        }
    )

    suspend fun setLiked(videoId: String, liked: Boolean): Result<Unit> = transport.post(
        if (liked) "like/like" else "like/removelike",
        buildJsonObject {
            put("context", transport.webContext())
            putJsonObject("target") { put("videoId", videoId) }
        }
    ).map { }

    /**
     * Follows or unfollows a channel on the signed-in YouTube Music account.
     */
    suspend fun setSubscribed(channelId: String, subscribed: Boolean): Result<Unit> = transport.post(
        if (subscribed) "subscription/subscribe" else "subscription/unsubscribe",
        buildJsonObject {
            put("context", transport.webContext())
            putJsonArray("channelIds") { add(channelId) }
        }
    ).map { }
}
