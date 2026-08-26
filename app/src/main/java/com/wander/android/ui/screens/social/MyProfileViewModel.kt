package com.wander.android.ui.screens.social

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.repository.SocialRepository
import com.wander.android.data.sources.agro.AgroProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Your own page.
 *
 * [username] is empty when no Agro server is paired, which is a real state rather than an error:
 * the screen still exists, because listening statistics are local and are about you whether or not
 * anyone else can see them.
 */
@Immutable
internal data class MyProfileUiState(
    val username: String = "",
    val profile: AgroProfile? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val isPaired: Boolean get() = username.isNotBlank()
}

@HiltViewModel
internal class MyProfileViewModel @Inject constructor(
    private val repository: SocialRepository,
    secureStorage: SecureStorage
) : ViewModel() {

    private val _state = MutableStateFlow(MyProfileUiState(username = secureStorage.agroUsername))
    val state: StateFlow<MyProfileUiState> = _state.asStateFlow()

    init {
        if (_state.value.isPaired) load()
    }

    fun load() {
        val username = _state.value.username
        if (username.isBlank()) return
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.profile(username).fold(
                onSuccess = { profile ->
                    _state.value = _state.value.copy(profile = profile, isLoading = false)
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message ?: "Could not load your profile"
                    )
                }
            )
        }
    }

    /**
     * Saves the display name and bio.
     *
     * `updateProfile` has existed on the repository since profiles did and had no caller at all,
     * so display name, bio and avatar were fields the server stored and the app could never set.
     * Reloaded afterwards rather than assumed: the server trims and bounds both, and showing what
     * was typed instead of what was stored would misreport what other people see.
     */
    fun save(displayName: String, bio: String) {
        if (!_state.value.isPaired) return
        _state.value = _state.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
            repository.updateProfile(
                displayName = displayName.trim().takeIf { it.isNotBlank() },
                bio = bio.trim().takeIf { it.isNotBlank() },
                // Left alone: this screen edits the two fields it shows, and passing null for a
                // field the user did not touch must not be read as "clear it".
                avatarUrl = _state.value.profile?.avatarUrl
            ).fold(
                onSuccess = {
                    _state.value = _state.value.copy(isSaving = false)
                    load()
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = error.message ?: "Could not save your profile"
                    )
                }
            )
        }
    }
}
