package com.wander.android.ui.screens.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.ImmersiveHero
import com.wander.android.ui.components.ShapedActionButton
import com.wander.android.ui.components.ShapedPlayButton

/**
 * The top of an artist page: their portrait edge to edge, their name over it, and the things you
 * can do with them.
 *
 * This used to be a rounded card with a 96 dp circular portrait beside two lines of text, laid out
 * to match the album page's header. The two pages *should* differ here, and now do — see
 * [ImmersiveHero], which is the shape both of them settled on.
 *
 * The portrait is the backend's when it publishes one, otherwise a cover off one of their records.
 * Wanda has no artist photography and does not invent any.
 */
@Composable
internal fun ArtistHero(
    name: String,
    subtitle: String,
    imageUrl: String?,
    onPlay: () -> Unit,
    onRadio: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null until a track has loaded with a backend artist id — see `ArtistViewModel`. */
    onShare: (() -> Unit)? = null,
    /**
     * Whether this account follows the artist, or null while that is still unknown.
     *
     * Null rather than false: drawing "Follow" and flipping it to "Following" a moment later reads
     * as the tap having failed, so the control waits until there is an answer to show.
     */
    isFollowing: Boolean? = null,
    onToggleFollow: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ImmersiveHero(
            imageUrl = imageUrl,
            contentDescription = name,
            aspect = PortraitAspect,
            scrimHeight = 108.dp,
            horizontalPadding = 24.dp
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 4.dp)
        ) {
            ShapedActionButton(
                onClick = onRadio,
                contentDescription = "Start radio",
                icon = Icons.Rounded.Podcasts
            )
            // Centre, and larger than its neighbours: the one control the page exists for. It sat
            // at the trailing edge while the header was a card, which is the right answer for a
            // left-aligned row and the wrong one for a centred portrait.
            ShapedPlayButton(
                onClick = onPlay,
                contentDescription = "Play",
                icon = Icons.Rounded.PlayArrow
            )
            ShapedActionButton(
                onClick = onShuffle,
                contentDescription = "Shuffle",
                icon = Icons.Rounded.Shuffle
            )
            onShare?.let { share ->
                ShapedActionButton(
                    onClick = share,
                    contentDescription = "Share",
                    icon = Icons.Rounded.Share
                )
            }
            if (isFollowing != null) {
                ShapedActionButton(
                    onClick = onToggleFollow,
                    contentDescription = if (isFollowing) {
                        "Stop following ${'$'}name"
                    } else {
                        "Follow ${'$'}name for new releases"
                    },
                    icon = if (isFollowing) {
                        Icons.Rounded.NotificationsActive
                    } else {
                        Icons.Rounded.NotificationAdd
                    }
                )
            }
        }
    }
}

/** A shade taller than the shared default: a head-and-shoulders shot needs the extra height. */
internal const val PortraitAspect = 0.86f
