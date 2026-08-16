package com.wander.android.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.repository.StatsRepository
import com.wander.android.data.sources.agro.AgroStats
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
        load(StatsPeriod.MONTH)
    }

    fun setPeriod(period: StatsPeriod) {
        if (period == _state.value.period) return
        load(period)
    }

    fun retry() {
        load(_state.value.period)
    }

    private fun load(period: StatsPeriod) {
        _state.update { it.copy(period = period, isLoading = true, error = null) }
        viewModelScope.launch {
            repository.stats(period)
                .onSuccess { stats ->
                    _state.update {
                        it.copy(stats = stats, isLoading = false, isFleetWide = repository.isFleetWide)
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
    val stats: AgroStats? = null,
    val period: StatsPeriod = StatsPeriod.MONTH,
    val isLoading: Boolean = true,
    /** True when the numbers cover every device on the Agro account rather than this one. */
    val isFleetWide: Boolean = false,
    val error: String? = null
)
