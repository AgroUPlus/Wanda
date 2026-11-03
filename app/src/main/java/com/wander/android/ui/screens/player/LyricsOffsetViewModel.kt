package com.wander.android.ui.screens.player

import androidx.lifecycle.ViewModel
import com.wander.android.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * The full-screen lyrics' timing correction for each track; see
 * [com.wander.android.core.security.SecureLyricsOffsetPreferences].
 */
@HiltViewModel
internal class LyricsOffsetViewModel @Inject constructor(secureStorage: SecureStorage) : ViewModel() {

    private val store = secureStorage.playbackPrefs.lyricsOffsets

    val offsets: StateFlow<Map<String, Long>> = store.offsets

    fun adjust(trackId: String, deltaMs: Long) = store.adjust(trackId, deltaMs)
}
