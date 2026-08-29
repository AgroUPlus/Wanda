package com.wander.android.data.sources.agro

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** What the signed-in account is allowed to do on its Agro server. */
internal data class AgroPermissions(
    /**
     * Whether this account may upload whole audio files into the server's library directory.
     *
     * Granted per account on the server (`users.can_archive`), and not something a client can talk
     * itself into. Read so the setting can be *shown as unavailable* rather than offered and then
     * refused at upload time — a switch that flips on and silently does nothing is worse than one
     * that is visibly greyed out.
     */
    val canArchive: Boolean
)

/** The signed-in account, as the server describes it. */
@Singleton
internal class AgroAccountApi @Inject constructor(
    private val graphQl: AgroGraphQl
) {

    /**
     * The caller's own permissions.
     *
     * `me` takes a username because a server holds several accounts and a device token names one;
     * passing the paired username is what stops a signed-in device reading somebody else's card.
     */
    suspend fun permissions(): Result<AgroPermissions> = graphQl.execute(
        """
        query Me(${'$'}username: String) {
            me(username: ${'$'}username) { canArchive }
        }
        """.trimIndent(),
        buildJsonObject { put("username", graphQl.userId) }
    ).mapCatching { data ->
        AgroPermissions(
            canArchive = data["me"]!!.jsonObject["canArchive"]!!.jsonPrimitive.boolean
        )
    }
}
