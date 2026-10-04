package com.wander.android.ui.screens.playlist.blend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.repository.BlendRepository
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistRepository
import com.wander.android.data.sources.agro.BlendInfo
import com.wander.android.data.sources.agro.BlendRecipe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A blend's members and recipe, for the banner over its tracks. Read from Agro each time the
 * screen opens: it is small, it changes as people join, and the tracks themselves are already in
 * Room through the shared-playlist copy.
 */
@HiltViewModel
internal class BlendBannerViewModel @Inject constructor(
    private val blends: BlendRepository,
    private val shared: SharedPlaylistRepository
) : ViewModel() {

    private val _info = MutableStateFlow<BlendInfo?>(null)
    val info: StateFlow<BlendInfo?> = _info.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _left = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Fires once this account is out of it, for the screen to close. */
    val left: SharedFlow<Unit> = _left.asSharedFlow()

    private var loadedFor: String? = null

    fun load(agroId: String) {
        if (loadedFor == agroId) return
        loadedFor = agroId
        viewModelScope.launch {
            blends.info(agroId).fold(onSuccess = { _info.value = it }, onFailure = { _error.value = it.message })
        }
    }

    /** Leaves it — or, for its creator, ends it for everyone. */
    fun leave(agroId: String) {
        viewModelScope.launch {
            shared.remove(agroId).fold(onSuccess = { _left.tryEmit(Unit) }, onFailure = { _error.value = it.message })
        }
    }

    fun update(agroId: String, title: String, recipe: BlendRecipe) {
        viewModelScope.launch {
            blends.update(agroId, title, recipe).fold(onSuccess = { _info.value = it }, onFailure = { _error.value = it.message })
        }
    }
}
