package com.wander.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.LoudnessMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Backs [LoudnessSection]. The playback service reads the same flows, so changes apply at once. */
@HiltViewModel
internal class LoudnessViewModel @Inject constructor(secureStorage: SecureStorage) : ViewModel() {

    private val prefs = secureStorage.loudness

    val mode: StateFlow<LoudnessMode> = prefs.mode
    val preampDb: StateFlow<Int> = prefs.preampDb

    fun setMode(mode: LoudnessMode) = prefs.setMode(mode)
    fun setPreampDb(db: Int) = prefs.setPreampDb(db)
}
