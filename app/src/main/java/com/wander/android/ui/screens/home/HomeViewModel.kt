package com.wander.android.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.SmartMix
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.SmartMixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val greeting: String = "",
    val sections: List<HomeSection> = emptyList()
) {
    val isEmpty: Boolean get() = !isLoading && sections.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val smartMixRepository: SmartMixRepository,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
        observeLibrary()
    }

    /**
     * Likes are toggled from every track surface in the app, so the Favourites shelf follows Room
     * rather than waiting for the next [refresh] — previously a like never showed up on Home.
     */
    private fun observeLibrary() {
        viewModelScope.launch {
            musicRepository.getLikedTracksFlow().collect { liked ->
                _uiState.update { state ->
                    state.copy(
                        sections = state.sections.withSection(
                            carousel(SectionLiked, "Your Favourites", liked.take(CarouselSize))
                        )
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val sections = coroutineScope {
                val mixes = async { smartMixRepository.getSmartMixes() }
                val onRepeat = async { musicRepository.getTopTracks(CarouselSize) }
                val jumpBackIn = async { musicRepository.getRecentAlbumStarters(CarouselSize) }
                val recentlyPlayed = async { musicRepository.getRecentlyPlayed(CarouselSize) }
                val liked = async { musicRepository.getLikedTracks(CarouselSize) }
                val discover = async { musicRepository.getNeverPlayed(CarouselSize) }
                val recentAdded = async { musicRepository.getRecentTracks(ListSize) }
                val perSource = musicRepository.configuredSources().map { source ->
                    source to async { musicRepository.getRecentBySource(source, CarouselSize) }
                }

                buildList {
                    add(carousel(SectionOnRepeat, "On Repeat", onRepeat.await()))
                    add(
                        HomeSection(
                            id = SectionMixes,
                            title = "Made For You",
                            style = HomeSectionStyle.MIX_CAROUSEL,
                            mixes = mixes.await()
                        )
                    )
                    add(carousel(SectionJumpBackIn, "Jump Back In", jumpBackIn.await()))
                    add(carousel(SectionRecentlyPlayed, "Recently Played", recentlyPlayed.await()))
                    add(carousel(SectionLiked, "Your Favourites", liked.await()))
                    add(carousel(SectionDiscover, "Discover", discover.await()))
                    perSource.forEach { (source, deferred) ->
                        add(
                            carousel(
                                id = "source_${source.name}",
                                title = "From ${source.displayName}",
                                tracks = deferred.await()
                            )
                        )
                    }
                    add(
                        HomeSection(
                            id = SectionRecentAdded,
                            title = "Recently Added",
                            style = HomeSectionStyle.TRACK_LIST,
                            tracks = recentAdded.await()
                        )
                    )
                }.filterNot(HomeSection::isEmpty)
            }

            _uiState.value = HomeUiState(
                isLoading = false,
                greeting = greeting(),
                sections = sections
            )
        }
    }

    fun playMix(mix: SmartMix) = playerConnection.play(mix.tracks)

    fun play(tracks: List<UnifiedTrack>, index: Int) = playerConnection.play(tracks, index)

    fun toggleLike(track: UnifiedTrack) {
        viewModelScope.launch { musicRepository.toggleLike(track) }
    }

    private fun carousel(id: String, title: String, tracks: List<UnifiedTrack>) =
        HomeSection(id = id, title = title, style = HomeSectionStyle.TRACK_CAROUSEL, tracks = tracks)

    /**
     * Replaces a shelf, adding it if it wasn't there and dropping it once it empties, keeping the
     * canonical shelf order in both cases.
     */
    private fun List<HomeSection>.withSection(section: HomeSection): List<HomeSection> {
        val without = filterNot { it.id == section.id }
        if (section.isEmpty) return without
        return (without + section).sortedBy { existing ->
            SectionOrder.indexOf(existing.id).takeIf { it >= 0 } ?: SectionOrder.size
        }
    }

    /** Time of day the user is most likely reading this. */
    private fun greeting(): String = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

    private companion object {
        const val CarouselSize = 12
        const val ListSize = 20

        const val SectionOnRepeat = "on_repeat"
        const val SectionMixes = "mixes"
        const val SectionJumpBackIn = "jump_back_in"
        const val SectionRecentlyPlayed = "recently_played"
        const val SectionLiked = "liked"
        const val SectionDiscover = "discover"
        const val SectionRecentAdded = "recent_added"

        /** Per-source shelves are unlisted, so they sort after these and before the closing list. */
        val SectionOrder = listOf(
            SectionOnRepeat,
            SectionMixes,
            SectionJumpBackIn,
            SectionRecentlyPlayed,
            SectionLiked,
            SectionDiscover
        )
    }
}
