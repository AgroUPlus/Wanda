package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
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
    private val graphQl: AgroGraphQl,
    private val identityKeyManager: com.wander.android.core.security.IdentityKeyManager,
    private val secureStorage: com.wander.android.core.security.SecureStorage
) {
    suspend fun friends(): Result<List<AgroFriend>> = graphQl.execute(
        // This answer seeds the presence feed on a graph refresh, so it needs the device id for
        // the same reason [friendsNowPlaying] does: without it every sealed friend arrives as a
        // placeholder and stays one until a socket frame happens to replace it.
        """
        query Friends(${'$'}deviceId: String) {
            friends(deviceId: ${'$'}deviceId) {
                profile { $PROFILE_FIELDS }
                nowPlaying { $NOW_PLAYING_FIELDS }
            }
        }
        """.trimIndent(),
        buildJsonObject { put("deviceId", secureStorage.agroDeviceId) }
    ).map { data ->
        (data["friends"] as? JsonArray).orEmpty().map { entry ->
            val friend = entry.jsonObject.toFriend()
            friend.copy(nowPlaying = friend.nowPlaying?.openIfSealed(identityKeyManager))
        }
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
        // The device id names which key the server should pick a sealed copy for. A copy is sealed
        // to one device, so without it there is nothing the server could safely hand back.
        """
        query FriendsNowPlaying(${'$'}deviceId: String) {
            friendsNowPlaying(deviceId: ${'$'}deviceId) { $NOW_PLAYING_FIELDS }
        }
        """.trimIndent(),
        buildJsonObject { put("deviceId", secureStorage.agroDeviceId) }
    ).map { data ->
        (data["friendsNowPlaying"] as? JsonArray).orEmpty().map {
            it.jsonObject.toNowPlaying().openIfSealed(identityKeyManager)
        }
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

    /**
     * Mints a short-lived code for adding this account as a friend in person.
     *
     * For two people in the same room, where the username search cannot help — one of them may
     * not be discoverable, and should not have to become so to be added once. The server drops
     * any previous code when it mints a new one, so only the code currently on screen works.
     */
    suspend fun createFriendCode(): Result<AgroFriendCode> = graphQl.execute(
        """
        mutation CreateFriendCode {
          createFriendCode { code expiresAt ttlSeconds }
        }
        """.trimIndent(),
        buildJsonObject { }
    ).mapCatching { data ->
        val obj = data["createFriendCode"]?.jsonObject
            ?: error("the server minted no friend code")
        AgroFriendCode(
            code = obj["code"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            ttlSeconds = obj["ttlSeconds"]?.jsonPrimitive?.longOrNull ?: 0L
        )
    }

    /** Drops this account's outstanding code, for a panel being closed or the app going away. */
    suspend fun revokeFriendCode(): Result<Boolean> = graphQl.execute(
        "mutation RevokeFriendCode { revokeFriendCode }",
        buildJsonObject { }
    ).map { data -> data["revokeFriendCode"]?.jsonPrimitive?.booleanOrNull ?: false }

    /**
     * Spends someone's code and becomes their friend.
     *
     * Answers with their username on success and null on every failure — unknown, expired,
     * already spent, blocked. The server does not distinguish them, and neither should this.
     */
    suspend fun redeemFriendCode(code: String): Result<String?> = graphQl.execute(
        """
        mutation RedeemFriendCode(${'$'}code: String!) {
          redeemFriendCode(code: ${'$'}code)
        }
        """.trimIndent(),
        buildJsonObject { put("code", code.trim()) }
    ).map { data -> data["redeemFriendCode"]?.jsonPrimitive?.contentOrNull }

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
