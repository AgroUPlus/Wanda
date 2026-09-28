package com.wander.android.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.sources.deezer.DeezerAccountManager
import com.wander.android.data.sources.deezer.DeezerStreamResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeezerLoginState(
    val manualArl: String = "",
    val error: String? = null,
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false
)

@HiltViewModel
class DeezerLoginViewModel @Inject constructor(
    private val accountManager: DeezerAccountManager,
    private val streamResolver: DeezerStreamResolver
) : ViewModel() {

    private val _state = MutableStateFlow(DeezerLoginState())
    val state: StateFlow<DeezerLoginState> = _state.asStateFlow()

    fun onManualArlChange(value: String) =
        _state.update { it.copy(manualArl = value, error = null) }

    fun onSessionCaptured(cookieHeader: String) {
        val extracted = extractArl(cookieHeader)
        if (extracted != null) {
            submitArl(extracted)
        }
    }

    fun submitManualArl() {
        val input = _state.value.manualArl.trim()
        val extracted = extractArl(input) ?: input.takeIf { it.length >= 64 }
        if (extracted != null) {
            submitArl(extracted)
        } else {
            _state.update { it.copy(error = "Invalid ARL token format.") }
        }
    }

    private fun submitArl(arl: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            accountManager.setSession(arl)
            streamResolver.invalidateSession()
            _state.update { it.copy(isLoading = false, isSignedIn = true, error = null) }
        }
    }

    companion object {
        fun extractArl(cookieString: String): String? {
            val match = ARL_REGEX.find(cookieString)
            return match?.groupValues?.get(1)
        }

        private val ARL_REGEX = Regex("""(?:^|;\s*)arl=([a-f0-9]{64,})(?:;|$)""")
    }
}
