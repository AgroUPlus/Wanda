package com.wander.android.ui.screens.social

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.data.repository.DropsRepository
import com.wander.android.data.sources.agro.AgroDrop
import com.wander.android.data.sources.agro.AgroFeedApi
import com.wander.android.data.sources.agro.AgroFeedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One thing that happened, whoever it happened to.
 *
 * The three kinds arrive from different places and are stored differently — the circle's events are
 * derived server-side and never cached, a drop is a row in Room — so they are wrapped here rather
 * than flattened into a common record. Nothing is lost, and each still renders as itself.
 */
@Immutable
internal sealed interface ActivityItem {
    /** When it happened, ISO-8601, for the one ordering the whole screen is sorted by. */
    val at: String

    /** Somebody in the circle crossed a threshold, put something on repeat, found a favourite. */
    @Immutable
    data class Milestone(val item: AgroFeedItem) : ActivityItem {
        override val at: String get() = item.at
    }

    /** A friend handed you a song. */
    @Immutable
    data class Shared(val drop: AgroDrop) : ActivityItem {
        override val at: String get() = drop.createdAt
    }
}

/** Which slice of the feed is showing. */
internal enum class ActivityFilter(val label: String) {
    ALL("All"),
    CIRCLE("Circle"),
    SHARED("Shared"),
    RELEASES("Releases")
}

@Immutable
internal data class ActivityUiState(
    val items: List<ActivityItem> = emptyList(),
    val filter: ActivityFilter = ActivityFilter.ALL,
    val unread: Int = 0,
    val loading: Boolean = true
) {
    /** [items] with [filter] applied. Computed here so the screen has nothing to decide. */
    val visible: List<ActivityItem>
        get() = when (filter) {
            ActivityFilter.ALL -> items
            ActivityFilter.CIRCLE -> items.filterIsInstance<ActivityItem.Milestone>()
            ActivityFilter.SHARED -> items.filterIsInstance<ActivityItem.Shared>()
            // Nothing produces these yet; the artist subscriptions that will are not built.
            ActivityFilter.RELEASES -> emptyList()
        }
}

/**
 * Everything that has happened lately, in one list.
 *
 * The circle's activity and the songs friends have sent used to be two screens reached from two
 * tiles, which meant checking whether anything had happened was two journeys and a decision about
 * which one to make first. They are the same question. This answers it once, newest first,
 * whichever kind of thing the newest turns out to be.
 *
 * The circle's *recap* — the anthem, the leaderboards, the taste matrix — deliberately stays on its
 * own screen. It is not something that happened; it is a summary of a period, and folding a set of
 * charts into a chronological feed would put a bar chart between two events and call it news.
 */
@HiltViewModel
internal class ActivityViewModel @Inject constructor(
    private val feedApi: AgroFeedApi,
    private val drops: DropsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        observeDrops()
        refresh()
    }

    fun setFilter(filter: ActivityFilter) {
        _state.update { it.copy(filter = filter) }
    }

    /**
     * Room first and continuously: drops are cached, so the shared half of this list is on screen
     * before any request goes out, and stays live as new ones land.
     */
    private fun observeDrops() {
        viewModelScope.launch {
            drops.inbox.collect { incoming ->
                _state.update { current ->
                    current.copy(
                        items = merge(current.milestones(), incoming),
                        unread = incoming.count { it.readAt == null }
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            drops.refresh()
            val feed = feedApi.friendActivity().getOrElse { emptyList() }
            _state.update { current ->
                current.copy(items = merge(feed, current.shared()), loading = false)
            }
        }
    }

    private fun ActivityUiState.milestones(): List<AgroFeedItem> =
        items.filterIsInstance<ActivityItem.Milestone>().map { it.item }

    private fun ActivityUiState.shared(): List<AgroDrop> =
        items.filterIsInstance<ActivityItem.Shared>().map { it.drop }

    /**
     * Newest first, by the timestamp each kind carries.
     *
     * Compared as strings, which is only correct because both ends emit ISO-8601 in UTC — the one
     * format that sorts lexicographically in the same order it sorts chronologically. Parsing every
     * item on every emission to get the same answer would be work for nothing.
     */
    private fun merge(feed: List<AgroFeedItem>, incoming: List<AgroDrop>): List<ActivityItem> =
        (feed.map(ActivityItem::Milestone) + incoming.map(ActivityItem::Shared))
            .sortedByDescending { it.at }
}
