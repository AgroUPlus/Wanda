package com.wander.android.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlaybackCoordinator
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.SmartMix
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.R
import com.wander.android.data.christian.ChristianShelfRepository
import com.wander.android.data.repository.EpisodeProgressRepository
import com.wander.android.data.repository.FriendPicksRepository
import com.wander.android.data.repository.HomeShelfRepository
import com.wander.android.data.repository.ServiceShelvesRepository
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.repository.RecommendationRepository
import com.wander.android.ui.screens.home.layout.HomeLayoutActions
import com.wander.android.ui.screens.home.layout.HomeLayoutStore
import com.wander.android.ui.screens.home.layout.ShelfUsageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val recommendationRepository: RecommendationRepository,
    private val homeShelfRepository: HomeShelfRepository,
    private val shareRepository: ShareRepository,
    private val playerConnection: PlayerConnection,
    private val playbackCoordinator: PlaybackCoordinator,
    private val layoutStore: HomeLayoutStore,
    val shelfUsage: ShelfUsageStore,
    christianShelf: ChristianShelfRepository,
    friendPicks: FriendPicksRepository,
    serviceShelves: ServiceShelvesRepository,
    episodeProgress: EpisodeProgressRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val extras = ExtraShelves(homeShelfRepository, christianShelf, friendPicks, serviceShelves, context)

    /** Why a Popular on Agro or YouTube Music shelf has nothing to show, for the customizer to say. */
    val shelfProblems = extras.problems

    /** The customizer's actions; see [HomeLayoutActions]. */
    internal val layoutActions = HomeLayoutActions(layoutStore, _uiState)

    /** Genres the library has enough songs in to offer as a shelf. */
    val genres: StateFlow<List<String>> = homeShelfRepository.genres
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Shelves are one-shot reads, so the tracks they hold keep whatever `isLiked` was true when
     * they were fetched. Overlaying Room's liked set is what makes the heart respond to a tap.
     */
    private val likedTrackIds: StateFlow<Set<String>> = musicRepository.getLikedTrackIdsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Episodes started and not finished. Held eagerly so [refresh], which rebuilds Home from
     * scratch, can put the shelf straight back instead of waiting for Room to change again.
     */
    private val continueListening: StateFlow<HomeSection> = combine(
        episodeProgress.inProgress,
        episodeProgress.fractions
    ) { tracks, fractions ->
        shelf(SectionContinueListening, context.getString(R.string.home_your_episodes), HomeSectionStyle.TRACK_CAROUSEL, tracks.take(CarouselSize))
            .copy(progress = fractions)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeSection(SectionContinueListening, "", HomeSectionStyle.TRACK_CAROUSEL))

    init {
        refresh()
        observeLibrary()
        observeLikes()
        observeLayout()
    }

    /** The user's shelf layout; Home re-derives its sections when it changes. */
    private fun observeLayout() {
        viewModelScope.launch {
            layoutStore.editing.collect { editing -> _uiState.update { it.copy(editing = editing) } }
        }
        viewModelScope.launch {
            layoutStore.editRequested.collect { requested ->
                if (!requested) return@collect
                layoutActions.start()
                layoutStore.clearEditRequest()
            }
        }
        viewModelScope.launch {
            layoutStore.layout.collect { layout ->
                _uiState.update { it.copy(layout = layout) }
                // Shelves the user added have no tracks until they are read, or after their settings change.
                val changes = extras.sync(layout, _uiState.value.allSections)
                _uiState.update { it.copy(allSections = changes.applyTo(it.allSections)) }
            }
        }
    }

    private fun observeLikes() {
        viewModelScope.launch {
            likedTrackIds.collect { liked ->
                val updated = withContext(Dispatchers.Default) { _uiState.value.allSections.withLikes(liked) }
                _uiState.update { it.copy(allSections = updated) }
            }
        }
    }

    /**
     * Likes are toggled from every track surface in the app, so the Favourites shelf follows Room
     * rather than waiting for the next [refresh] — previously a like never showed up on Home.
     */
    private fun observeLibrary() {
        viewModelScope.launch {
            continueListening.collect { shelf ->
                _uiState.update { it.copy(allSections = it.allSections.withSection(shelf)) }
            }
        }
        viewModelScope.launch {
            musicRepository.getLikedTracksFlow().collect { liked ->
                _uiState.update { state ->
                    state.copy(
                        allSections = state.allSections.withSection(
                            shelf(SectionLiked, "Your Favorites", HomeSectionStyle.HERO_CAROUSEL, liked.take(CarouselSize))
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
            // If we already have sections rendered, keep them on screen without showing full spinner
            val hasExisting = _uiState.value.allSections.isNotEmpty()
            if (showSpinner && !hasExisting) {
                _uiState.update { it.copy(isLoading = true) }
            } else {
                _uiState.update { it.copy(isRefreshing = true) }
            }

            // Phase 1: Instant Local-First Room Database Read (< 5ms)
            var sources: List<SourceType> = emptyList()
            val localSections = coroutineScope {
                // Read before the shelves rather than beside them: the lead shelf is built across
                // whichever backends are configured, so it cannot start until that is known.
                sources = musicRepository.configuredSources()
                val onRepeat = async { homeShelfRepository.getQuickPicks(CarouselSize, sources) }
                val recentlyPlayed = async { homeShelfRepository.getRecentlyPlayed(CarouselSize) }
                val liked = async { homeShelfRepository.getLikedTracks(CarouselSize) }
                val discover = async { homeShelfRepository.getNeverPlayed(CarouselSize) }

                buildList {
                    // The lead shelf earns legible full-width rows; the second earns big
                    // artwork. The rest stay carousels, so the top of Home has a shape to it.
                    add(shelf(SectionOnRepeat, "Quick picks", HomeSectionStyle.TRACK_PAGER, onRepeat.await()))
                    add(shelf(SectionRecentlyPlayed, "Recently Played", HomeSectionStyle.HERO_CAROUSEL, recentlyPlayed.await()))
                    add(continueListening.value)
                    add(shelf(SectionLiked, "Your Favorites", HomeSectionStyle.HERO_CAROUSEL, liked.await()))
                    add(shelf(SectionDiscover, "Discover", HomeSectionStyle.DISCOVER_MASONRY, discover.await()))
                    addAll(extras.sync(layoutStore.layout.first(), emptyList(), force = true).sections)
                }.filterNot(HomeSection::isEmpty)
            }

            // Immediately emit local shelves so the screen pops up in 0ms with zero blocking
            _uiState.value = HomeUiState(
                isLoading = false,
                isRefreshing = false,
                greeting = greeting(LocalTime.now()),
                layout = _uiState.value.layout,
                editing = _uiState.value.editing,
                allSections = localSections.withLikes(likedTrackIds.value),
                sources = sources,
                // A filter survives a refresh: it is how the user is looking at Home, not a
                // property of the data underneath it.
                selectedSource = _uiState.value.selectedSource?.takeIf { it in sources }
            )

            // Phase 2: Non-blocking Background Network Enrichment (with 7s timeout)
            launch {
                runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(7000) {
                        val feedDeferred = async { recommendationRepository.getShelves() }
                        val recommendedDeferred = async {
                            val seed = homeShelfRepository.getRecentlyPlayed(1).firstOrNull()
                            seed to seed?.let { musicRepository.generateRadio(it, CarouselSize) }.orEmpty()
                        }
                        // Only the shelves that are a kind of music; see `RecommendedShelf.isGeneric`.
                        val feed = feedDeferred.await().filter { it.isGeneric }
                        val (seed, suggestions) = recommendedDeferred.await()

                        if (feed.isNotEmpty() || (seed != null && suggestions.isNotEmpty())) {
                            _uiState.update { state ->
                                val feedIds = feed.map { it.id }.toSet()
                                val updated = buildList {
                                    // Keep On Repeat first
                                    state.allSections.find { it.id == SectionOnRepeat }?.let { add(it) }
                                    // ...and Recently Played straight under it, ahead of the feed
                                    state.allSections.find { it.id == SectionRecentlyPlayed }?.let { add(it) }
                                    // Add online recommendation feed shelves
                                    feed.forEach { shelf ->
                                        add(carousel(shelf.id, shelf.title, shelf.tracks.take(CarouselSize)))
                                    }
                                    // Add remaining local sections without duplicating feed or because
                                    state.allSections.filterNot { it.id == SectionOnRepeat || it.id == SectionRecentlyPlayed || it.id in feedIds || it.id == SectionBecause }.forEach { add(it) }
                                    // Add seed radio recommendations
                                    if (seed != null && suggestions.isNotEmpty()) {
                                        add(
                                            shelf(
                                                id = SectionBecause,
                                                title = "Because you listened to ${seed.title}",
                                                style = HomeSectionStyle.FEATURED_HERO,
                                                tracks = suggestions
                                                    .filter { it.id != seed.id }
                                                    .distinctBy { it.title.lowercase() }
                                                    .filterNot { it.title.equals(seed.title, ignoreCase = true) }
                                            )
                                        )
                                    }
                                }.filterNot(HomeSection::isEmpty)
                                state.copy(allSections = updated.withLikes(likedTrackIds.value))
                            }
                        }
                    }
                }
                // Background sync of recent tracks for next launch
                runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(4000) {
                        musicRepository.getRecentTracks(ListSize)
                    }
                }
            }
        }
    }

    fun playMix(mix: SmartMix) = playerConnection.play(mix.tracks)

    fun playNext(track: UnifiedTrack) = playerConnection.playNext(listOf(track))

    fun addToQueue(track: UnifiedTrack) = playerConnection.addToQueue(listOf(track))

    /**
     * Plays the track, then fills the queue behind it with its source's radio.
     *
     * Episodes and livestreams have no station to build, so they play alone.
     */
    fun startRadio(track: UnifiedTrack) {
        if (track.isEpisode || track.isLive) {
            playerConnection.play(listOf(track))
            return
        }
        viewModelScope.launch { playbackCoordinator.startRadio(track) }
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

    /** Narrows Home to one backend, or back to all of them. Filters; never refetches. */
    fun selectSource(source: SourceType?) {
        _uiState.update { it.copy(selectedSource = source) }
    }
}
