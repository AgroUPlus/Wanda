package com.wander.android.data.repository

import com.wander.android.data.repository.sharedplaylist.SharedPlaylistRepository
import com.wander.android.data.sources.agro.AgroBlendApi
import com.wander.android.data.sources.agro.BlendInfo
import com.wander.android.data.sources.agro.BlendRecipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blends: making one, answering an invitation to one, leaving, and changing the recipe.
 *
 * A blend's tracks are a shared playlist's, kept in Room by [SharedPlaylistRepository] like any
 * other, so this holds only what has no copy there: the invitations not yet answered.
 */
@Singleton
internal class BlendRepository @Inject constructor(
    private val api: AgroBlendApi,
    private val shared: SharedPlaylistRepository
) {
    val isAvailable: Boolean get() = api.isAvailable

    /**
     * Invitations waiting on an answer, held in memory only. An invitation answered on another
     * device has to disappear here, and one cannot be answered offline anyway, so a stored copy
     * would only ever be a way to show a button that fails.
     */
    private val _invites = MutableStateFlow<List<BlendInfo>>(emptyList())
    val invites: StateFlow<List<BlendInfo>> = _invites.asStateFlow()

    /** Re-reads the invitations, answering the ones that were not waiting before this read. */
    suspend fun refreshInvites(): Result<List<BlendInfo>> {
        if (!api.isAvailable) {
            _invites.value = emptyList()
            return Result.success(emptyList())
        }
        return api.invites().map { fresh ->
            val seen = _invites.value.mapTo(HashSet()) { it.playlistId }
            _invites.value = fresh
            fresh.filter { it.playlistId !in seen }
        }
    }

    /** Makes a blend and keeps its copy, answering the id to open it by. */
    suspend fun create(title: String, members: List<String>, recipe: BlendRecipe): Result<String> =
        api.create(title, members, recipe).mapCatching { shared.keepOwn(it.playlistId).getOrThrow() }

    /** Joins a blend, then keeps it like any playlist this account follows. */
    suspend fun accept(playlistId: String): Result<String> =
        answer(playlistId, accept = true).mapCatching { shared.follow(playlistId).getOrThrow() }

    suspend fun decline(playlistId: String): Result<Unit> = answer(playlistId, accept = false).map { }

    private suspend fun answer(playlistId: String, accept: Boolean): Result<Boolean> =
        api.answer(playlistId, accept).onSuccess {
            // Answered here or already elsewhere: either way it is no longer waiting.
            _invites.value = _invites.value.filterNot { it.playlistId == playlistId }
        }

    suspend fun info(playlistId: String): Result<BlendInfo> = api.blend(playlistId)

    /** Changes the title and recipe. Agro rewrites it at once and the copy follows on its own. */
    suspend fun update(playlistId: String, title: String, recipe: BlendRecipe): Result<BlendInfo> =
        api.update(playlistId, title, recipe)

    /** On sign-out: another account's invitations are not this one's. */
    fun clear() {
        _invites.value = emptyList()
    }
}
