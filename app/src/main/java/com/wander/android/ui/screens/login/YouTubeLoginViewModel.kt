package com.wander.android.ui.screens.login

import androidx.lifecycle.ViewModel
import com.wander.android.data.sources.ytmusic.GoogleAccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class YouTubeLoginState(
    val manualCookie: String = "",
    val error: String? = null,
    val isSignedIn: Boolean = false
)

@HiltViewModel
class YouTubeLoginViewModel @Inject constructor(
    private val accountManager: GoogleAccountManager
) : ViewModel() {

    private val _state = MutableStateFlow(YouTubeLoginState())
    val state: StateFlow<YouTubeLoginState> = _state.asStateFlow()

    fun onManualCookieChange(value: String) =
        _state.update { it.copy(manualCookie = value, error = null) }

    /** Called by the WebView once music.youtube.com reports a signed-in session. */
    fun onSessionCaptured(cookie: String, visitorData: String) = submit(cookie, visitorData)

    /**
     * A pasted cookie carries no visitorData. That is a weaker session than the WebView produces,
     * but still a valid one, so it is accepted rather than rejected.
     */
    fun submitManualCookie() = submit(_state.value.manualCookie, visitorData = "")

    private fun submit(cookie: String, visitorData: String) {
        if (accountManager.signIn(cookie, visitorData)) {
            _state.update { it.copy(isSignedIn = true, error = null) }
        } else {
            _state.update {
                it.copy(error = "That cookie has no SAPISID value, so requests cannot be signed.")
            }
        }
    }
}
