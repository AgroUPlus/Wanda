package com.wander.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.rememberHaptics

@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp)
    )
}

/**
 * A setting row's glyph, on a plain tonal circle — discreet rather than the settings hub's own
 * per-category coloured badge ([SettingsCategoryRow]): one setting has no hue of its own to give
 * it, so this is the same neutral surface tone for every row rather than a colour invented per
 * icon. Still a real circle behind it, not a bare floating glyph, so a page of a dozen rows reads
 * as a column of distinct controls instead of a column of icons that happen to have labels.
 */
@Composable
private fun SettingsIconBadge(
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(IconBadgeSize)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest
                    .copy(alpha = if (enabled) 1f else DisabledAlpha),
                CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
                .copy(alpha = if (enabled) 1f else DisabledAlpha),
            modifier = Modifier.size(IconGlyphSize)
        )
    }
}

private val IconBadgeSize = 40.dp
private val IconGlyphSize = 22.dp

/**
 * [enabled] dims the row and drops its click rather than hiding it.
 *
 * A setting that vanishes when another setting turns on is a setting the user cannot find again,
 * and cannot tell was ever there. Greyed out says both what exists and that something else is
 * currently in charge of it.
 */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    /** A second, less common action on the same row. Requires [onClick] to be set. */
    onLongClick: (() -> Unit)? = null,
    /**
     * A badge for a row that names something — a server, an account, a device. See [AgroBadge]
     * for why most rows deliberately have none. Mutually exclusive with [icon] in practice — a row
     * names one thing, not two.
     */
    leading: (@Composable () -> Unit)? = null,
    /** A circled glyph (see [SettingsIconBadge]) for a row that doesn't name a thing — most rows. */
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        leading?.invoke()
        if (leading == null && icon != null) {
            SettingsIconBadge(icon = icon, enabled = enabled)
        }

        Column(
            modifier = if (leading != null || icon != null) Modifier.padding(start = 16.dp) else Modifier
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha)
                    destructive -> MaterialTheme.colorScheme.error
                    else -> Color.Unspecified
                }
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = if (enabled) 1f else DisabledAlpha)
                )
            }
        }
    }
}

/** See [SettingsRow] for why [enabled] greys the row out rather than removing it. */
@Composable
fun SettingsToggle(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    /** A circled glyph — see [SettingsRow]'s equivalent parameter. */
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable {
                        // The row and the switch are one control, so the tick fires here rather
                        // than only on the `Switch` — tapping the label must feel like tapping the
                        // switch, because it is.
                        haptics.toggled(!checked)
                        onCheckedChange(!checked)
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        if (icon != null) {
            SettingsIconBadge(icon = icon, enabled = enabled, modifier = Modifier.padding(end = 16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    Color.Unspecified
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha)
                }
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = if (enabled) 1f else DisabledAlpha)
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Material's standard disabled opacity. */
private const val DisabledAlpha = 0.38f
