package com.wander.android.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.repository.sharedplaylist.SharedWithMeRepository
import com.wander.android.data.sources.agro.AgroSharedListing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The library's "Shared with me" list: friends' playlists and the blends this account is in, read
 * from Agro when the list is opened, and opening, leaving or unfollowing one of them.
 */
@HiltViewModel
class SharedWithMeViewModel @Inject constructor(
    private val repository: SharedWithMeRepository
) : ViewModel() {

    private val _state = MutableStateFlow<SharedWithMe>(SharedWithMe.NotLoaded)
    val state: StateFlow<SharedWithMe> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Why the last leave or unfollow failed, until the next try. */
    val error: StateFlow<String?> = _error.asStateFlow()

    val isAvailable: Boolean get() = repository.isAvailable

    fun load() {
        viewModelScope.launch { read() }
    }

    /** Opens [listing], following it first when it is not in the library yet. */
    fun open(listing: AgroSharedListing, onOpen: (String) -> Unit) {
        viewModelScope.launch {
            repository.open(listing).onSuccess {
                onOpen(it)
                read()
            }
        }
    }

    /** Leaves or unfollows [listing], then re-reads, so the row goes with it. */
    fun remove(listing: AgroSharedListing) {
        viewModelScope.launch {
            _error.value = repository.remove(listing).exceptionOrNull()?.message
            read()
        }
    }

    private suspend fun read() {
        if (_state.value == SharedWithMe.NotLoaded) _state.value = SharedWithMe.Loading
        _state.value = repository.list().fold(
            onSuccess = { SharedWithMe.Loaded(it) },
            onFailure = { SharedWithMe.Failed(it.message.orEmpty()) }
        )
    }
}

/** Where the "Shared with me" list stands. */
sealed interface SharedWithMe {
    data object NotLoaded : SharedWithMe
    data object Loading : SharedWithMe
    data class Loaded(val items: List<AgroSharedListing>) : SharedWithMe
    data class Failed(val message: String) : SharedWithMe
}
