package com.wander.android.ui.screens.artist

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.ArtistMonogram
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.ImmersiveHero

/**
 * The top of an artist page: their portrait edge to edge, their name over it, and the things you
 * can do with them.
 *
 * This used to be a rounded card with a 96 dp circular portrait beside two lines of text, laid out
 * to match the album page's header. The two pages *should* differ here, and now do — see
 * [ImmersiveHero], which is the shape both of them settled on.
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
            scrimHeight = 108.dp,
            horizontalPadding = 24.dp,
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
            Text(
                text = name,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = titleModifier
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

        // Two rows rather than one: transport (radio/play/shuffle) is what you reach for first,
        // and shared its row with share/follow only because there had never been anywhere else to
        // put them.
        //
        // Both rows now span exactly what the song list below them does — the same 16 dp inset
        // `groupedListItem` uses — and each button is `Modifier.weight(...)` rather than a fixed
        // size, the identical mechanism `ActionButtonGroup` already uses for every context menu in
        // the app: a button's *own* press state animates *its own* weight up, and the Row's normal
        // weighted layout does the "neighbours give way" part on its own — nothing here tracks
        // sibling buttons the way an earlier, scale-based version of this did.
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
            modifier = Modifier
                .fillMaxWidth()
                .height(HeroRowHeight)
                .padding(horizontal = RowInset, vertical = 0.dp)
                .padding(top = 18.dp)
        ) {
            ArtistActionButton(
                onClick = onRadio,
                contentDescription = stringResource(R.string.artist_start_radio),
                icon = Icons.Rounded.Podcasts,
                baseWeight = 1f,
                iconSize = ActionIconSize,
                rowHeight = HeroRowHeight,
                tint = ActionTint.TERTIARY_CONTAINER
            )
            // Wider than its neighbours, not just present among them: the one control the page
            // exists for still reads as the biggest thing in the row, the same way `WideWeight`
            // gives a context menu's primary action more room than the buttons beside it.
            ArtistActionButton(
                onClick = onPlay,
                contentDescription = stringResource(R.string.action_play),
                icon = Icons.Rounded.PlayArrow,
                baseWeight = HeroWeight,
                iconSize = HeroIconSize,
                rowHeight = HeroRowHeight,
                tint = ActionTint.PRIMARY_SOLID
            )
            ArtistActionButton(
                onClick = onShuffle,
                contentDescription = stringResource(R.string.action_shuffle),
                icon = Icons.Rounded.Shuffle,
                baseWeight = 1f,
                iconSize = ActionIconSize,
                rowHeight = HeroRowHeight,
                tint = ActionTint.SECONDARY_CONTAINER
            )
        }

        if (onShare != null || isFollowing != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonGap),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ActionRowHeight)
                    .padding(horizontal = RowInset)
                    .padding(top = 14.dp, bottom = 4.dp)
            ) {
                onShare?.let { share ->
                    ArtistActionButton(
                        onClick = share,
                        contentDescription = stringResource(R.string.action_share),
                        icon = Icons.Rounded.Share,
                        baseWeight = 1f,
                        iconSize = ActionIconSize,
                        rowHeight = ActionRowHeight,
                        tint = ActionTint.TERTIARY_CONTAINER
                    )
                }
                if (isFollowing != null) {
                    ArtistActionButton(
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
                        iconSize = ActionIconSize,
                        rowHeight = ActionRowHeight,
                        tint = if (isFollowing) ActionTint.PRIMARY_CONTAINER else ActionTint.NEUTRAL
                    )
                }
            }
        }
    }
}

/**
 * Which of the theme's own tonal pairs a button draws from — see [ArtistActionButton]'s [tint].
 *
 * Every one of these already exists in `WandaLightScheme`/`WandaDarkScheme`
 * ([com.wander.android.ui.theme.WandaLightScheme]): this is a palette choice, not a new colour.
 */
private enum class ActionTint { PRIMARY_SOLID, PRIMARY_CONTAINER, SECONDARY_CONTAINER, TERTIARY_CONTAINER, NEUTRAL }

@Composable
private fun ActionTint.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        // The one solid, non-container colour — the hero play button, the single control the
        // page exists for. Everything else stays on a tonal container, including a toggle that's
        // switched on: a solid `primary` pill there would fight the hero for the same emphasis.
        ActionTint.PRIMARY_SOLID -> scheme.primary to scheme.onPrimary
        ActionTint.PRIMARY_CONTAINER -> scheme.primaryContainer to scheme.onPrimaryContainer
        ActionTint.SECONDARY_CONTAINER -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ActionTint.TERTIARY_CONTAINER -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ActionTint.NEUTRAL -> scheme.surfaceContainerHighest to scheme.onSurface
    }
}

/**
 * One button in either row — see the doc above the rows themselves for the weight mechanism.
 *
 * The shape mechanism is `ActionButtonGroup`'s own, not a polygon morph: a full stadium
 * ([rowHeight] / 2) at rest that tightens to [ActionPressedCorner] under a finger, exactly the
 * corner-radius animation every context menu in the app already uses — the user asked for these
 * two to read as the same control, not two different ones that happen to share a press gesture.
 *
 * [tint] picks the button's colour from the app's own primary/secondary/tertiary palette rather
 * than leaving every non-hero button on one uniform neutral tone — the row otherwise reads as one
 * grey pill repeated four times with a single accent one buried in the middle of it.
 */
@Composable
private fun RowScope.ArtistActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    baseWeight: Float,
    iconSize: Dp,
    rowHeight: Dp,
    tint: ActionTint = ActionTint.NEUTRAL
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The fast spatial spec, not the default: this sits directly under a finger, and anything
    // leisurely reads as the tap not having registered — same reasoning `ActionButtonGroup`
    // documents for its own press-corner animation.
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val corner by animateDpAsState(
        targetValue = if (pressed) ActionPressedCorner else rowHeight / 2,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "artistActionCorner"
    )
    val weight by animateFloatAsState(
        targetValue = if (pressed) baseWeight * PressedGrowth else baseWeight,
        animationSpec = spatial,
        label = "artistActionWeight"
    )
    val (containerColor, contentColor) = tint.colors()

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
        }
    }
}

/** A shade taller than the shared default: a head-and-shoulders shot needs the extra height. */
internal const val PortraitAspect = 0.86f

/** Constant, not measured — see the note in [ImmersiveHero]'s own backdrop. */
private val PortraitDecodeSize = 480.dp

/**
 * Matches `groupedListItem`'s own default inset — the song list below reads as the same width.
 * Shared with [ArtistSkeleton], which mirrors this whole layout so nothing resizes when the real
 * page lands.
 */
internal val RowInset = 16.dp
internal val ButtonGap = 10.dp

internal val HeroRowHeight = 104.dp
internal val ActionRowHeight = 80.dp
private val HeroIconSize = 32.dp
private val ActionIconSize = 26.dp

/** The play button's share of the top row before any press — see [ArtistActionButton]. */
internal const val HeroWeight = 1.6f

/** How much a pressed button takes from its row neighbours — the same figure `ActionButtonGroup` uses. */
private const val PressedGrowth = 1.35f

/** The pressed corner radius — the same figure `ActionButtonGroup`'s own `PressedCorner` uses. */
private val ActionPressedCorner = 14.dp
