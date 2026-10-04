package com.wander.android.ui.components.newplaylist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.ConnectedToggleGroup
import com.wander.android.ui.components.WandaSheet

/**
 * Every way a playlist comes into existence, in one sheet: a plain one, one friends edit with you
 * on Agro, or a Blend Agro writes from everyone's listening.
 *
 * The kinds that need Agro appear only when it is there, and a Blend only where nothing has been
 * picked yet — [allowBlend] is false when the sheet opens to hold [tracks], because Agro chooses a
 * Blend's songs and would replace them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NewPlaylistSheet(
    onCreated: (playlistId: String) -> Unit,
    onDismiss: () -> Unit,
    tracks: List<UnifiedTrack> = emptyList(),
    plainSource: SourceType = SourceType.LOCAL,
    allowBlend: Boolean = true,
    viewModel: NewPlaylistViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val kinds = state.kinds.filter { allowBlend || it != NewPlaylistKind.BLEND }
    val kind = state.kind.takeIf { it in kinds } ?: NewPlaylistKind.PLAYLIST
    val you = stringResource(R.string.new_playlist_blend_you)
    // A blend nobody named is called after who is in it, and follows the picks until it is named.
    val autoName = (listOf(you) + state.picked).joinToString(" + ")
    val title = if (kind == NewPlaylistKind.BLEND && state.name.isBlank()) autoName else state.name
    val motion = MaterialTheme.motionScheme

    WandaSheet(onDismissRequest = onDismiss) { dismiss ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Text(stringResource(R.string.new_playlist_title), style = MaterialTheme.typography.headlineSmall)

            if (kinds.size > 1) {
                ConnectedToggleGroup(
                    options = kinds,
                    selected = kind,
                    label = { stringResource(it.label) },
                    onSelect = viewModel::setKind
                )
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                singleLine = true,
                label = { Text(stringResource(R.string.common_name)) },
                placeholder = if (kind == NewPlaylistKind.BLEND) ({ Text(autoName) }) else null,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedContent(
                targetState = kind,
                transitionSpec = {
                    fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) using
                        SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                },
                label = "newPlaylistKind"
            ) { shown ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(shown.hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    when (shown) {
                        NewPlaylistKind.PLAYLIST -> Unit
                        NewPlaylistKind.COLLABORATIVE -> CollaborativeOptions(state, viewModel)
                        NewPlaylistKind.BLEND -> BlendOptions(state, viewModel)
                    }
                }
            }

            state.error?.let { error ->
                Text(
                    text = stringResource(error, state.errorDetail.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    viewModel.setKind(kind)
                    viewModel.create(title, tracks, plainSource) { id ->
                        dismiss()
                        onCreated(id)
                    }
                },
                enabled = title.isNotBlank() && !state.isCreating,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isCreating) {
                    LoadingIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.common_create), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private val NewPlaylistKind.label: Int
    get() = when (this) {
        NewPlaylistKind.PLAYLIST -> R.string.new_playlist_kind_playlist
        NewPlaylistKind.COLLABORATIVE -> R.string.new_playlist_kind_collaborative
        NewPlaylistKind.BLEND -> R.string.new_playlist_kind_blend
    }

private val NewPlaylistKind.hint: Int
    get() = when (this) {
        NewPlaylistKind.PLAYLIST -> R.string.new_playlist_kind_playlist_hint
        NewPlaylistKind.COLLABORATIVE -> R.string.new_playlist_kind_collaborative_hint
        NewPlaylistKind.BLEND -> R.string.new_playlist_kind_blend_hint
    }
