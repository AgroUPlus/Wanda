package com.wander.android.ui.screens.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.repository.ListenerPresence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What happened when the user asked to go incognito from the listeners bar. */
internal sealed interface IncognitoOutcome {
    data object Done : IncognitoOutcome
    /** The server's refusal, verbatim, or null when it gave none. */
    data class Failed(val message: String?) : IncognitoOutcome
}

/** The people listening along with this account, for the bar above the mini-player. */
@HiltViewModel
internal class ListenersViewModel @Inject constructor(
    private val presence: ListenerPresence
) : ViewModel() {

    val listeners: StateFlow<List<String>> = presence.listeners

    private val _outcomes = MutableSharedFlow<IncognitoOutcome>(extraBufferCapacity = 1)
    val outcomes: SharedFlow<IncognitoOutcome> = _outcomes.asSharedFlow()

    fun goIncognito() {
        viewModelScope.launch {
            val outcome = presence.goIncognito().fold(
                onSuccess = { IncognitoOutcome.Done },
                onFailure = { IncognitoOutcome.Failed(it.message) }
            )
            _outcomes.emit(outcome)
        }
    }
}
