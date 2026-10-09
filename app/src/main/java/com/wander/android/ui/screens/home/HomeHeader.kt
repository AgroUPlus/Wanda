package com.wander.android.ui.screens.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R

@Composable
internal fun HomeHeader(
    greeting: String,
    hasSession: Boolean,
    onOpenSessions: () -> Unit,
    onCustomize: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp)
    ) {
        // The greeting is the header. The app's own name told the user nothing they didn't
        // already know from having opened it.
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f)
        )
        // Only present when there is somewhere to hand off from, so the header stays quiet the
        // rest of the time. No live badge: the icon's presence already says a session exists, and
        // the sheet behind it is where "still playing" actually means something.
        if (hasSession) {
            IconButton(onClick = onOpenSessions) {
                Icon(Icons.Rounded.Devices, contentDescription = stringResource(R.string.home_sessions_other_devices))
            }
        }
        IconButton(onClick = onCustomize) {
            Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.home_edit_open))
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.nav_settings))
        }
    }
}
