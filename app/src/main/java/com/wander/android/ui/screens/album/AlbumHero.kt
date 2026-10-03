package com.wander.android.ui.screens.album

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.HeroActionButton
import com.wander.android.ui.components.HeroActionIconSize
import com.wander.android.ui.components.HeroActionRow
import com.wander.android.ui.components.HeroActionTint
import com.wander.android.ui.components.HeroOverline
import com.wander.android.ui.components.HeroPlayIconSize
import com.wander.android.ui.components.HeroPlayWeight
import com.wander.android.ui.components.HeroSecondaryRowHeight
import com.wander.android.ui.components.HeroTransportRowHeight
import com.wander.android.ui.components.ImmersiveHero
import com.wander.android.ui.theme.heroTitle

/**
 * The top of an album or playlist page: the cover, at the size a cover is worth looking at.
 *
 * It began as a 96 dp thumbnail in a card with the title beside it — a list row scaled up and
 * given buttons — then became a centred 260 dp square. It is the full width of the window now,
 * running to the top of it, with the title on a sheet rising over the foot of the artwork. See
 * [ImmersiveHero] for why the caption needs no scrim and why the artist page and the statistics
 * screen open the same way.
 *
 * Playlists use this too. A playlist's cover is a cover, and giving the two pages different
 * headers would say they were different kinds of thing when the only real difference is who chose
 * the running order.
 */
@Composable
internal fun AlbumHero(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null when this record's backend cannot publish a link for it. */
    onShare: (() -> Unit)? = null,
    /** Playlists only: already shared through Agro, so the button opens what is shared, not a new share. */
    isShared: Boolean = false,
    /** Playlists only: copy a backend's playlist into a Wanda one. Null when it already is one. */
    onConvert: (() -> Unit)? = null,
    /** Playlists only: save the tracks as an `.m3u8` file. */
    onDownload: (() -> Unit)? = null,
    /** On the title alone — how the page's collapsing bar tracks it. */
    titleModifier: Modifier = Modifier,
    /** What kind of record this is, shown over the title. */
    @StringRes overline: Int = R.string.hero_overline_album
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ImmersiveHero(
            imageUrl = artworkUrl,
            contentDescription = title,
            aspect = CoverAspect
        ) {
            HeroOverline(overline)
            Text(
                text = title,
                style = MaterialTheme.typography.heroTitle,
                maxLines = HeroTitleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = titleModifier
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // The artist page's two rows, for the same reasons — see [HeroActionButton]. Transport
        // first; sharing and the playlist-only actions below it, only when there are any.
        HeroActionRow(height = HeroTransportRowHeight, modifier = Modifier.padding(top = 18.dp)) {
            HeroActionButton(
                onClick = onShuffle,
                contentDescription = stringResource(R.string.action_shuffle),
                icon = Icons.Rounded.Shuffle,
                baseWeight = 1f,
                iconSize = HeroActionIconSize,
                rowHeight = HeroTransportRowHeight,
                tint = HeroActionTint.SECONDARY_CONTAINER
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
        }

        if (onShare != null || onConvert != null || onDownload != null) {
            HeroActionRow(
                height = HeroSecondaryRowHeight,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
            ) {
                onShare?.let { share ->
                    HeroActionButton(
                        onClick = share,
                        contentDescription = stringResource(if (isShared) R.string.playlist_shared_on_agro else R.string.action_share),
                        icon = if (isShared) Icons.Rounded.CloudDone else Icons.Rounded.Share,
                        baseWeight = 1f,
                        iconSize = HeroActionIconSize,
                        rowHeight = HeroSecondaryRowHeight,
                        // Switched on reads like the artist page's Following: a container, never
                        // solid, so it does not compete with Play.
                        tint = if (isShared) HeroActionTint.PRIMARY_CONTAINER else HeroActionTint.TERTIARY_CONTAINER
                    )
                }
                onConvert?.let { convert ->
                    HeroActionButton(
                        onClick = convert,
                        contentDescription = stringResource(R.string.playlist_convert_action),
                        icon = Icons.Rounded.LibraryAdd,
                        baseWeight = 1f,
                        iconSize = HeroActionIconSize,
                        rowHeight = HeroSecondaryRowHeight,
                        tint = HeroActionTint.SECONDARY_CONTAINER
                    )
                }
                onDownload?.let { download ->
                    HeroActionButton(
                        onClick = download,
                        contentDescription = stringResource(R.string.playlist_download),
                        icon = Icons.Rounded.Download,
                        baseWeight = 1f,
                        iconSize = HeroActionIconSize,
                        rowHeight = HeroSecondaryRowHeight,
                        tint = HeroActionTint.NEUTRAL
                    )
                }
            }
        }
    }
}

/** Square, like the cover: the title sheet sits below it and only overlaps its foot. */
internal const val CoverAspect = 1f

/** Shared with the page's collapsing bar, which must wrap the title exactly as the hero does. */
internal const val HeroTitleMaxLines = 3
