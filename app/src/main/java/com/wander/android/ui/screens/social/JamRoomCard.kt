package com.wander.android.ui.screens.social

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.Jam
import com.wander.android.data.sources.agro.JamMode
import com.wander.android.ui.components.AvatarGroup

/**
 * The join code, who is here, and the room rules the creator can configure.
 */
@Composable
internal fun JamRoomCard(jam: Jam, isRadioEnabled: Boolean, viewModel: JamViewModel) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.common_join_code), style = MaterialTheme.typography.labelMedium)
                    Text(jam.code, style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = {
                    val shareUrl = viewModel.shareUrl(jam.code)
                    val shareText = if (shareUrl != null) {
                        "Join my music Jam on Wanda! $shareUrl"
                    } else {
                        "Join my music Jam on Wanda! Use code: ${jam.code}"
                    }
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Jam Link"))
                }) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.social_share_jam_link))
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(jam.code)) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.social_copy_join_code))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                AvatarGroup(
                    usernames = jam.members,
                    size = 34.dp,
                    overlap = 10.dp,
                    maxDisplay = 6
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${jam.members.size} ${if (jam.members.size == 1) "member" else "members"} in room",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = jam.members.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (jam.isHost) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton(
                        checked = jam.mode == JamMode.DEMOCRACY,
                        onCheckedChange = { viewModel.setMode(JamMode.DEMOCRACY) }
                    ) { Text(stringResource(R.string.social_vote_add)) }
                    ToggleButton(
                        checked = jam.mode == JamMode.OPEN,
                        onCheckedChange = { viewModel.setMode(JamMode.OPEN) }
                    ) { Text(stringResource(R.string.social_anyone_adds)) }
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = if (jam.mode == JamMode.DEMOCRACY) {
                    "Suggestions need ${jam.approvalsNeeded} other " +
                        (if (jam.approvalsNeeded == 1L) "person" else "people") + " to agree."
                } else {
                    "Anyone can add straight to the queue."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (jam.isHost) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.social_jam_radio), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.social_auto_blends_room_s_music),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isRadioEnabled,
                        onCheckedChange = viewModel::setJamRadioEnabled
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.social_open_friends), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (jam.openToFriends) {
                                "Your friends can see this jam and join without the code."
                            } else {
                                "Only people you give the code to can join."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = jam.openToFriends,
                        onCheckedChange = viewModel::setOpenToFriends
                    )
                }
            }

            OutlinedButton(
                onClick = viewModel::leave,
                modifier = Modifier.padding(top = 16.dp),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(if (jam.isHost) "End jam" else "Leave")
            }
        }
    }
}
