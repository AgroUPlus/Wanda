package com.wander.android.ui.screens.playlist.blend

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Blender
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.sources.agro.BlendInfo
import com.wander.android.data.sources.agro.BlendRefresh
import com.wander.android.ui.components.ConfirmRequest
import com.wander.android.ui.components.rememberConfirmState
import com.wander.android.ui.screens.library.blend.Faces
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/**
 * Who a blend is made of and when it changes next, over its tracks — with Edit and End for the one
 * who made it, and Leave for everyone else.
 */
@Composable
internal fun BlendBanner(agroId: String, onLeft: () -> Unit, viewModel: BlendBannerViewModel = hiltViewModel()) {
    LaunchedEffect(agroId) { viewModel.load(agroId) }
    LaunchedEffect(viewModel) { viewModel.left.collect { onLeft() } }
    val info by viewModel.info.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val confirm = rememberConfirmState()
    val colors = MaterialTheme.colorScheme
    val blend = info
    if (blend == null) {
        error?.let { BlendUnavailable(it, onLeave = { viewModel.leave(agroId) }) }
        return
    }

    if (editing) {
        EditBlendSheet(
            blend = blend,
            onSave = { title, recipe -> viewModel.update(agroId, title, recipe) },
            onDismiss = { editing = false }
        )
    }
    val leave = ConfirmRequest(
        title = stringResource(if (blend.isCreator) R.string.blend_end_confirm_title else R.string.blend_leave_confirm_title),
        message = stringResource(if (blend.isCreator) R.string.blend_end_confirm_message else R.string.blend_leave_confirm_message),
        confirmLabel = stringResource(if (blend.isCreator) R.string.blend_banner_end else R.string.blend_banner_leave),
        onConfirm = { viewModel.leave(agroId) }
    )
    val joined = blend.members.filter { it.joined }.map { it.username }
    val waiting = blend.members.count { !it.joined }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Faces(joined.take(5), size = 36)
                Spacer(Modifier.width(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.background(colors.tertiaryContainer, CircleShape).padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
                ) {
                    Icon(Icons.Rounded.Blender, null, tint = colors.onTertiaryContainer, modifier = Modifier.size(14.dp))
                    Text(
                        text = stringResource(R.string.blend_banner_badge),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = colors.onTertiaryContainer
                    )
                }
            }
            Text(
                text = stringResource(R.string.blend_banner_with, joined.joinToString { "@$it" }),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = listOfNotNull(
                    refreshLine(blend),
                    waiting.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.blend_banner_invited, it, it) }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (blend.isCreator) {
                    FilledTonalButton(onClick = { editing = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.blend_banner_edit))
                    }
                }
                OutlinedButton(onClick = { confirm.ask(leave) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(if (blend.isCreator) R.string.blend_banner_end else R.string.blend_banner_leave),
                        color = colors.error
                    )
                }
            }
        }
    }
}

/**
 * The blend's details would not load — most often because it has ended. Leave stays reachable
 * here, since otherwise a copy nobody can open any more would have no way out of the library.
 */
@Composable
private fun BlendUnavailable(error: String, onLeave: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onLeave, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.blend_banner_leave))
            }
        }
    }
}

/**
 * When the tracks change next. The server writes the time; one it wrote unparseably is a server
 * bug and is shown as it came rather than replaced with a guess.
 */
@Composable
private fun refreshLine(blend: BlendInfo): String = when {
    blend.recipe.refresh == BlendRefresh.FROZEN -> stringResource(R.string.blend_banner_frozen)
    blend.nextRefreshAt == null -> stringResource(R.string.blend_banner_pending)
    else -> stringResource(R.string.blend_banner_next, formatWhen(blend.nextRefreshAt))
}

private fun formatWhen(at: String): String = try {
    OffsetDateTime.parse(at).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
} catch (_: DateTimeParseException) {
    at
}
