package com.wander.android.ui.screens.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.data.model.SourceType

/**
 * A quiet line naming the backends a search could not reach, when others did answer.
 *
 * Quiet on purpose: the results below are real and complete for the sources that replied, so this
 * qualifies them rather than replacing them — the full error state is for when nobody answered.
 */
@Composable
internal fun UnansweredSourcesNote(
    sources: List<SourceType>,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(
            R.string.search_sources_unanswered,
            sources.joinToString { it.displayName }
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
