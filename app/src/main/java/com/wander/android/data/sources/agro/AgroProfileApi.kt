package com.wander.android.data.sources.agro

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Profiles: reading someone else's, editing your own, and the switches that gate both. */
@Singleton
internal class AgroProfileApi @Inject constructor(
    private val graphQl: AgroGraphQl
) {
    /**
     * One account's card, or `null`.
     *
     * `null` covers every reason at once — no such account, not discoverable, blocked — because the
     * server answers all of them identically and the app must not invent a distinction the server
     * refused to make.
     */
    suspend fun profile(username: String): Result<AgroProfile?> = graphQl.execute(
        """
        query Profile(${'$'}username: String!) {
            profile(username: ${'$'}username) { $PROFILE_FIELDS }
        }
        """.trimIndent(),
        buildJsonObject { put("username", username.trim().lowercase()) }
    ).map { data ->
        (data["profile"] as? kotlinx.serialization.json.JsonObject)?.toProfile()
    }

    /** Edits the signed-in account. Fields left null are left alone, not blanked. */
    suspend fun updateProfile(
        displayName: String? = null,
        bio: String? = null,
        avatarUrl: String? = null
    ): Result<AgroProfile> = graphQl.execute(
        """
        mutation UpdateProfile(
            ${'$'}displayName: String, ${'$'}bio: String, ${'$'}avatarUrl: String
        ) {
            updateProfile(
                displayName: ${'$'}displayName, bio: ${'$'}bio, avatarUrl: ${'$'}avatarUrl
            ) { $PROFILE_FIELDS }
        }
        """.trimIndent(),
        buildJsonObject {
            displayName?.let { put("displayName", it) }
            bio?.let { put("bio", it) }
            avatarUrl?.let { put("avatarUrl", it) }
        }
    ).mapCatching { data ->
        data["updateProfile"]!!.jsonObject.toProfile()
    }

    /**
     * Writes all three switches together.
     *
     * One call rather than three, so the privacy screen cannot land half-applied — a state where
     * `discoverable` is on but the user believes they also turned now-playing off is exactly the
     * kind of thing a partial write produces.
     */
    suspend fun setVisibility(visibility: AgroVisibility): Result<AgroProfile> = graphQl.execute(
        """
        mutation SetVisibility(
            ${'$'}showNowPlaying: Boolean!, ${'$'}showStats: Boolean!,
            ${'$'}discoverable: Boolean!, ${'$'}showActivity: Boolean!
        ) {
            setVisibility(
                showNowPlaying: ${'$'}showNowPlaying, showStats: ${'$'}showStats,
                discoverable: ${'$'}discoverable, showActivity: ${'$'}showActivity
            ) { $PROFILE_FIELDS }
        }
        """.trimIndent(),
        buildJsonObject {
            put("showNowPlaying", visibility.showNowPlaying)
            put("showStats", visibility.showStats)
            put("discoverable", visibility.discoverable)
            put("showActivity", visibility.showActivity)
        }
    ).mapCatching { data -> data["setVisibility"]!!.jsonObject.toProfile() }

    /**
     * Whether this account is currently quiet.
     *
     * Only ever answered for the signed-in account. An incognito account is not returned to
     * anybody else at all, so there is no version of this question about a friend — that a friend
     * has gone quiet is itself a disclosure, and the server declines to make it.
     */
    suspend fun incognito(): Result<Boolean> = graphQl.execute(
        "query Incognito { incognito }",
        buildJsonObject { }
    ).mapCatching { data ->
        data["incognito"]!!.jsonPrimitive.boolean
    }

    /**
     * Goes quiet, or stops being quiet, for the whole account.
     *
     * Its own mutation rather than a fifth switch on [setVisibility]: the others are standing
     * consents, and this is a temporary override of all of them. Bundling them would mean leaving
     * incognito had to restore the rest from this client's idea of what they were, and a stale
     * copy would silently turn a privacy switch back on.
     */
    suspend fun setIncognito(enabled: Boolean): Result<Boolean> = graphQl.execute(
        """
        mutation SetIncognito(${'$'}incognito: Boolean!) {
            setIncognito(incognito: ${'$'}incognito)
        }
        """.trimIndent(),
        buildJsonObject { put("incognito", enabled) }
    ).mapCatching { data ->
        data["setIncognito"]!!.jsonPrimitive.boolean
    }

    /**
     * How far your listening overlaps theirs.
     *
     * Fails rather than returning empty when the friend keeps their stats private: an empty match
     * and a withheld one mean different things, and only the caller can decide what to say.
     */
    suspend fun tasteMatch(username: String): Result<AgroTasteMatch> = graphQl.execute(
        """
        query TasteMatch(${'$'}username: String!) {
            tasteMatch(username: ${'$'}username) {
                score
                sharedArtists { name value }
                sharedTracks { name value }
            }
        }
        """.trimIndent(),
        buildJsonObject { put("username", username.trim().lowercase()) }
    ).mapCatching { data ->
        val match = data["tasteMatch"]!!.jsonObject
        AgroTasteMatch(
            score = match.long("score").toInt(),
            sharedArtists = match.entries("sharedArtists"),
            sharedTracks = match.entries("sharedTracks")
        )
    }
}
