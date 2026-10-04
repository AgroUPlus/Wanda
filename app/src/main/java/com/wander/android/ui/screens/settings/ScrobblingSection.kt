package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.security.ScrobbleAccount
import com.wander.android.data.repository.ScrobbleService
import com.wander.android.ui.components.GroupedCard

/**
 * ListenBrainz and Last.fm. Each is off until connected, which is the consent; once connected it
 * has its own switch, so it can be paused without signing out, and a way to sign out entirely.
 */
@Composable
internal fun ScrobblingSection(viewModel: ScrobblingViewModel = hiltViewModel()) {
    val listenBrainz by viewModel.listenBrainz.collectAsStateWithLifecycle()
    val lastFm by viewModel.lastFm.collectAsStateWithLifecycle()
    val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()
    val step by viewModel.lastFmStep.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var connectingListenBrainz by remember { mutableStateOf(false) }

    if (connectingListenBrainz) {
        ListenBrainzTokenDialog(
            busy = busy,
            error = error,
            onOpenSettings = { uriHandler.openUri("https://listenbrainz.org/settings/") },
            onConnect = { token -> viewModel.connectListenBrainz(token) { connectingListenBrainz = false } },
            onDismiss = {
                connectingListenBrainz = false
                viewModel.clearError()
            }
        )
    }
    when (val current = step) {
        LastFmStep.Idle -> Unit
        LastFmStep.NeedsKey -> LastFmKeyDialog(
            onCreateKey = { uriHandler.openUri("https://www.last.fm/api/account/create") },
            onSave = { key, secret -> viewModel.saveLastFmKey(key, secret, uriHandler::openUri) },
            onDismiss = viewModel::cancelLastFm
        )
        is LastFmStep.Approving -> LastFmApproveDialog(
            busy = busy,
            error = error,
            onReopen = { uriHandler.openUri(current.url) },
            onApproved = viewModel::finishLastFm,
            onDismiss = viewModel::cancelLastFm
        )
    }

    val rows = mutableListOf<@Composable () -> Unit>()
    rows += serviceRows(
        name = stringResource(R.string.scrobbling_listenbrainz),
        account = listenBrainz,
        icon = Icons.Rounded.GraphicEq,
        busy = busy,
        onConnect = { connectingListenBrainz = true },
        onEnabled = { viewModel.setEnabled(ScrobbleService.LISTENBRAINZ, it) },
        onDisconnect = { viewModel.disconnect(ScrobbleService.LISTENBRAINZ) }
    )
    rows += serviceRows(
        name = stringResource(R.string.scrobbling_lastfm),
        account = lastFm,
        icon = Icons.Rounded.Radio,
        busy = busy,
        onConnect = { viewModel.startLastFm(uriHandler::openUri) },
        onEnabled = { viewModel.setEnabled(ScrobbleService.LASTFM, it) },
        onDisconnect = { viewModel.disconnect(ScrobbleService.LASTFM) }
    )

    Column {
        SettingsSection(stringResource(R.string.scrobbling_section))
        GroupedCard(items = rows)
        signedOut.forEach { (service, reason) ->
            Text(
                text = stringResource(R.string.scrobbling_signed_out, stringResource(service.label), reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        if (step == LastFmStep.Idle && !connectingListenBrainz) {
            error?.let {
                Text(
                    text = stringResource(R.string.scrobbling_failed, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** One service: a connect row while signed out; a switch and a sign-out row once connected. */
@Composable
private fun serviceRows(
    name: String,
    account: ScrobbleAccount,
    icon: ImageVector,
    busy: Boolean,
    onConnect: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onDisconnect: () -> Unit
): List<@Composable () -> Unit> {
    val user = account.username ?: return listOf({
        SettingsRow(
            title = name,
            subtitle = stringResource(R.string.scrobbling_connect_hint),
            onClick = onConnect,
            enabled = !busy,
            icon = icon
        )
    })
    return listOf(
        {
            SettingsToggle(
                title = name,
                subtitle = stringResource(
                    if (account.enabled) R.string.scrobbling_sending_as else R.string.scrobbling_paused_as,
                    user
                ),
                checked = account.enabled,
                onCheckedChange = onEnabled,
                icon = icon
            )
        },
        {
            SettingsRow(
                title = stringResource(R.string.scrobbling_disconnect, name),
                onClick = onDisconnect,
                destructive = true,
                icon = Icons.AutoMirrored.Rounded.Logout
            )
        }
    )
}

private val ScrobbleService.label: Int
    get() = when (this) {
        ScrobbleService.LISTENBRAINZ -> R.string.scrobbling_listenbrainz
        ScrobbleService.LASTFM -> R.string.scrobbling_lastfm
    }
