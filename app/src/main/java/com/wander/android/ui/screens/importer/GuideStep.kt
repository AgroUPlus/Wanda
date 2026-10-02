package com.wander.android.ui.screens.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.importer.PlatformType

/**
 * What a person has to do on [this] platform for Wanda to be able to read their playlist, or null
 * when the platform needs no preparation. Spotify, Deezer and Apple Music are read from the link
 * to a public playlist, with no sign-in, so the steps are about making it public and copying the
 * link.
 */
internal fun PlatformType.guideSteps(): List<Int>? = when (this) {
    PlatformType.SPOTIFY -> listOf(
        R.string.importer_guide_spotify_1,
        R.string.importer_guide_spotify_2,
        R.string.importer_guide_spotify_3
    )
    PlatformType.DEEZER -> listOf(
        R.string.importer_guide_deezer_1,
        R.string.importer_guide_deezer_2,
        R.string.importer_guide_deezer_3
    )
    PlatformType.APPLE_MUSIC -> listOf(
        R.string.importer_guide_apple_1,
        R.string.importer_guide_apple_2,
        R.string.importer_guide_apple_3
    )
    PlatformType.YOUTUBE, PlatformType.PLAIN_TEXT -> null
}

/**
 * Step 2 for platforms with no sign-in here: the steps to make a playlist public, a button to the
 * platform to do it, then the bar to paste its link into. There is deliberately no embedded
 * browser — the person already has the app or their own browser, with their own login.
 */
@Composable
internal fun GuideStep(
    platform: PlatformType,
    steps: List<Int>,
    state: PlaylistImportUiState,
    actions: AccessStepActions,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val mismatch = state.mismatchedPlatform?.let {
        stringResource(R.string.importer_wrong_platform, it.displayName, platform.displayName)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.importer_guide_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.importer_guide_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                steps.forEachIndexed { index, step -> GuideStepRow(number = index + 1, text = stringResource(step)) }
            }
        }

        platform.webUrl?.let { url ->
            FilledTonalButton(
                onClick = { uriHandler.openUri(url) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.importer_open_platform, platform.displayName))
            }
        }

        HorizontalDivider()

        ImportDirectLinkContent(
            manualInput = state.manualInput,
            isLoadingPlaylist = state.isLoadingPlaylist,
            error = mismatch ?: state.error,
            onInputChange = actions.onInputChange,
            onLoadPlaylist = actions.onLoadPlaylist,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GuideStepRow(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}
