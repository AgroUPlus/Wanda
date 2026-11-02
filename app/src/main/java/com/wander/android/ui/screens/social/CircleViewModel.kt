package com.wander.android.ui.screens.social

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.network.userMessageRes
import com.wander.android.data.sources.agro.AgroFeedApi
import com.wander.android.data.sources.agro.AgroFeedItem
import com.wander.android.data.sources.agro.AgroRecap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
internal data class CircleUiState(
    val feed: List<AgroFeedItem> = emptyList(),
    val recap: AgroRecap? = null,
    val period: String = "MONTH",
    val loading: Boolean = true,
    /** A @StringRes saying why the feed or the recap could not be read, or null. */
    val error: Int? = null
)

/**
 * The activity feed and the circle recap, which are read together because they are shown together.
 *
 * Neither is cached in Room. Both are *derived* on the server from other people's plays, and a
 * local copy would outlive the switch that permitted it — the same reasoning that keeps presence
 * out of the database. An empty answer is the normal one for a circle that has not opted in, so
 * neither is treated as an error; a failed answer is, and keeps what was on screen.
 */
@HiltViewModel
internal class CircleViewModel @Inject constructor(
    private val feedApi: AgroFeedApi
) : ViewModel() {

    private val _state = MutableStateFlow(CircleUiState())
    val state: StateFlow<CircleUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun setPeriod(period: String) {
        _state.update { it.copy(period = period) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val feed = feedApi.friendActivity()
            val recap = feedApi.recap(_state.value.period)
            val failure = feed.exceptionOrNull() ?: recap.exceptionOrNull()
            _state.update {
                it.copy(
                    feed = feed.getOrElse { _ -> it.feed },
                    recap = recap.getOrElse { _ -> it.recap },
                    error = failure?.userMessageRes(),
                    loading = false
                )
            }
        }
    }
}
