package com.wander.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.repository.MergeReport
import com.wander.android.data.repository.RecordingMergePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class MergePreviewViewModel @Inject constructor(
    private val preview: RecordingMergePreview
) : ViewModel() {

    /** Null while the walk is running. It reads every track row, so it is not instant. */
    private val _report = MutableStateFlow<MergeReport?>(null)
    val report: StateFlow<MergeReport?> = _report.asStateFlow()

    init {
        viewModelScope.launch { _report.value = preview.preview() }
    }
}
