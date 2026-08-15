package com.wander.android.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.SmartMix
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.HomeShelfRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.repository.RecommendationRepository
import com.wander.android.data.repository.SmartMixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    /**
     * A pull-to-refresh in progress. Deliberately separate from [isLoading]: that one replaces the
     * whole screen with a spinner, which is right on a cold start and wrong when the user is
     * looking at shelves and pulled them down.
     */
    val isRefreshing: Boolean = false,
    val greeting: String = "",
    val sections: List<HomeSection> = emptyList()
) {
    val isEmpty: Boolean get() = !isLoading && sections.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val homeShelfRepository: HomeShelfRepository,
    private val recommendationRepository: RecommendationRepository,
    private val smartMixRepository: SmartMixRepository,
    private val playerConnection: PlayerConnection,
    private val shareRepository: ShareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Shelves are one-shot reads, so the tracks they hold keep whatever `isLiked` was true when
     * they were fetched. Overlaying Room's liked set is what makes the heart respond to a tap.
     */
    private val likedTrackIds: StateFlow<Set<String>> = musicRepository.getLikedTrackIdsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        refresh()
        observeLibrary()
        observeLikes()
    }

    private fun observeLikes() {
        viewModelScope.launch {
            likedTrackIds.collect { liked ->
                _uiState.update { state -> state.copy(sections = state.sections.withLikes(liked)) }
            }
        }
    }

    private fun List<HomeSection>.withLikes(liked: Set<String>): List<HomeSection> = map { section ->
        section.copy(tracks = section.tracks.map { it.copy(isLiked = it.id in liked) })
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

    /** Pull-to-refresh. Same work as [refresh], but the shelves stay on screen while it runs. */
    fun pullToRefresh() = refresh(showSpinner = false)

    fun refresh(showSpinner: Boolean = true) {
        viewModelScope.launch {
            _uiState.update {
                if (showSpinner) it.copy(isLoading = true) else it.copy(isRefreshing = true)
            }

            val sections = coroutineScope {
                val mixes = async { smartMixRepository.getSmartMixes() }
                val onRepeat = async { homeShelfRepository.getTopTracks(CarouselSize) }
                val jumpBackIn = async { homeShelfRepository.getRecentAlbumStarters(CarouselSize) }
                val recentlyPlayed = async { homeShelfRepository.getRecentlyPlayed(CarouselSize) }
                val liked = async { homeShelfRepository.getLikedTracks(CarouselSize) }
                val discover = async { homeShelfRepository.getNeverPlayed(CarouselSize) }
                val recentAdded = async { musicRepository.getRecentTracks(ListSize) }
                // Recommendations seeded by what you actually played last, rather than by what
                // happens to be sitting unplayed in the library. The seed's own backend supplies
                // them — for YouTube Music that is its radio, which mixes the artist with
                // stylistic neighbours instead of returning the same song over and over.
                val recommended = async {
                    val seed = homeShelfRepository.getRecentlyPlayed(1).firstOrNull()
                    seed to seed?.let { musicRepository.generateRadio(it, CarouselSize) }.orEmpty()
                }
                // The backends' own recommenders. Empty when nothing publishes one — signed out
                // of YouTube Music, Home is the shelves below and nothing is missing.
                val feed = async { recommendationRepository.getShelves() }
                // The Archive is a place to go looking for something, not a library of yours, so
                // it gets no shelf here. Its rows in Room are search residue — every result the
                // Search screen has ever shown — which is the opposite of a recommendation.
                val perSource = musicRepository.configuredSources()
                    .filterNot { it == SourceType.INTERNET_ARCHIVE }
                    .map { source ->
                        source to async {
                            homeShelfRepository.getRecentBySource(source, CarouselSize)
                        }
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
                    // High up on purpose: this is the one genuinely algorithmic part of Home, and
                    // burying it under shelves derived from Room would waste it.
                    feed.await().forEach { shelf ->
                        add(carousel(shelf.id, shelf.title, shelf.tracks.take(CarouselSize)))
                    }
                    add(carousel(SectionJumpBackIn, "Jump Back In", jumpBackIn.await()))
                    add(carousel(SectionRecentlyPlayed, "Recently Played", recentlyPlayed.await()))
                    add(carousel(SectionLiked, "Your Favourites", liked.await()))
                    add(carousel(SectionDiscover, "Discover", discover.await()))
                    val (seed, suggestions) = recommended.await()
                    if (seed != null) {
                        add(
                            carousel(
                                id = SectionBecause,
                                title = "Because you listened to ${seed.title}",
                                // By title, not by id: a radio seeded from a track the user found
                                // by search comes back full of re-uploads and remasters of that
                                // same song under different ids, which is what made this shelf
                                // read as "the thing you just searched for, twelve times".
                                tracks = suggestions
                                    .filter { it.id != seed.id }
                                    .distinctBy { it.title.lowercase() }
                                    .filterNot { it.title.equals(seed.title, ignoreCase = true) }
                            )
                        )
                    }
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
                isRefreshing = false,
                greeting = greeting(),
                sections = sections.withLikes(likedTrackIds.value)
            )
        }
    }

    fun playMix(mix: SmartMix) = playerConnection.play(mix.tracks)

    fun play(tracks: List<UnifiedTrack>, index: Int) = playerConnection.play(tracks, index)

    fun playNext(track: UnifiedTrack) = playerConnection.playNext(listOf(track))

    fun addToQueue(track: UnifiedTrack) = playerConnection.addToQueue(listOf(track))

    /** Plays the track, then fills the queue behind it with its source's radio. */
    fun startRadio(track: UnifiedTrack) {
        viewModelScope.launch {
            playerConnection.play(listOf(track))
            val radio = musicRepository.generateRadio(track)
            if (radio.isNotEmpty()) playerConnection.addToQueue(radio)
        }
    }

    /** Whether this track's backend can mint a public link at all. */
    fun canShare(track: UnifiedTrack) = shareRepository.canShare(track)

    /** The link is published on a shared flow and raised as a share sheet by `WanderApp`. */
    fun share(track: UnifiedTrack) {
        viewModelScope.launch { shareRepository.share(track) }
    }

    fun toggleLike(track: UnifiedTrack) {
        viewModelScope.launch { musicRepository.toggleLike(track) }
    }

    private fun carousel(id: String, title: String, tracks: List<UnifiedTrack>) =
        HomeSection(id = id, title = title, style = HomeSectionStyle.TRACK_CAROUSEL, tracks = tracks)

    /**
     * Replaces a shelf in place, adding it if it wasn't there and dropping it once it empties.
     *
     * Deliberately does **not** re-sort the whole list. It used to, by [SectionOrder] — which
     * silently rearranged Home the first time a like landed, because the recommendation shelves
     * and the per-source shelves are not in that list and all sorted to the end together. The
     * order [refresh] built is the order Home keeps.
     */
    private fun List<HomeSection>.withSection(section: HomeSection): List<HomeSection> {
        val existing = indexOfFirst { it.id == section.id }
        if (existing >= 0) {
            return if (section.isEmpty) filterIndexed { index, _ -> index != existing }
            else toMutableList().also { it[existing] = section }
        }
        if (section.isEmpty) return this

        // New shelf: slot it in ahead of the first shelf it is meant to precede, so it does not
        // simply appear at the bottom of the screen.
        val rank = SectionOrder.indexOf(section.id).takeIf { it >= 0 } ?: return this + section
        val at = indexOfFirst { SectionOrder.indexOf(it.id) > rank }
        return if (at < 0) this + section else toMutableList().also { it.add(at, section) }
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
        const val SectionBecause = "because_you_listened"
        const val SectionRecentAdded = "recent_added"

        /** Per-source shelves are unlisted, so they sort after these and before the closing list. */
        val SectionOrder = listOf(
            SectionOnRepeat,
            SectionMixes,
            SectionJumpBackIn,
            SectionRecentlyPlayed,
            SectionLiked,
            SectionBecause,
            SectionDiscover
        )
    }
}
