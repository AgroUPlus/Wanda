package com.wander.android.ui.screens.welcome

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.GroupedCard
import com.wander.android.ui.components.rememberShelfEntranceScale

/** The first step: where music comes from, plus the optional "restore instead" escape hatch. */
@Composable
internal fun SourcesStep(
    status: SetupStatus,
    onGrantLocal: () -> Unit,
    onNavidromeLogin: () -> Unit,
    onYouTubeLogin: () -> Unit,
    onImportedBackup: () -> Unit
) {
    Text(text = stringResource(R.string.welcome_welcome_wanda), style = MaterialTheme.typography.headlineLarge)
    Text(
        text = stringResource(R.string.welcome_one_library_one_queue_one),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    GroupedCard(
        items = listOf<@Composable () -> Unit>(
            {
                SourceSetupCard(
                    modifier = Modifier.scale(rememberShelfEntranceScale(0)),
                    title = stringResource(R.string.common_music_device),
                    description = stringResource(R.string.welcome_plays_offline_costs_nothing_needs),
                    icon = Icons.Rounded.MusicNote,
                    isConfigured = status.localGranted,
                    actionLabel = "Grant access",
                    onAction = onGrantLocal
                )
            },
            {
                SourceSetupCard(
                    modifier = Modifier.scale(rememberShelfEntranceScale(1)),
                    title = stringResource(R.string.common_navidrome),
                    description = stringResource(R.string.welcome_own_subsonic_compatible_server_full),
                    icon = Icons.Rounded.Cloud,
                    isConfigured = status.navidromeConfigured,
                    actionLabel = "Sign in",
                    onAction = onNavidromeLogin
                )
            },
            {
                SourceSetupCard(
                    modifier = Modifier.scale(rememberShelfEntranceScale(2)),
                    title = stringResource(R.string.common_youtube_music),
                    description = stringResource(R.string.welcome_signs_through_own_session_cookie),
                    icon = Icons.Rounded.LibraryMusic,
                    isConfigured = status.ytMusicConfigured,
                    actionLabel = "Sign in",
                    onAction = onYouTubeLogin
                )
            }
        )
    )

    ImportBackupAction(onImported = onImportedBackup)
}
