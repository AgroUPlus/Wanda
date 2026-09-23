package com.wander.android.ui.screens.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.EpisodeItem
import com.wander.android.data.model.EpisodeState
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.EpisodeProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** Episodes of one show, newest listened first. The show is the episode's artist on YouTube. */
@Immutable
data class PodcastShow(val name: String, val episodes: List<EpisodeItem>)

@Immutable
data class PodcastsUiState(
    val continueListening: List<EpisodeItem> = emptyList(),
    val shows: List<PodcastShow> = emptyList(),
    val filter: EpisodeState? = null,
    /** No episodes at all, as opposed to none matching [filter]. */
    val hasNoEpisodes: Boolean = false
)

/**
 * The Library's Podcasts tab.
 *
 * Its own ViewModel rather than more flows on `LibraryViewModel`: nothing here is shared with the
 * music tabs, and episodes are sorted by listening state, which songs do not have.
 */
@HiltViewModel
class PodcastsViewModel @Inject constructor(
    episodeProgress: EpisodeProgressRepository,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    private val filter = MutableStateFlow<EpisodeState?>(null)

    val uiState: StateFlow<PodcastsUiState> = combine(
        episodeProgress.episodes,
        episodeProgress.inProgress,
        filter
    ) { episodes, inProgress, selected ->
        val byId = episodes.associateBy { it.track.id }
        PodcastsUiState(
            // Ordered by when each was last heard, which `inProgress` carries and `episodes` does not.
            continueListening = inProgress.mapNotNull { byId[it.id] },
            shows = episodes
                .filter { selected == null || it.state == selected }
                .groupBy { it.track.artist }
                .map { (name, items) -> PodcastShow(name, items) },
            filter = selected,
            hasNoEpisodes = episodes.isEmpty()
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PodcastsUiState())

    /** Tapping the selected chip again clears it, back to every episode. */
    fun selectFilter(state: EpisodeState?) {
        filter.value = if (filter.value == state) null else state
    }

    /**
     * Plays one episode on its own. Queueing the rest of a show after it would roll straight into
     * another hour of audio the listener did not choose, which a song list gets away with and a
     * podcast does not. Resume is handled by `PlaybackCoordinator`.
     */
    fun play(episode: UnifiedTrack) = playerConnection.play(listOf(episode), 0)
}
