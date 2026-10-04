package com.wander.android.ui.screens.library.blend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.repository.BlendRepository
import com.wander.android.data.sources.agro.BlendInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Blends this account has been asked into, and answering them. */
@HiltViewModel
internal class BlendInvitesViewModel @Inject constructor(
    private val repository: BlendRepository
) : ViewModel() {

    val invites: StateFlow<List<BlendInfo>> = repository.invites

    /** The id being answered, so only that card shows it is working. */
    private val _answering = MutableStateFlow<String?>(null)
    val answering: StateFlow<String?> = _answering.asStateFlow()

    /** What the server said when it refused — most often, that stats are not being shared. */
    private val _error = MutableStateFlow<Pair<String, String>?>(null)
    val error: StateFlow<Pair<String, String>?> = _error.asStateFlow()

    init {
        viewModelScope.launch { repository.refreshInvites() }
    }

    fun join(playlistId: String, onJoined: (String) -> Unit) = answer(playlistId) {
        repository.accept(playlistId).map(onJoined)
    }

    fun decline(playlistId: String) = answer(playlistId) { repository.decline(playlistId) }

    private fun answer(playlistId: String, block: suspend () -> Result<Unit>) {
        if (_answering.value != null) return
        _answering.value = playlistId
        _error.value = null
        viewModelScope.launch {
            block().onFailure { _error.value = playlistId to it.message.orEmpty() }
            _answering.value = null
        }
    }
}
