package com.wander.android.ui.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.database.entity.PodcastEntity
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset
import com.wander.android.ui.screens.settings.ConfirmDialog

private const val OPML_MIME = "text/x-opml"
private const val OPML_FILE_NAME = "wanda-podcasts.opml"
private val ArtworkSize = 56.dp

/** The feeds the listener follows: add one, import or export them as OPML, check for episodes, remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PodcastSubscriptionsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: PodcastSubscriptionsViewModel = hiltViewModel()
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val busy by viewModel.isBusy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(context.getString(message.res, *message.args.toTypedArray()), withDismissAction = true)
        }
    }

    var showAdd by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<PodcastEntity?>(null) }
    // Any file, not just OPML: pickers tag .opml inconsistently, and a wrong pick fails with a message.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importOpml)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(OPML_MIME)) { uri ->
        uri?.let(viewModel::exportOpml)
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding.headerInset())) {
            TopAppBar(
                title = { Text(stringResource(R.string.podcasts_subscriptions_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.podcasts_add))
                    }
                    IconButton(onClick = viewModel::refresh, enabled = !busy) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.podcasts_refresh))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.podcasts_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.podcasts_import_opml)) },
                                onClick = { menuOpen = false; importLauncher.launch(arrayOf("*/*")) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.podcasts_export_opml)) },
                                onClick = { menuOpen = false; exportLauncher.launch(OPML_FILE_NAME) }
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                scrollBehavior = scrollBehavior
            )
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val list = subscriptions
            when {
                list == null -> Unit
                list.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(R.string.podcasts_subscriptions_empty_title),
                        message = stringResource(R.string.podcasts_subscriptions_empty_message),
                        icon = Icons.Rounded.Podcasts,
                        actionLabel = stringResource(R.string.podcasts_add),
                        onAction = { showAdd = true }
                    )
                }
                else -> LazyColumn(
                    contentPadding = contentPadding.listInset(),
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
                ) {
                    items(list, key = { it.feedUrl }) { podcast ->
                        SubscriptionRow(podcast, onRemove = { removing = podcast }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(contentPadding.listInset()))
    }

    if (showAdd) AddPodcastDialog(onConfirm = viewModel::add, onDismiss = { showAdd = false })
    removing?.let { podcast ->
        ConfirmDialog(
            title = stringResource(R.string.podcasts_unsubscribe_title, podcast.title),
            message = stringResource(R.string.podcasts_unsubscribe_message),
            confirmLabel = stringResource(R.string.podcasts_unsubscribe),
            onConfirm = { viewModel.remove(podcast.feedUrl) },
            onDismiss = { removing = null }
        )
    }
}

@Composable
private fun SubscriptionRow(podcast: PodcastEntity, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(podcast.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (podcast.author != null) {
            { Text(podcast.author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        leadingContent = {
            Artwork(
                url = podcast.artworkUrl,
                contentDescription = null,
                sizeDp = ArtworkSize,
                modifier = Modifier.size(ArtworkSize)
            )
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.podcasts_unsubscribe))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
    )
}
