package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * The six reactions on offer.
 *
 * A fixed short row rather than the system emoji keyboard: this is a reaction, and picking one
 * should take a tap, not a search. They are the ones that mean something about a song someone
 * gave you — loved it, it goes hard, it made me cry, it made me laugh, I already know it, no.
 */
private val REACTIONS = listOf("❤️", "🔥", "😭", "😂", "👀", "🙃")

/**
 * What you can do with one message, on long press.
 *
 * Both of these used to be permanent controls inside every incoming bubble — a reaction affordance
 * and an archive button, stacked under the track card, on every message in the thread. That is two
 * rows of chrome per message for two things people do occasionally, and it made a conversation
 * read as a list of forms. Long press is where the rest of the app keeps actions on a row, so it
 * is where these went.
 *
 * "Remove" and not "delete": the row is hidden for this account only and the other side keeps
 * their copy. Saying delete would promise something the server does not do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DropActionsSheet(
    /** Null when the message is one this account sent — you cannot react to your own. */
    currentReaction: String?,
    canReact: Boolean,
    onReact: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            // Reacting is the recipient's reply, so only an incoming message offers it. Letting
            // the sender react to their own would be a way to fake a response.
            if (canReact) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    REACTIONS.forEach { emoji ->
                        val chosen = emoji == currentReaction
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onReact(emoji) }
                                .then(
                                    if (chosen) {
                                        Modifier.background(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
                HorizontalDivider()
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Remove for me",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
