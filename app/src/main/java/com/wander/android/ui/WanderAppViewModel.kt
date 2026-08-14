package com.wander.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.local.LocalMusicSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WanderAppViewModel @Inject constructor(
    private val localSource: LocalMusicSource,
    secureStorage: SecureStorage
) : ViewModel() {

    /** Decides whether the app opens on the welcome flow or straight into the library. */
    val hasCompletedSetup: StateFlow<Boolean> = secureStorage.hasCompletedSetup

    /**
     * Runs once the audio permission is granted. The scan is incremental, so calling it on every
     * cold start costs almost nothing after the first time.
     */
    fun onAudioPermissionGranted() {
        viewModelScope.launch { localSource.refresh() }
    }
}
