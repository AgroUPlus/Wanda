package com.wander.android.ui.screens.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.local.LocalMusicSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which backends are already usable, so the welcome screen can show real state, not guesses. */
data class SetupStatus(
    val localGranted: Boolean = false,
    val navidromeConfigured: Boolean = false,
    val ytMusicConfigured: Boolean = false
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val localSource: LocalMusicSource
) : ViewModel() {

    val status: StateFlow<SetupStatus> = combine(
        localSource.isConfigured,
        secureStorage.navidromeConfigured,
        secureStorage.ytMusicConfigured
    ) { local, navidrome, ytMusic ->
        SetupStatus(local, navidrome, ytMusic)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupStatus())

    /** Rescans after the audio permission is granted from the welcome flow. */
    fun refreshLocal() {
        viewModelScope.launch { localSource.refresh() }
    }

    fun finish() = secureStorage.markSetupComplete()
}
