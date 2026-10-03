package com.wander.android.ui.screens.social

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.R
import com.wander.android.data.sources.agro.Jam
import com.wander.android.data.sources.agro.JamMode
import com.wander.android.ui.components.ConnectedToggleGroup
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.PersonShape
import com.wander.android.ui.theme.buttonSmall
import com.wander.android.ui.theme.joinCode
import kotlinx.coroutines.launch

/**
 * The join code, who is here, and the room rules. Ending or leaving the jam is not in here any
 * more — it is the top bar's job, where it cannot be mistaken for one of the rules.
 */
@Composable
internal fun JamRoomCard(jam: Jam, isRadioEnabled: Boolean, viewModel: JamViewModel) {
    val context = LocalContext.current
    val share = { shareJam(context, viewModel.shareUrl(jam.code), jam.code) }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            CodeRow(jam.code, onShare = share)
            MembersPanel(jam.members, onInvite = share)
            QueueMode(jam, onSetMode = viewModel::setMode)
            if (jam.isHost) {
                JamSettingsGroup(
                    isRadioEnabled = isRadioEnabled,
                    onRadioChange = viewModel::setJamRadioEnabled,
                    openToFriends = jam.openToFriends,
                    onOpenToFriendsChange = viewModel::setOpenToFriends
                )
            }
        }
    }
}

@Composable
private fun CodeRow(code: String, onShare: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.jam_join_code_overline),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = code,
                style = MaterialTheme.typography.joinCode,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        RoundAction(
            onClick = onShare,
            shape = CircleShape,
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = MaterialTheme.colorScheme.onSurface
        ) { Icon(Icons.Rounded.Share, stringResource(R.string.social_share_jam_link), Modifier.size(22.dp)) }
        RoundAction(
            onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(code, code))) } },
            shape = RoundedCornerShape(16.dp),
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary
        ) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.social_copy_join_code)) }
    }
}

@Composable
private fun RoundAction(
    onClick: () -> Unit,
    shape: Shape,
    container: Color,
    content: Color,
    icon: @Composable () -> Unit
) {
    Surface(onClick = onClick, shape = shape, color = container, contentColor = content, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { icon() }
    }
}

@Composable
private fun MembersPanel(members: List<String>, onInvite: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                members.take(MAX_FACES).forEach { CuteAvatar(seed = it, size = 40.dp, shape = PersonShape) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(R.plurals.jam_member_count, members.size, members.size),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = members.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(
                onClick = onInvite,
                shape = CircleShape,
                contentPadding = PaddingValues(start = 12.dp, end = 14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.height(40.dp)
            ) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.jam_invite),
                    style = MaterialTheme.typography.buttonSmall,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

/** The host chooses the rule; everyone else is told what it is. */
@Composable
private fun QueueMode(jam: Jam, onSetMode: (JamMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (jam.isHost) {
            ConnectedToggleGroup(
                options = listOf(JamMode.DEMOCRACY, JamMode.OPEN),
                selected = jam.mode,
                label = { mode ->
                    stringResource(if (mode == JamMode.DEMOCRACY) R.string.social_vote_add else R.string.social_anyone_adds)
                },
                onSelect = onSetMode,
                uncheckedContainer = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
        Text(
            text = if (jam.mode == JamMode.DEMOCRACY) {
                val needed = jam.approvalsNeeded.toInt()
                pluralStringResource(R.plurals.jam_votes_needed, needed, needed)
            } else {
                stringResource(R.string.jam_anyone_can_add)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

internal fun shareJam(context: Context, shareUrl: String?, code: String) {
    val shareText = if (shareUrl != null) {
        context.getString(R.string.jam_share_text_link, shareUrl)
    } else {
        context.getString(R.string.jam_share_text_code, code)
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, shareText)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.jam_share_chooser)))
}

private const val MAX_FACES = 3
