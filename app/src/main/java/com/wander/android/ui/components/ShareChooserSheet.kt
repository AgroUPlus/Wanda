package com.wander.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * "Share" asks *how* rather than making the caller offer two rows that mean the same verb.
 *
 * There used to be "Share a link" and "Send to a friend" side by side, which is a menu asking the
 * user to know Wanda's internal distinction between a public URL and a drop before they have
 * decided who they are sharing with. One button, then the question.
 *
 * [onSendToFriend] is null when there is nobody to send to. The row is dropped rather than
 * disabled: an option that can only ever answer "you have no friends" is not worth showing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareChooserSheet(
    subject: String,
    onShareLink: () -> Unit,
    onSendToFriend: (() -> Unit)?,
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
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Share “$subject”",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            onSendToFriend?.let { send ->
                ListItem(
                    headlineContent = { Text("Send to a friend") },
                    supportingContent = { Text("Lands in their messages, with a note if you like") },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = send)
                )
            }

            ListItem(
                headlineContent = { Text("Share a link") },
                supportingContent = { Text("A public link anyone can open") },
                leadingContent = { Icon(Icons.Rounded.Link, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onShareLink)
            )
        }
    }
}
