package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.PersonShape
import com.wander.android.ui.theme.extraColors
import com.wander.android.ui.theme.sectionTitle

/** "Listening now", with how many friends that is right now. */
@Composable
internal fun ListeningNowHeader(liveCount: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp)
    ) {
        Text(text = stringResource(R.string.social_listening_now), style = MaterialTheme.typography.sectionTitle)
        LiveCountChip(liveCount)
    }
}

@Composable
private fun LiveCountChip(count: Int) {
        Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.extraColors.liveContainer, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(6.dp).background(MaterialTheme.extraColors.liveDot, CircleShape))
        Text(
            text = pluralStringResource(R.plurals.social_live_count, count, count),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.extraColors.onLiveContainer
        )
    }
}

/**
 * Who is playing something right now, as album art you can see from across the room.
 *
 * A multi-browse carousel rather than a row of equal cards: the friend under your thumb gets the
 * width to show a cover and a title, and the rest shrink towards the edge to say there are more
 * without needing a scroll hint.
 *
 * Only ever built for friends who *are* playing something. A friend with nothing showing is either
 * not listening or has that switched off, and neither is a card worth a slot here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListeningNowCarousel(
    playing: List<Pair<AgroProfile, AgroFriendNowPlaying>>,
    onOpenProfile: (String) -> Unit
) {
    val state = rememberCarouselState { playing.size }
    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = LargeCardWidth,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().height(CardHeight)
    ) { index ->
        val (profile, now) = playing[index]
        PresenceCard(profile, now, onOpen = { onOpenProfile(profile.username) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselItemScope.PresenceCard(
    profile: AgroProfile,
    now: AgroFriendNowPlaying,
    onOpen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .maskClip(RoundedCornerShape(28.dp))
            .clickable(onClick = onOpen)
    ) {
        Artwork(
            url = now.artworkUrl,
            contentDescription = null,
            sizeDp = LargeCardWidth,
            // The mask above already shapes the card; a second clip at another radius would show
            // as a seam while the carousel resizes it.
            shape = RectangleShape,
            modifier = Modifier.fillMaxSize()
        )
        // The panel needs a card's worth of width to say anything, so it fades out as the item
        // shrinks to a peek instead of being squeezed into an ellipsis.
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(8.dp)
                .graphicsLayer {
                    val info = carouselItemDrawInfo
                    val span = (info.maxSize - info.minSize).coerceAtLeast(1f)
                    alpha = ((info.size - info.minSize) / span).coerceIn(0f, 1f)
                }
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CuteAvatar(seed = profile.username, avatarUrl = profile.avatarUrl, size = 24.dp, shape = PersonShape)
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // A sealed session travelled through the server as ciphertext; the lock says so
                    // whether or not it opened, the same badge the drops inbox uses for that fact.
                    if (now.encryptedPresence != null) EncryptedThreadLock()
                    Text(
                        text = now.trackTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = now.artistName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val LargeCardWidth = 200.dp
private val CardHeight = 228.dp
