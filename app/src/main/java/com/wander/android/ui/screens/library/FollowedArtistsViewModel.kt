package com.wander.android.ui.screens.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.database.dao.ArtistDao
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.RelatedArtist
import com.wander.android.data.repository.ArtistSubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the Followed tab can say, one answer at a time. */
@Immutable
internal sealed interface FollowedArtistsState {
    data object Loading : FollowedArtistsState

    /** Follows live on the paired Agro server; without one there is nothing to ask. */
    data object NotPaired : FollowedArtistsState

    data object Failed : FollowedArtistsState

    data class Loaded(val artists: List<RelatedArtist>) : FollowedArtistsState
}

/**
 * The artists this account follows, for the Library's Followed tab.
 *
 * Asked of the server each time the tab is shown rather than cached: a follow is made on an artist
 * page one tap away, and a tab that lagged behind it would read as the follow not having worked.
 */
@HiltViewModel
internal class FollowedArtistsViewModel @Inject constructor(
    private val subscriptions: ArtistSubscriptionRepository,
    private val artistDao: ArtistDao,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _state = MutableStateFlow<FollowedArtistsState>(FollowedArtistsState.Loading)
    val state: StateFlow<FollowedArtistsState> = _state.asStateFlow()

    fun refresh() {
        if (!secureStorage.agroConfigured.value) {
            _state.value = FollowedArtistsState.NotPaired
            return
        }
        viewModelScope.launch {
            _state.value = subscriptions.subscribed().fold(
                onSuccess = { followed ->
                    FollowedArtistsState.Loaded(
                        followed
                            .sortedBy { it.displayName.lowercase() }
                            // Blank id when no client has supplied a channel; the page opens by name.
                            .map { RelatedArtist(it.externalId.orEmpty(), it.displayName, portraitFor(it.displayName)) }
                    )
                },
                onFailure = { FollowedArtistsState.Failed }
            )
        }
    }

    /** The cached artist-page portrait, else the cover of their most played track; null if neither. */
    private suspend fun portraitFor(name: String): String? =
        artistDao.getByName(name.lowercase())?.imageUrl ?: artistDao.trackArtworkFor(name)
}
