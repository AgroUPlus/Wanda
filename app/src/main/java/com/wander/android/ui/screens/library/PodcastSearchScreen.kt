package com.wander.android.ui.screens.library

import androidx.compose.foundation.lazy.itemsIndexed
import com.wander.android.ui.components.rememberShelfEntranceScale
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.podcast.PodcastHit
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

private val ArtworkSize = 56.dp

/**
 * Keyword search of the open PodcastIndex directory, for finding a feed to subscribe to.
 *
 * Searches run only when the listener submits the field, not on every keystroke: each one is a
 * request to a third party, and typing alone should send nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PodcastSearchScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: PodcastSearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribed.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(context.getString(message.res, *message.args.toTypedArray()), withDismissAction = true)
        }
    }
    var term by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding.headerInset())) {
            TopAppBar(
                title = { Text(stringResource(R.string.podcasts_search_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
            when (val current = state) {
                PodcastSearchState.NeedsConsent -> PodcastSearchConsent(onAllow = viewModel::allowSearch)
                PodcastSearchState.NeedsKey -> PodcastSearchKeyForm(onSave = viewModel::saveKey)
                else -> {
                    OutlinedTextField(
                        value = term,
                        onValueChange = { term = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        label = { Text(stringResource(R.string.podcasts_search_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search(term) }),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    SearchBody(current, subscribed, viewModel::subscribe, contentPadding)
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(contentPadding.listInset()))
    }
}

@Composable
private fun SearchBody(
    state: PodcastSearchState,
    subscribed: Set<String>,
    onSubscribe: (PodcastHit) -> Unit,
    contentPadding: PaddingValues
) {
    when (state) {
        PodcastSearchState.Searching -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        is PodcastSearchState.Failed -> Message(stringResource(R.string.podcasts_search_failed, state.reason))
        is PodcastSearchState.Results -> if (state.hits.isEmpty()) {
            Message(stringResource(R.string.podcasts_search_none))
        } else {
            LazyColumn(contentPadding = contentPadding.listInset(), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(state.hits, key = { _, hit -> hit.feedUrl }) { index, hit ->
                    HitRow(
                        hit,
                        isSubscribed = hit.feedUrl in subscribed,
                        onSubscribe = { onSubscribe(hit) },
                        modifier = Modifier.animateItem().scale(rememberShelfEntranceScale(index))
                    )
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

@Composable
private fun HitRow(hit: PodcastHit, isSubscribed: Boolean, onSubscribe: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(hit.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (hit.author != null) {
            { Text(hit.author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        leadingContent = {
            Artwork(url = hit.artworkUrl, contentDescription = null, sizeDp = ArtworkSize, modifier = Modifier.size(ArtworkSize))
        },
        trailingContent = {
            FilledTonalButton(onClick = onSubscribe, enabled = !isSubscribed) {
                Text(stringResource(if (isSubscribed) R.string.podcasts_subscribed else R.string.podcasts_add_confirm))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
