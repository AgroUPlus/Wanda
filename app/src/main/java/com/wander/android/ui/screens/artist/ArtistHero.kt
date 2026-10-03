package com.wander.android.ui.screens.artist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.ArtistMonogram
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.HeroActionButton
import com.wander.android.ui.components.HeroActionIconSize
import com.wander.android.ui.components.HeroActionRow
import com.wander.android.ui.components.HeroActionTint
import com.wander.android.ui.components.HeroOverline
import com.wander.android.ui.components.ImmersiveHero
import com.wander.android.ui.components.HeroPlayIconSize
import com.wander.android.ui.components.HeroPlayWeight
import com.wander.android.ui.components.HeroSecondaryRowHeight
import com.wander.android.ui.components.HeroTransportRowHeight
import com.wander.android.ui.theme.heroTitle

/**
 * The top of an artist page: their portrait edge to edge, their name over it, and the things you
 * can do with them.
 *
 * This used to be a rounded card with a 96 dp circular portrait beside two lines of text, laid out
 * to match the album page's header. It now opens like every detail page — see [ImmersiveHero] —
 * with the name on a sheet rising over the foot of the portrait.
 *
 * The portrait is the backend's when it publishes one, and a monogram when it does not. It is
 * deliberately never a cover off one of their records: that fallback is how a correct biography
 * ended up beside a stranger's photograph. Wanda has no artist photography and does not guess.
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
    onToggleFollow: () -> Unit = {},
    /** On the name alone — how the page's collapsing bar tracks it. */
    titleModifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ImmersiveHero(
            aspect = PortraitAspect,
            backdrop = {
                // A portrait or a letter, and nothing in between.
                //
                // This used to hand `imageUrl` straight to `Artwork`, which draws a music note for
                // null — fine for a missing album cover, wrong for a person. What made it a real
                // bug is what fed it: the fallback below `imageUrl` was a cover off any record the
                // page could not *disprove* was theirs, so a correct biography was being published
                // next to a stranger's face. That fallback is gone; this is what stands in for it.
                if (imageUrl.isNullOrBlank()) {
                    ArtistMonogram(name = name)
                } else {
                    Artwork(
                        url = imageUrl,
                        contentDescription = name,
                        sizeDp = PortraitDecodeSize,
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth().aspectRatio(PortraitAspect)
                    )
                }
            }
        ) {
            HeroOverline(R.string.hero_overline_artist)
            Text(
                text = name,
                style = MaterialTheme.typography.heroTitle,
                maxLines = ArtistNameMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = titleModifier
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // Two rows rather than one: transport (radio/play/shuffle) is what you reach for first,
        // and shared its row with share/follow only because there had never been anywhere else to
        // put them. See [HeroActionButton] for the weight mechanism the buttons share.
        HeroActionRow(height = HeroTransportRowHeight, modifier = Modifier.padding(top = 18.dp)) {
            HeroActionButton(
                onClick = onRadio,
                contentDescription = stringResource(R.string.artist_start_radio),
                icon = Icons.Rounded.Podcasts,
                baseWeight = 1f,
                iconSize = HeroActionIconSize,
                rowHeight = HeroTransportRowHeight,
                tint = HeroActionTint.TERTIARY_CONTAINER
            )
            HeroActionButton(
                onClick = onPlay,
                contentDescription = stringResource(R.string.action_play),
                icon = Icons.Rounded.PlayArrow,
                baseWeight = HeroPlayWeight,
                iconSize = HeroPlayIconSize,
                rowHeight = HeroTransportRowHeight,
                tint = HeroActionTint.PRIMARY_SOLID
            )
            HeroActionButton(
                onClick = onShuffle,
                contentDescription = stringResource(R.string.action_shuffle),
                icon = Icons.Rounded.Shuffle,
                baseWeight = 1f,
                iconSize = HeroActionIconSize,
                rowHeight = HeroTransportRowHeight,
                tint = HeroActionTint.SECONDARY_CONTAINER
            )
        }

        if (onShare != null || isFollowing != null) {
            HeroActionRow(
                height = HeroSecondaryRowHeight,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
            ) {
                onShare?.let { share ->
                    HeroActionButton(
                        onClick = share,
                        contentDescription = stringResource(R.string.action_share),
                        icon = Icons.Rounded.Share,
                        baseWeight = 1f,
                        iconSize = HeroActionIconSize,
                        rowHeight = HeroSecondaryRowHeight,
                        tint = HeroActionTint.TERTIARY_CONTAINER
                    )
                }
                if (isFollowing != null) {
                    HeroActionButton(
                        onClick = onToggleFollow,
                        contentDescription = if (isFollowing) {
                            stringResource(R.string.artist_stop_following, name)
                        } else {
                            stringResource(R.string.artist_follow_for_new_releases, name)
                        },
                        icon = if (isFollowing) {
                            Icons.Rounded.NotificationsActive
                        } else {
                            Icons.Rounded.NotificationAdd
                        },
                        baseWeight = 1f,
                        iconSize = HeroActionIconSize,
                        rowHeight = HeroSecondaryRowHeight,
                        tint = if (isFollowing) HeroActionTint.PRIMARY_CONTAINER else HeroActionTint.NEUTRAL
                    )
                }
            }
        }
    }
}

/** A shade taller than the shared default: a head-and-shoulders shot needs the extra height. */
internal const val PortraitAspect = 0.86f

/** Shared with the page's collapsing bar, which must wrap the name exactly as the hero does. */
internal const val ArtistNameMaxLines = 2

/** Constant, not measured — see the note in [ImmersiveHero]'s own backdrop. */
private val PortraitDecodeSize = 480.dp
