package com.wander.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.KeptApartPair
import com.wander.android.data.repository.MergeGroup
import com.wander.android.data.repository.MergeReport
import com.wander.android.data.repository.RecordingMergePreview
import com.wander.android.data.repository.RecordingSplitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class MergePreviewViewModel @Inject constructor(
    private val preview: RecordingMergePreview,
    private val splitRepository: RecordingSplitRepository
) : ViewModel() {

    /** Null while the walk is running. It reads every track row, so it is not instant. */
    private val _report = MutableStateFlow<MergeReport?>(null)
    val report: StateFlow<MergeReport?> = _report.asStateFlow()

    init {
        refresh()
    }

    /**
     * Declares one rendition to be a different performance from the rest of its group.
     *
     * Re-runs the preview afterwards rather than editing the report in place: the pin is what the
     * matcher will read from now on, so the honest confirmation is the recomputed answer — the
     * group either splits in two or leaves the list entirely.
     */
    fun keepApart(group: MergeGroup, rendition: UnifiedTrack) {
        viewModelScope.launch {
            splitRepository.keepApart(rendition, group.renditions)
            refresh()
        }
    }

    /** Lets the matcher have its say about a pair again. The group reappears above if it merges. */
    fun rejoin(pair: KeptApartPair) {
        viewModelScope.launch {
            splitRepository.rejoin(pair.a.id, pair.b.id)
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _report.value = null
            _report.value = preview.preview()
        }
    }
}
