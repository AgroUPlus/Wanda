package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.R
import com.wander.android.data.sources.agro.Jam
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.EmblemShape
import com.wander.android.ui.theme.buttonLarge
import com.wander.android.ui.theme.extraColors
import com.wander.android.ui.theme.heroTitle

/**
 * The one big coloured thing on the Friends tab, because a jam is the one thing on it you do *with*
 * people rather than look at.
 *
 * It replaces a pair of half-width tiles — Jam and Activity — that were the same size and weight,
 * which told nobody which of them mattered. Activity is a list row further down now; this keeps the
 * colour to itself.
 *
 * The faces are the jam's members while you are in one, and otherwise a few of your friends: the
 * people a new jam would be for.
 */
@Composable
internal fun JamCard(
    jam: Jam?,
    people: List<String>,
    onOpenJam: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = MaterialTheme.colorScheme.primary
    val content = MaterialTheme.colorScheme.onPrimary

    Surface(
        onClick = onOpenJam,
        shape = RoundedCornerShape(32.dp),
        color = container,
        contentColor = content,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
    ) {
        Box {
            // Decoration only: the card's own colour pushed a step towards its ink, in the shape the
            // app uses for places rather than people.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 50.dp, y = (-60).dp)
                    .size(200.dp)
                    .clip(EmblemShape)
                    .background(MaterialTheme.extraColors.primaryShapeAccent)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(52.dp).background(content, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = container,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column {
                    Text(
                        text = jam?.let { stringResource(R.string.social_jam_with_code, it.code) }
                            ?: stringResource(R.string.social_start_jam),
                        style = MaterialTheme.typography.heroTitle
                    )
                    Text(
                        text = stringResource(R.string.social_jam_tagline),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.extraColors.onPrimaryVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StackedFaces(people = people, ring = container)
                    Button(
                        onClick = onOpenJam,
                        colors = ButtonDefaults.buttonColors(
                            // The card's own pair, inverted: the only roles guaranteed to contrast.
                            containerColor = content,
                            contentColor = container
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        shape = CircleShape,
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            text = stringResource(if (jam == null) R.string.social_jam_start else R.string.social_jam_open),
                            style = MaterialTheme.typography.buttonLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Up to three faces, each tucked under the last and ringed in the card's colour to separate them. */
@Composable
private fun StackedFaces(people: List<String>, ring: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
        people.take(MAX_FACES).forEach { username ->
            CuteAvatar(
                seed = username,
                size = 30.dp,
                modifier = Modifier.border(3.dp, ring, CircleShape).padding(3.dp)
            )
        }
    }
}

private const val MAX_FACES = 3
