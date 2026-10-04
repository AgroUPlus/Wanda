package com.wander.android.ui.components.newplaylist

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.BlendRepository
import com.wander.android.data.repository.PlaylistCreator
import com.wander.android.data.repository.SocialRepository
import com.wander.android.data.sources.agro.AgroPlaylistApi
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.data.sources.agro.BlendRecipe
import com.wander.android.data.sources.agro.EditAccess
import com.wander.android.data.sources.agro.PlaylistVisibility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the sheet makes. The two shared kinds need Agro, and are offered only when it is there. */
internal enum class NewPlaylistKind { PLAYLIST, COLLABORATIVE, BLEND }

@Immutable
internal data class NewPlaylistUiState(
    val kind: NewPlaylistKind = NewPlaylistKind.PLAYLIST,
    /** What was typed. A blend left unnamed is called after its members — see the sheet. */
    val name: String = "",
    val visibility: PlaylistVisibility = PlaylistVisibility.FRIENDS,
    val editAccess: EditAccess = EditAccess.FRIENDS,
    val friends: List<AgroProfile> = emptyList(),
    val picked: List<String> = emptyList(),
    val recipe: BlendRecipe = BlendRecipe(),
    val canShare: Boolean = false,
    val canBlend: Boolean = false,
    val isCreating: Boolean = false,
    @param:StringRes val error: Int? = null,
    val errorDetail: String? = null
) {
    val kinds: List<NewPlaylistKind>
        get() = listOfNotNull(
            NewPlaylistKind.PLAYLIST,
            NewPlaylistKind.COLLABORATIVE.takeIf { canShare },
            NewPlaylistKind.BLEND.takeIf { canBlend }
        )
}

@HiltViewModel
internal class NewPlaylistViewModel @Inject constructor(
    private val creator: PlaylistCreator,
    agroPlaylists: AgroPlaylistApi,
    blends: BlendRepository,
    social: SocialRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        NewPlaylistUiState(canShare = agroPlaylists.isAvailable, canBlend = blends.isAvailable)
    )
    val state: StateFlow<NewPlaylistUiState> = _state.asStateFlow()

    init {
        social.friends.onEach { friends -> _state.update { it.copy(friends = friends) } }.launchIn(viewModelScope)
    }

    fun setKind(kind: NewPlaylistKind) = _state.update { it.copy(kind = kind, error = null) }
    fun setName(name: String) = _state.update { it.copy(name = name) }

    /** Narrowing who can open it narrows who can edit it, exactly as the server will. */
    fun setVisibility(visibility: PlaylistVisibility) =
        _state.update { it.copy(visibility = visibility, editAccess = it.editAccess.clampedTo(visibility)) }

    fun setEditAccess(access: EditAccess) = _state.update { it.copy(editAccess = access.clampedTo(it.visibility)) }
    fun setRecipe(recipe: BlendRecipe) = _state.update { it.copy(recipe = recipe) }

    /** Adds or removes a friend, holding at most as many as a blend may ask. */
    fun toggleFriend(username: String) = _state.update { state ->
        val picked = when {
            username in state.picked -> state.picked - username
            state.picked.size >= BlendRecipe.MAX_INVITED -> state.picked
            else -> state.picked + username
        }
        state.copy(picked = picked, error = null)
    }

    /**
     * Makes it, then hands back the id to open it by. [title] is the name as the sheet resolved it;
     * [tracks] go in when the sheet was opened to hold them, and [plainSource] is where a plain one
     * lives.
     */
    fun create(title: String, tracks: List<UnifiedTrack>, plainSource: SourceType, onCreated: (String) -> Unit) {
        val state = _state.value
        if (state.isCreating || title.isBlank()) return
        if (state.kind == NewPlaylistKind.BLEND && state.picked.isEmpty()) {
            _state.update { it.copy(error = R.string.new_playlist_blend_pick_someone) }
            return
        }
        _state.update { it.copy(isCreating = true, error = null, errorDetail = null) }
        viewModelScope.launch {
            val name = title.trim()
            val result = when (state.kind) {
                NewPlaylistKind.PLAYLIST -> creator.plain(name, plainSource, tracks)
                NewPlaylistKind.COLLABORATIVE -> creator.collaborative(name, state.visibility, state.editAccess, tracks)
                NewPlaylistKind.BLEND -> creator.blend(name, state.picked, state.recipe)
            }
            result.fold(
                onSuccess = { id ->
                    _state.update { it.copy(isCreating = false) }
                    onCreated(id)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(isCreating = false, error = R.string.new_playlist_failed, errorDetail = error.message)
                    }
                }
            )
        }
    }
}
