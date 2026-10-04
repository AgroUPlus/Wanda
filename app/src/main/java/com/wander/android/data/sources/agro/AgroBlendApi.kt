package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** How far back each member's listening is read. Names match the server's enum. */
enum class BlendWindow { FOUR_WEEKS, SIX_MONTHS, ALL_TIME }

/** How often Agro writes it again on its own. [FROZEN] changes only when people join or leave. */
enum class BlendRefresh { DAILY, WEEKLY, FROZEN }

/** What a blend is made from. [mix] runs from 0 (all common ground) to 100 (all discovery). */
data class BlendRecipe(
    val size: Int = 50,
    val mix: Int = 60,
    val window: BlendWindow = BlendWindow.SIX_MONTHS,
    val refresh: BlendRefresh = BlendRefresh.WEEKLY
) {
    companion object {
        /** The only sizes the server accepts. */
        val SIZES = listOf(25, 50, 100)
        /** Friends a blend may ask, besides whoever makes it. */
        const val MAX_INVITED = 7
    }
}

data class BlendMember(val username: String, val joined: Boolean)

/** A blend as one of its members — or someone asked to join — sees it. */
data class BlendInfo(
    val playlistId: String,
    val title: String,
    val createdBy: String,
    val isCreator: Boolean,
    val members: List<BlendMember>,
    val recipe: BlendRecipe,
    val nextRefreshAt: String?
)

/**
 * Blends on the paired Agro: playlists it writes from several friends' listening.
 *
 * Only offered when the server advertises [AgroSharedPlaylistParsing.BLENDS]; the tracks themselves
 * arrive through the ordinary shared-playlist path, since a blend is a playlist like any other.
 */
@Singleton
class AgroBlendApi @Inject constructor(private val graphQl: AgroGraphQl) {

    val isAvailable: Boolean
        get() = graphQl.isConfigured && graphQl.serverSupports(AgroSharedPlaylistParsing.BLENDS)

    private val fields = """
        playlistId title createdBy isCreator size mix window refresh nextRefreshAt
        members { username joined }
    """.trimIndent()

    private val recipeArgs = "size: \$size, mix: \$mix, window: \$window, refresh: \$refresh"
    private val recipeParams = "\$size: Int!, \$mix: Int!, \$window: BlendWindow!, \$refresh: BlendRefresh!"

    suspend fun create(title: String, members: List<String>, recipe: BlendRecipe): Result<BlendInfo> = graphQl.execute(
        "mutation(\$title: String!, \$members: [String!]!, $recipeParams) " +
            "{ createBlend(title: \$title, members: \$members, $recipeArgs) { $fields } }",
        buildJsonObject {
            put("title", title)
            put("members", buildJsonArray { members.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            putRecipe(recipe)
        }
    ).mapCatching { blend(it.obj("createBlend")) }

    suspend fun blend(playlistId: String): Result<BlendInfo> = graphQl.execute(
        "query(\$id: String!) { blend(playlistId: \$id) { $fields } }",
        buildJsonObject { put("id", playlistId) }
    ).mapCatching { blend(it.obj("blend")) }

    suspend fun invites(): Result<List<BlendInfo>> = graphQl.execute(
        "query { blendInvites { $fields } }",
        buildJsonObject { }
    ).mapCatching { data -> data.objects("blendInvites").map(::blend) }

    /** `false` when there was no invitation left to answer — answered on another device. */
    suspend fun answer(playlistId: String, accept: Boolean): Result<Boolean> = graphQl.execute(
        "mutation(\$id: String!, \$accept: Boolean!) { answerBlendInvite(playlistId: \$id, accept: \$accept) }",
        buildJsonObject {
            put("id", playlistId)
            put("accept", accept)
        }
    ).map { it["answerBlendInvite"]?.jsonPrimitive?.booleanOrNull == true }

    /** Leaves it; for its creator, that ends it for everyone. */
    suspend fun leave(playlistId: String): Result<Unit> = graphQl.execute(
        "mutation(\$id: String!) { leaveBlend(playlistId: \$id) }",
        buildJsonObject { put("id", playlistId) }
    ).map { }

    suspend fun update(playlistId: String, title: String, recipe: BlendRecipe): Result<BlendInfo> = graphQl.execute(
        "mutation(\$id: String!, \$title: String!, $recipeParams) " +
            "{ updateBlend(playlistId: \$id, title: \$title, $recipeArgs) { $fields } }",
        buildJsonObject {
            put("id", playlistId)
            put("title", title)
            putRecipe(recipe)
        }
    ).mapCatching { blend(it.obj("updateBlend")) }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putRecipe(recipe: BlendRecipe) {
        put("size", recipe.size)
        put("mix", recipe.mix)
        put("window", recipe.window.name)
        put("refresh", recipe.refresh.name)
    }

    private fun blend(json: JsonObject?): BlendInfo {
        json ?: error("Agro answered without the blend")
        return BlendInfo(
            playlistId = json.str("playlistId") ?: error("A blend arrived without an id"),
            title = json.str("title").orEmpty(),
            createdBy = json.str("createdBy").orEmpty(),
            isCreator = json.bool("isCreator"),
            members = json.objects("members").map { BlendMember(it.str("username").orEmpty(), it.bool("joined")) },
            recipe = BlendRecipe(
                size = json.long("size").toInt(),
                mix = json.long("mix").toInt(),
                window = BlendWindow.entries.firstOrNull { it.name == json.str("window") } ?: BlendWindow.SIX_MONTHS,
                refresh = BlendRefresh.entries.firstOrNull { it.name == json.str("refresh") } ?: BlendRefresh.WEEKLY
            ),
            nextRefreshAt = json.str("nextRefreshAt")
        )
    }
}
