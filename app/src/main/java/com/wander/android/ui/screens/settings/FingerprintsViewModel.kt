package com.wander.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.FingerprintStatus
import com.wander.android.data.repository.FingerprintStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One track and what has been measured about it. */
@androidx.compose.runtime.Immutable
internal data class FingerprintRow(
    val track: UnifiedTrack,
    val status: FingerprintStatus
)

@androidx.compose.runtime.Immutable
internal data class FingerprintsUiState(
    val rows: List<FingerprintRow> = emptyList(),
    val indexed: Int = 0,
    val total: Int = 0,
    val isLoading: Boolean = true
) {
    /** The one being decoded right now, if any — the line the screen leads with. */
    val processing: FingerprintRow?
        get() = rows.firstOrNull { it.status == FingerprintStatus.PROCESSING }
}

/**
 * What the indexer has and has not done, for someone asking why a hum did not find their song.
 *
 * Sorted with the unmeasured first. A list of everything sorted by title would bury the answer:
 * the reason to open this screen is to find out what is *missing*, and on a mostly-indexed library
 * that is a handful of rows somewhere in the middle of thousands.
 */
@HiltViewModel
internal class FingerprintsViewModel @Inject constructor(
    trackDao: TrackDao,
    statuses: FingerprintStatusRepository
) : ViewModel() {

    val state: StateFlow<FingerprintsUiState> = combine(
        trackDao.getAllTracksFlow(),
        statuses.statuses()
    ) { tracks, byId ->
        val rows = tracks
            .map { entity ->
                val track = entity.toUnifiedTrack()
                FingerprintRow(track, byId[track.id] ?: FingerprintStatus.MISSING)
            }
            // Processing first, then missing, then done — most actionable at the top.
            .sortedWith(
                compareBy<FingerprintRow> {
                    when (it.status) {
                        FingerprintStatus.PROCESSING -> 0
                        FingerprintStatus.MISSING -> 1
                        FingerprintStatus.INDEXED -> 2
                    }
                }.thenBy { it.track.title.lowercase() }
            )

        FingerprintsUiState(
            rows = rows,
            indexed = rows.count { it.status == FingerprintStatus.INDEXED },
            total = rows.size,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FingerprintsUiState())
}
