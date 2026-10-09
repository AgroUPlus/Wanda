package com.wander.android.ui.screens.home.customize

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.screens.home.HomeSectionStyle

/** The layouts any shelf can take, in the order they are offered. */
private val Layouts = listOf(
    HomeSectionStyle.HERO_CAROUSEL,
    HomeSectionStyle.TRACK_CAROUSEL,
    HomeSectionStyle.TRACK_LIST,
    HomeSectionStyle.LARGE_GRID,
    HomeSectionStyle.FEATURED_HERO,
    HomeSectionStyle.DISCOVER_MASONRY,
    HomeSectionStyle.TRACK_PAGER
)

/** Every layout as a tile that draws its own shape, so choosing one is seeing it. */
@Composable
internal fun ShelfStylePicker(
    selected: HomeSectionStyle,
    onSelect: (HomeSectionStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        items(Layouts, key = { it.name }) { style ->
            StyleTile(style, selected = style == selected, onClick = { onSelect(style) })
        }
    }
}

@Composable
private fun StyleTile(style: HomeSectionStyle, selected: Boolean, onClick: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        motion.fastEffectsSpec(),
        label = "tileColor"
    )
    val ink = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(if (selected) 28.dp else 16.dp),
        color = container,
        modifier = Modifier.width(88.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            StyleGlyph(style, ink)
            Text(style.label(), style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1)
        }
    }
}

/** A 56 x 40 sketch of a layout: blocks where the cards, rows or pages would be. */
@Composable
private fun StyleGlyph(style: HomeSectionStyle, ink: Color) {
    val block = ink.copy(alpha = 0.85f)
    val faint = ink.copy(alpha = 0.45f)
    Box(modifier = Modifier.size(width = 56.dp, height = 40.dp), contentAlignment = Alignment.Center) {
        when (style) {
            HomeSectionStyle.HERO_CAROUSEL -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Piece(9.dp, 24.dp, faint)
                Piece(24.dp, 38.dp, block)
                Piece(9.dp, 24.dp, faint)
            }

            HomeSectionStyle.TRACK_CAROUSEL -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Piece(14.dp, 14.dp, block)
                Piece(14.dp, 14.dp, block)
                Piece(14.dp, 14.dp, faint)
            }

            HomeSectionStyle.TRACK_LIST -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { Piece(48.dp, 8.dp, if (it == 2) faint else block) }
            }

            HomeSectionStyle.LARGE_GRID -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Piece(22.dp, 17.dp, block)
                        Piece(22.dp, 17.dp, faint)
                    }
                }
            }

            HomeSectionStyle.FEATURED_HERO -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Piece(28.dp, 38.dp, block)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Piece(16.dp, 17.dp, faint)
                    Piece(16.dp, 17.dp, faint)
                }
            }

            HomeSectionStyle.DISCOVER_MASONRY -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Piece(14.dp, 23.dp, block)
                    Piece(14.dp, 12.dp, faint)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Piece(14.dp, 12.dp, faint)
                    Piece(14.dp, 23.dp, block)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Piece(14.dp, 17.dp, block)
                    Piece(14.dp, 18.dp, faint)
                }
            }

            HomeSectionStyle.TRACK_PAGER -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { Piece(48.dp, 7.dp, block) }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Piece(5.dp, 5.dp, block)
                    Piece(5.dp, 5.dp, faint)
                    Piece(5.dp, 5.dp, faint)
                }
            }

            HomeSectionStyle.MIX_CAROUSEL -> Unit
        }
    }
}

@Composable
private fun Piece(width: Dp, height: Dp, color: Color) {
    Box(Modifier.size(width, height).background(color, RoundedCornerShape(3.dp)))
}

@Composable
internal fun HomeSectionStyle.label(): String = stringResource(
    when (this) {
        HomeSectionStyle.HERO_CAROUSEL -> R.string.home_style_carousel
        HomeSectionStyle.TRACK_CAROUSEL -> R.string.home_style_cards
        HomeSectionStyle.TRACK_LIST -> R.string.home_style_list
        HomeSectionStyle.LARGE_GRID -> R.string.home_style_grid
        HomeSectionStyle.FEATURED_HERO -> R.string.home_style_spotlight
        HomeSectionStyle.DISCOVER_MASONRY -> R.string.home_style_mosaic
        HomeSectionStyle.TRACK_PAGER -> R.string.home_style_pages
        HomeSectionStyle.MIX_CAROUSEL -> R.string.home_style_cards
    }
)
