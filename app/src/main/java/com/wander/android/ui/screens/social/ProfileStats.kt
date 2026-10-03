package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.SunnyShape
import com.wander.android.ui.theme.displayStat

/**
 * The two numbers that summarise somebody's listening, each as a square tile big enough that the
 * number is the first thing read. Plays is a plain rounded tile; hours is the sunny shape, so the
 * pair reads as two different facts rather than one tile drawn twice.
 */
@Composable
internal fun StatTiles(plays: Long, hours: Long) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(32.dp))
                .padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp)
            )
            StatFigure(plays.toString(), stringResource(R.string.social_plays), MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.tertiary, SunnyShape)
        ) {
            StatFigure(
                hours.toString(),
                stringResource(R.string.social_hours),
                MaterialTheme.colorScheme.onTertiary,
                centred = true
            )
        }
    }
}

@Composable
private fun StatFigure(value: String, label: String, color: Color, centred: Boolean = false) {
    Column(horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start) {
        Text(text = value, style = MaterialTheme.typography.displayStat, color = color, maxLines = 1)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = color,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
