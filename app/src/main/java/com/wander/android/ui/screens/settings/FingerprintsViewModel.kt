package com.wander.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.audio.fingerprint.FingerprintProgress
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.notification.WorkProgressNotification
import com.wander.android.core.work.WorkControls
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.FingerprintStatus
import com.wander.android.data.repository.FingerprintStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.wander.android.core.database.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One track and what has been measured about it. */
@androidx.compose.runtime.Immutable
internal data class FingerprintRow(
    val track: UnifiedTrack,
    val status: FingerprintStatus
)

/**
 * One group of tracks that share a reason to be looked at together.
 *
 * Split local from streamed because the two fail for completely different reasons and the fix is
 * different. A local file that will not measure has a codec this device cannot decode. A streamed
 * track that will not measure was almost certainly never *reached* — measuring one downloads about
 * a minute of it, so it waits on Wi-Fi, and before that it waited on a charger it rarely saw.
 * Lumping them together turns two answerable questions into one shrug.
 */
@androidx.compose.runtime.Immutable
internal data class FingerprintSection(
    val title: String,
    val subtitle: String,
    val rows: List<FingerprintRow>
) {
    val indexed: Int get() = rows.count { it.status == FingerprintStatus.INDEXED }
}

@androidx.compose.runtime.Immutable
internal data class FingerprintsUiState(
    val sections: List<FingerprintSection> = emptyList(),
    val indexed: Int = 0,
    val total: Int = 0,
    val isLoading: Boolean = true,
    val isPaused: Boolean = false
) {
    /** The one being decoded right now, if any — the line the screen leads with. */
    val processing: FingerprintRow?
        get() = sections.firstNotNullOfOrNull { section ->
            section.rows.firstOrNull { it.status == FingerprintStatus.PROCESSING }
        }
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
    statuses: FingerprintStatusRepository,
    private val workControls: WorkControls,
    private val progress: FingerprintProgress
) : ViewModel() {

    /** Pauses measuring, or lifts the pause and starts it again. */
    fun setPaused(paused: Boolean) {
        if (paused) {
            workControls.pause(WorkProgressNotification.Kind.FINGERPRINT)
        } else {
            // An explicit resume is also the user saying "try the ones that failed again" — it is
            // the only gesture that means that, and without it a track the indexer could not reach
            // stays skipped for the life of the process.
            progress.retryFailures()
            workControls.resume(WorkProgressNotification.Kind.FINGERPRINT)
        }
    }

    /**
     * The whole library, re-sorted whenever a measurement lands.
     *
     * This screen genuinely needs every row — it groups and counts them — so it is the one place
     * that legitimately loads the lot. What it must not do is *re-map* the lot on every tick, and
     * that is what it was doing: `statuses()` carries a one-second ticker so that a failed
     * measurement eventually turns red, and combining it directly meant a thousand
     * entity-to-model conversions and a thousand-row sort every second, for a screen showing
     * thirty rows.
     *
     * The tracks are therefore mapped only when *the tracks* change, and the statuses are folded in
     * afterwards — the sort is over rows that already exist, and the ticker no longer touches the
     * conversion at all.
     */
    private val libraryRows: Flow<List<UnifiedTrack>> = trackDao.getAllTracksFlow()
        .map { entities -> entities.map(TrackEntity::toUnifiedTrack) }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<FingerprintsUiState> = combine(
        libraryRows,
        // Conflated: during an indexing pass the statuses change faster than a person can read
        // them, and every intermediate state costs a full sort. The screen wants the latest answer,
        // not each of them.
        statuses.statuses().conflate(),
        workControls.isPaused(WorkProgressNotification.Kind.FINGERPRINT)
    ) { tracks, byId, paused ->
        val rows = tracks
            .map { track -> FingerprintRow(track, byId[track.id] ?: FingerprintStatus.MISSING) }
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

        // Ordered by how likely the group is to be the one you came here about, not by size.
        //
        // External first — YouTube Music and anything else that is neither a local file nor your
        // own server. Those are the tracks the indexer reaches last and fails on most, because
        // measuring one means fetching audio from a third party, so they are the group that
        // actually explains a low count. Your own library, local or Navidrome, comes after.
        val byGroup = rows.groupBy { it.track.source }

        val sections = listOfNotNull(
            byGroup[SourceType.YTMUSIC]?.takeIf { it.isNotEmpty() }?.let {
                FingerprintSection(
                    title = "External",
                    subtitle = "YouTube Music. Measuring one fetches about a minute of audio from " +
                        "a third party, so these are the slowest to fill in and the likeliest to " +
                        "fail outright.",
                    rows = it
                )
            },
            byGroup[SourceType.NAVIDROME]?.takeIf { it.isNotEmpty() }?.let {
                FingerprintSection(
                    title = "Navidrome",
                    subtitle = "Your own server. Measuring one downloads about a minute of it, so " +
                        "these fill in over Wi-Fi.",
                    rows = it
                )
            },
            byGroup[SourceType.LOCAL]?.takeIf { it.isNotEmpty() }?.let {
                FingerprintSection(
                    title = "On this device",
                    subtitle = "Local files. These cost nothing but time; one that stays red has " +
                        "a format this phone cannot decode.",
                    rows = it
                )
            }
        )

        FingerprintsUiState(
            sections = sections,
            indexed = rows.count { it.status == FingerprintStatus.INDEXED },
            total = rows.size,
            isLoading = false,
            isPaused = paused
        )
    }
        // The grouping and the thousand-row sort are real work and have no business on the main
        // thread; `combine` runs its transform on the collector's dispatcher by default.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FingerprintsUiState())
}
