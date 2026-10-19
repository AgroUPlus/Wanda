package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.listInset
import com.wander.android.ui.screens.artist.RelatedArtistCard

/** The Library's Followed tab: every artist this account follows, as round portraits. */
@Composable
internal fun FollowedArtistsPage(
    contentPadding: PaddingValues,
    onOpenArtist: (String, String?) -> Unit,
    viewModel: FollowedArtistsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Each time the page is composed: coming back from an artist page after a follow lands here.
    LaunchedEffect(Unit) { viewModel.refresh() }

    when (val current = state) {
        FollowedArtistsState.Loading -> Centered { LoadingIndicator() }
        FollowedArtistsState.NotPaired -> Centered {
            EmptyState(
                title = stringResource(R.string.library_followed_not_paired_title),
                message = stringResource(R.string.library_followed_not_paired_message),
                icon = Icons.Rounded.Person
            )
        }
        FollowedArtistsState.Failed -> Centered {
            EmptyState(
                title = stringResource(R.string.library_followed_failed_title),
                message = stringResource(R.string.library_followed_failed_message),
                icon = Icons.Rounded.Person,
                actionLabel = stringResource(R.string.common_try_again),
                onAction = viewModel::refresh
            )
        }
        is FollowedArtistsState.Loaded -> if (current.artists.isEmpty()) {
            Centered {
                EmptyState(
                    title = stringResource(R.string.library_followed_empty_title),
                    message = stringResource(R.string.library_followed_empty_message),
                    icon = Icons.Rounded.Person
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                contentPadding = contentPadding.listInset(),
                modifier = Modifier.fillMaxSize()
            ) {
                items(current.artists, key = { "followed-${it.name}" }) { artist ->
                    Box(contentAlignment = Alignment.Center) {
                        RelatedArtistCard(
                            artist = artist,
                            onClick = { onOpenArtist(artist.name, artist.id.ifBlank { null }) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
