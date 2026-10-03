package com.wander.android.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.repository.TopSong
import com.wander.android.data.sources.agro.StatsPeriod
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.HeroOverline
import com.wander.android.ui.components.HeroSheetOverlap
import com.wander.android.ui.components.HeroSheetShape
import com.wander.android.ui.components.riseBy
import com.wander.android.ui.theme.buttonSmall
import com.wander.android.ui.theme.heroTitle

/**
 * The top of the statistics screen: the one song the window was mostly about, full-bleed, with its
 * name on a sheet that rises over the foot of the cover rather than printed on top of it — text on
 * artwork is only legible for the artwork it was tuned against.
 *
 * [topSong] is null before anything has been played in the window, and then this is a plain
 * heading: an empty cover with "Top song" under it would be a promise the screen cannot keep.
 */
@Composable
internal fun StatsHero(
    topSong: TopSong?,
    period: StatsPeriod,
    onPeriod: (StatsPeriod) -> Unit,
    onBack: () -> Unit,
    topInset: PaddingValues
) {
    if (topSong == null) {
        Column {
            HeroControls(period, onPeriod, onBack, topInset, overArt = false)
            Text(
                text = stringResource(R.string.common_listening),
                style = MaterialTheme.typography.heroTitle,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)
            )
            Text(
                text = stringResource(R.string.stats_nothing_played_period_yet),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp)
            )
        }
        return
    }

    Column {
        Box(modifier = Modifier.fillMaxWidth().height(ArtHeight)) {
            Artwork(
                url = topSong.artworkUrl,
                contentDescription = topSong.title,
                sizeDp = ArtHeight,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().height(ArtHeight)
            )
            HeroControls(period, onPeriod, onBack, topInset, overArt = true)
        }
        Surface(
            shape = HeroSheetShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().riseBy(HeroSheetOverlap)
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
                HeroOverline(R.string.stats_top_song)
                Text(
                    text = topSong.title,
                    style = MaterialTheme.typography.heroTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (topSong.artist.isNotBlank()) {
                    Text(
                        text = topSong.artist,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroControls(
    period: StatsPeriod,
    onPeriod: (StatsPeriod) -> Unit,
    onBack: () -> Unit,
    topInset: PaddingValues,
    overArt: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(topInset)
            .padding(start = 8.dp, end = 16.dp, top = 8.dp)
    ) {
        FilledIconButton(
            onClick = onBack,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (overArt) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
        }
        PeriodPicker(period, onPeriod)
    }
}

/**
 * Which stretch of time the screen is about, as a menu: four periods in a row of toggles cost a
 * full row of the screen to say one word.
 */
@Composable
private fun PeriodPicker(period: StatsPeriod, onPeriod: (StatsPeriod) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Button(
            onClick = { open = true },
            shape = CircleShape,
            contentPadding = PaddingValues(start = 16.dp, end = 10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.height(40.dp)
        ) {
            Text(text = period.label, style = MaterialTheme.typography.buttonSmall)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            StatsPeriod.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    onClick = {
                        open = false
                        onPeriod(entry)
                    }
                )
            }
        }
    }
}

private val ArtHeight = 340.dp
