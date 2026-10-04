package com.wander.android.ui.components.newplaylist

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.BlendRecipe
import com.wander.android.data.sources.agro.BlendRefresh
import com.wander.android.data.sources.agro.BlendWindow
import com.wander.android.ui.components.ConnectedToggleGroup
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.PersonShape

/** Mix moves in tenths: finer than that is a difference nobody could hear in the result. */
private const val MIX_STEPS = 9

/** Who to blend with, and the recipe: how big, how adventurous, how far back, how often. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlendOptions(state: NewPlaylistUiState, viewModel: NewPlaylistViewModel) {
    val recipe = state.recipe
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OptionLabel(R.string.new_playlist_blend_with)
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.new_playlist_blend_picked, state.picked.size, BlendRecipe.MAX_INVITED),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.friends.isEmpty()) {
            Text(
                text = stringResource(R.string.new_playlist_blend_no_friends),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.friends.forEach { friend ->
                val picked = friend.username in state.picked
                FilterChip(
                    selected = picked,
                    onClick = { viewModel.toggleFriend(friend.username) },
                    enabled = picked || state.picked.size < BlendRecipe.MAX_INVITED,
                    label = { Text(friend.displayName ?: friend.username) },
                    leadingIcon = {
                        CuteAvatar(seed = friend.username, avatarUrl = friend.avatarUrl, size = FilterChipDefaults.IconSize, shape = PersonShape)
                    },
                    shape = MaterialTheme.shapes.large
                )
            }
        }

        BlendRecipeControls(recipe, viewModel::setRecipe)

        Text(
            text = stringResource(R.string.new_playlist_blend_consent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** How big, how adventurous, how far back and how often: the part of a blend its creator can change. */
@Composable
internal fun BlendRecipeControls(recipe: BlendRecipe, onRecipe: (BlendRecipe) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OptionLabel(R.string.new_playlist_blend_size)
        ConnectedToggleGroup(
            options = BlendRecipe.SIZES,
            selected = recipe.size,
            label = { it.toString() },
            onSelect = { onRecipe(recipe.copy(size = it)) }
        )

        OptionLabel(R.string.new_playlist_blend_mix)
        MixSlider(recipe.mix) { onRecipe(recipe.copy(mix = it)) }

        OptionLabel(R.string.new_playlist_blend_window)
        ConnectedToggleGroup(
            options = BlendWindow.entries,
            selected = recipe.window,
            label = { stringResource(it.label) },
            onSelect = { onRecipe(recipe.copy(window = it)) }
        )

        OptionLabel(R.string.new_playlist_blend_refresh)
        ConnectedToggleGroup(
            options = BlendRefresh.entries,
            selected = recipe.refresh,
            label = { stringResource(it.label) },
            onSelect = { onRecipe(recipe.copy(refresh = it)) }
        )
    }
}

/** Common ground at one end, discovery at the other, with the expressive tall thumb. */
@Composable
private fun MixSlider(mix: Int, onMix: (Int) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column {
        Slider(
            value = mix.toFloat(),
            onValueChange = { onMix(it.toInt()) },
            valueRange = 0f..100f,
            steps = MIX_STEPS,
            interactionSource = interaction,
            thumb = { SliderDefaults.Thumb(interactionSource = interaction, thumbSize = DpSize(4.dp, 40.dp)) },
            track = { SliderDefaults.Track(sliderState = it, modifier = Modifier.height(16.dp)) }
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.new_playlist_blend_common), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f).size(0.dp))
            Text(stringResource(R.string.new_playlist_blend_discovery), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private val BlendWindow.label: Int
    get() = when (this) {
        BlendWindow.FOUR_WEEKS -> R.string.new_playlist_blend_window_4w
        BlendWindow.SIX_MONTHS -> R.string.new_playlist_blend_window_6m
        BlendWindow.ALL_TIME -> R.string.new_playlist_blend_window_all
    }

private val BlendRefresh.label: Int
    get() = when (this) {
        BlendRefresh.DAILY -> R.string.new_playlist_blend_refresh_daily
        BlendRefresh.WEEKLY -> R.string.new_playlist_blend_refresh_weekly
        BlendRefresh.FROZEN -> R.string.new_playlist_blend_refresh_frozen
    }
