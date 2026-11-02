package com.wander.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.wander.android.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs the per-source streaming quality pickers on the Connections page.
 *
 * The stream resolvers read the stored value each time they resolve a track, so a change applies
 * from the next song on; nothing here has to tell playback.
 */
@HiltViewModel
internal class StreamQualityViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {

    // A plain stored string rather than a flow in storage: only this screen ever writes it, so a
    // copy held here is the one place it can change while the screen is up.
    private val _deezerQuality = MutableStateFlow(DeezerQualityChoice.fromTag(secureStorage.deezerAudioQuality))
    val deezerQuality: StateFlow<DeezerQualityChoice> = _deezerQuality.asStateFlow()

    fun setDeezerQuality(choice: DeezerQualityChoice) {
        secureStorage.deezerAudioQuality = choice.tag
        _deezerQuality.value = choice
    }
}
