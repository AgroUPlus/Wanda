package com.wander.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.wander.android.R
import com.wander.android.core.permissions.NOTIFICATION_PERMISSION
import com.wander.android.core.permissions.hasPermission

/**
 * The switch for new-release notifications, which asks for the right to notify as it is turned on.
 *
 * The permission used to be asked for only once, bundled with audio access at first launch. Refused
 * there, this switch turned on, every check ran, and each notification it tried to post vanished.
 * Now turning it on asks, a refusal leaves it off, and if notifications are blocked later the
 * subtitle says so instead of the setting looking as if it worked.
 */
@Composable
internal fun ReleaseNotificationsToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }

    // A grant or a block made in system settings is picked up on coming back.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (granted) onCheckedChange(true)
    }

    SettingsToggle(
        modifier = modifier,
        title = stringResource(R.string.settings_new_music_from_artists_follow),
        subtitle = stringResource(
            if (checked && !allowed) {
                R.string.settings_release_notifications_blocked
            } else {
                R.string.settings_checks_every_few_hours_wi
            }
        ),
        checked = checked,
        onCheckedChange = { turnOn ->
            val permission = NOTIFICATION_PERMISSION
            if (turnOn && permission != null && !context.hasPermission(permission)) {
                launcher.launch(permission)
            } else {
                onCheckedChange(turnOn)
            }
        },
        icon = Icons.Rounded.NewReleases
    )
}
