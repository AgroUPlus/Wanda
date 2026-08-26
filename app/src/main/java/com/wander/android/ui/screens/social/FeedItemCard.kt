package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroFeedItem
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.scrollingTitle

/**
 * One thing a friend has been doing lately.
 *
 * Lifted out of `CircleScreen`, which had grown to nearly twice the file-size cap, so the Friends
 * tab can show the same feed rather than owning a second copy of it that drifts.
 *
 * Leads with the friend's avatar rather than only an event badge. The server writes the summary as
 * a whole sentence starting with the username, which reads fine in a list of one person's events
 * and reads as a wall of near-identical text in a mixed feed — a face is what makes it scannable.
 * The badge rides on the corner of the avatar so it still says *what kind* of thing happened
 * without taking a column of its own.
 */
@Composable
internal fun FeedItemCard(
    item: AgroFeedItem,
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        onClick = { onOpenProfile(item.username) },
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            val (badgeIcon, badgeColor) = when (item.kind) {
                "ON_REPEAT" -> Icons.Rounded.LocalFireDepartment to MaterialTheme.colorScheme.error
                "NEW_FAVOURITE" -> Icons.Rounded.Favorite to MaterialTheme.colorScheme.tertiary
                else -> Icons.Rounded.Stars to MaterialTheme.colorScheme.primary
            }

            Box {
                CuteAvatar(seed = item.username, size = 40.dp)
                Surface(
                    color = badgeColor,
                    contentColor = MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                    // Nudged off the avatar's edge so the badge reads as attached to it rather
                    // than as a second, unrelated dot floating beside it.
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(badgeIcon, contentDescription = null, modifier = Modifier.size(11.dp))
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (!item.title.isNullOrBlank()) {
                    Text(
                        text = "${item.title} · ${item.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.scrollingTitle()
                    )
                }
            }
        }
    }
}
