package com.wander.android.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.repository.ListeningReport
import com.wander.android.data.repository.StatsRepository
import com.wander.android.data.repository.StatsWindow
import com.wander.android.data.sources.agro.StatsPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: StatsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        StatsUiState(isFleetWide = repository.isFleetWide)
    )
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        load(_state.value.window)
    }

    fun setPeriod(period: StatsPeriod) {
        if (period == _state.value.window.period) return
        // Back to the newest window. Holding the offset would land the user four *months* back
        // after switching from weeks, which is not what picking a longer period means.
        load(StatsWindow(period))
    }

    fun showEarlier() = load(_state.value.window.earlier())

    fun showLater() = load(_state.value.window.later())

    fun retry() = load(_state.value.window)

    private fun load(window: StatsWindow) {
        if (window == _state.value.window && _state.value.isLoading) return
        _state.update { it.copy(window = window, isLoading = true, error = null) }
        viewModelScope.launch {
            repository.report(window)
                .onSuccess { report ->
                    _state.update {
                        // The report carries the window it was actually answered for — Agro drops
                        // the offset — so the arrows and the date range agree with the figures.
                        it.copy(
                            report = report,
                            window = report.window,
                            isLoading = false,
                            isFleetWide = report.isFleetWide
                        )
                    }
                }
                .onFailure { failure ->
                    // The previous figures stay on screen behind the message. Blanking them would
                    // turn a failed refresh into what looks like a listening history that vanished.
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = failure.message ?: "Couldn't load your statistics."
                        )
                    }
                }
        }
    }
}

data class StatsUiState(
    val report: ListeningReport? = null,
    val window: StatsWindow = StatsWindow(StatsPeriod.WEEK),
    val isLoading: Boolean = true,
    /** True when the numbers cover every device on the Agro account rather than this one. */
    val isFleetWide: Boolean = false,
    val error: String? = null
)
