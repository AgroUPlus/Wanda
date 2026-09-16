package com.wander.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.repository.StorageBreakdown
import com.wander.android.data.repository.StorageButlerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StorageButlerUiState(
    val breakdown: StorageBreakdown? = null,
    val isCleaning: Boolean = false
)

@HiltViewModel
class StorageButlerViewModel @Inject constructor(
    private val repository: StorageButlerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StorageButlerUiState())
    val state: StateFlow<StorageButlerUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(breakdown = repository.breakdown())
        }
    }

    fun cleanStaleDownloads() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isCleaning = true)
            repository.cleanStaleDownloads()
            _state.value = _state.value.copy(breakdown = repository.breakdown(), isCleaning = false)
        }
    }
}
