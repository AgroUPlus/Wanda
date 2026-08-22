package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The friend graph: who you are connected to, who is asking, and who you can find.
 *
 * None of these take a `userId`. The social resolvers work on the account the token proved and
 * offer no argument through which another could be named, which is the whole reason they are safe
 * to expose at all.
 */
@Singleton
internal class AgroFriendsApi @Inject constructor(
    private val graphQl: AgroGraphQl
) {
    suspend fun friends(): Result<List<AgroFriend>> = graphQl.execute(
        "{ friends { profile { $PROFILE_FIELDS } nowPlaying { $NOW_PLAYING_FIELDS } } }",
        buildJsonObject { }
    ).map { data ->
        (data["friends"] as? JsonArray).orEmpty().map { it.jsonObject.toFriend() }
    }

    /** Both directions at once; `outgoing` says which is which. */
    suspend fun friendRequests(): Result<List<AgroProfile>> = graphQl.execute(
        "{ friendRequests { $PROFILE_FIELDS } }",
        buildJsonObject { }
    ).map { data ->
        (data["friendRequests"] as? JsonArray).orEmpty().map { it.jsonObject.toProfile() }
    }

    /**
     * Just the presence feed.
     *
     * Separate from [friends] because it is refreshed far more often — the friend list changes
     * when somebody accepts a request, presence changes with every track.
     */
    suspend fun friendsNowPlaying(): Result<List<AgroFriendNowPlaying>> = graphQl.execute(
        "{ friendsNowPlaying { $NOW_PLAYING_FIELDS } }",
        buildJsonObject { }
    ).map { data ->
        (data["friendsNowPlaying"] as? JsonArray).orEmpty().map { it.jsonObject.toNowPlaying() }
    }

    /**
     * The public directory.
     *
     * Only accounts that switched `discoverable` on appear, and the server matches on a prefix
     * rather than a substring, so this cannot be used to walk the user list.
     */
    suspend fun searchUsers(query: String, limit: Int = 20): Result<List<AgroProfile>> =
        graphQl.execute(
            """
            query SearchUsers(${'$'}query: String!, ${'$'}limit: Int) {
                searchUsers(query: ${'$'}query, limit: ${'$'}limit) { $PROFILE_FIELDS }
            }
            """.trimIndent(),
            buildJsonObject {
                put("query", query.trim())
                put("limit", limit)
            }
        ).map { data ->
            (data["searchUsers"] as? JsonArray).orEmpty().map { it.jsonObject.toProfile() }
        }

    /**
     * Asks to be someone's friend.
     *
     * `false` is not an error. The server answers it for an existing edge, a block, or no such
     * account alike, and deliberately does not say which — telling them apart would turn this into
     * a way to test whether an account exists.
     */
    suspend fun sendFriendRequest(username: String): Result<Boolean> =
        booleanMutation("sendFriendRequest", username)

    suspend fun acceptFriendRequest(username: String): Result<Boolean> =
        booleanMutation("acceptFriendRequest", username)

    /** Declines a request, or ends a friendship. The same call for both. */
    suspend fun removeFriend(username: String): Result<Boolean> =
        booleanMutation("removeFriend", username)

    suspend fun blockUser(username: String): Result<Boolean> =
        booleanMutation("blockUser", username)

    suspend fun unblockUser(username: String): Result<Boolean> =
        booleanMutation("unblockUser", username)

    /** The five graph mutations differ only in their name, so they share one body. */
    private suspend fun booleanMutation(field: String, username: String): Result<Boolean> =
        graphQl.execute(
            """
            mutation Edge(${'$'}username: String!) { $field(username: ${'$'}username) }
            """.trimIndent(),
            buildJsonObject { put("username", username.trim().lowercase()) }
        ).map { data -> data[field]?.jsonPrimitive?.booleanOrNull ?: false }
}
