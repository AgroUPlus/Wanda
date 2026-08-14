package com.wander.android.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.sources.navidrome.NavidromeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NavidromeLoginState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isConnecting: Boolean = false,
    val error: String? = null,
    val isSignedIn: Boolean = false
) {
    val canSubmit: Boolean
        get() = !isConnecting && serverUrl.isNotBlank() &&
            username.isNotBlank() && password.isNotBlank()
}

@HiltViewModel
class NavidromeLoginViewModel @Inject constructor(
    private val navidromeSource: NavidromeSource
) : ViewModel() {

    private val _state = MutableStateFlow(NavidromeLoginState())
    val state: StateFlow<NavidromeLoginState> = _state.asStateFlow()

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, error = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    /**
     * Credentials are validated with a `ping` before being stored, so a wrong password fails
     * here with a message rather than silently producing an empty library.
     */
    fun signIn() {
        val current = _state.value
        if (!current.canSubmit) return

        viewModelScope.launch {
            _state.update { it.copy(isConnecting = true, error = null) }
            navidromeSource.login(
                url = current.serverUrl.withScheme(),
                username = current.username,
                password = current.password
            ).fold(
                onSuccess = { _state.update { it.copy(isConnecting = false, isSignedIn = true) } },
                onFailure = { cause ->
                    _state.update {
                        it.copy(isConnecting = false, error = cause.readableMessage())
                    }
                }
            )
        }
    }
}

/** Users type "music.example.com"; assume HTTPS rather than silently downgrading. */
private fun String.withScheme(): String =
    if (startsWith("http://") || startsWith("https://")) trim() else "https://${trim()}"

private fun Throwable.readableMessage(): String = when (this) {
    is java.net.UnknownHostException -> "Could not find that server. Check the address."
    is java.net.ConnectException -> "The server did not respond. Is it reachable from here?"
    is javax.net.ssl.SSLException ->
        "The server's certificate was rejected. Plain HTTP needs an exception in Settings."
    else -> message ?: "Sign-in failed."
}
