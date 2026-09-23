package com.wander.android.ui.screens.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * The last card: keep the year, and — if the user says so — forget the plays behind it.
 *
 * The tidy-up is unchecked by default and takes a second confirmation, because it is the one
 * irreversible thing in the story. The copy says plainly what survives and what does not, including
 * the consequence that will not otherwise surface until next December: with the earlier plays gone,
 * more artists will look new next year.
 */
@Composable
internal fun ReplayOutroCard(
    card: ReplayCard.Outro,
    state: ReplayUiState,
    onSave: () -> Unit,
    onSaveAndPurge: () -> Unit,
    onDone: () -> Unit
) {
    var purgeChecked by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    ReplayCardFrame(
        kicker = stringResource(R.string.replay_outro_kicker),
        headline = stringResource(R.string.replay_outro_headline, card.report.year)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BlockGap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                state.purgedCount != null -> Text(
                    text = pluralStringResource(
                        R.plurals.replay_outro_purged,
                        state.purgedCount,
                        state.purgedCount
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = replayMuted,
                    textAlign = TextAlign.Center
                )

                state.isSaved -> Text(
                    text = stringResource(R.string.replay_outro_saved),
                    style = MaterialTheme.typography.bodyLarge,
                    color = replayMuted,
                    textAlign = TextAlign.Center
                )

                else -> Text(
                    text = stringResource(R.string.replay_outro_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = replayMuted,
                    textAlign = TextAlign.Center
                )
            }

            state.actionFailure?.let { failure ->
                Text(
                    text = stringResource(failure),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            // Offered only while there is actually something older to forget, and only before it
            // has been done. Otherwise the card is just a receipt.
            if (state.purgedCount == null && state.purgeableCount > 0) {
                PurgeOffer(
                    year = card.report.year,
                    count = state.purgeableCount,
                    checked = purgeChecked,
                    onCheckedChange = {
                        purgeChecked = it
                        confirming = false
                    }
                )
            }

            Actions(
                state = state,
                purgeChecked = purgeChecked,
                confirming = confirming,
                onConfirmNeeded = { confirming = true },
                onSave = onSave,
                onSaveAndPurge = onSaveAndPurge,
                onDone = onDone
            )
        }
    }
}

@Composable
private fun PurgeOffer(
    year: Int,
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CheckboxGap),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column {
            Text(
                text = pluralStringResource(R.plurals.replay_purge_label, count, count, year),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current
            )
            Text(
                text = stringResource(R.string.replay_purge_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = replayMuted
            )
        }
    }
}

@Composable
private fun Actions(
    state: ReplayUiState,
    purgeChecked: Boolean,
    confirming: Boolean,
    onConfirmNeeded: () -> Unit,
    onSave: () -> Unit,
    onSaveAndPurge: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ButtonGap),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (confirming) {
            Text(
                text = stringResource(R.string.replay_purge_confirm_question),
                style = MaterialTheme.typography.titleMedium,
                color = LocalContentColor.current,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = {
                when {
                    // Two steps, never one tap: the first press asks, the second does it.
                    purgeChecked && !confirming -> onConfirmNeeded()
                    purgeChecked -> onSaveAndPurge()
                    else -> onSave()
                }
            },
            enabled = !state.isWorking,
            shapes = ButtonDefaults.shapesFor(ActionHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ActionHeight),
            modifier = Modifier.fillMaxWidth().heightIn(min = ActionHeight)
        ) {
            ActionLabel(
                icon = Icons.Rounded.Bookmark,
                text = when {
                    confirming -> stringResource(R.string.replay_purge_confirm_action)
                    purgeChecked -> stringResource(R.string.replay_outro_save_and_tidy)
                    else -> stringResource(R.string.replay_outro_save)
                }
            )
        }

        FilledTonalButton(
            onClick = onDone,
            enabled = !state.isWorking,
            shapes = ButtonDefaults.shapesFor(ActionHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ActionHeight),
            modifier = Modifier.fillMaxWidth().heightIn(min = ActionHeight)
        ) {
            ActionLabel(icon = Icons.Rounded.Close, text = stringResource(R.string.replay_outro_close))
        }
    }
}

/** Icon and label at the sizes M3 Expressive specifies for [ActionHeight]. */
@Composable
private fun ActionLabel(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(ActionHeight)))
    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ActionHeight)))
    Text(text = text, style = ButtonDefaults.textStyleFor(ActionHeight))
}

/**
 * M3 Expressive's Medium button: the last card's two actions end the story, so they get more than
 * the default 40dp — and the shape springs square-ish under the finger, as every Expressive button
 * does.
 */
private val ActionHeight = ButtonDefaults.MediumContainerHeight

private val BlockGap = 20.dp
private val ButtonGap = 12.dp
private val CheckboxGap = 8.dp
