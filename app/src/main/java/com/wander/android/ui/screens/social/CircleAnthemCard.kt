package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroAnthem
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.EmblemShape
import com.wander.android.ui.components.PersonShape
import com.wander.android.ui.theme.extraColors
import com.wander.android.ui.theme.heroTitleSmall

/**
 * The song the whole circle played most, as the screen's hero, with who played it how much.
 *
 * The recap carries no artwork for the anthem, so there is no cover slot: an empty art box would
 * promise a picture the server never sends.
 */
@Composable
internal fun AnthemHeroCard(anthem: AgroAnthem) {
    val colors = MaterialTheme.colorScheme
    val extra = MaterialTheme.extraColors
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = colors.primary,
        contentColor = colors.onPrimary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 60.dp, y = (-60).dp)
                    .size(200.dp)
                    .clip(EmblemShape)
                    .background(extra.primaryShapeAccent)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnthemChip()
                    Text(
                        text = stringResource(R.string.social_plays_2, anthem.plays),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Column {
                    Text(
                        text = anthem.title,
                        style = MaterialTheme.typography.heroTitleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = anthem.artist,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        color = extra.onPrimaryVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (anthem.byMember.isNotEmpty()) TopListeners(anthem)
            }
        }
    }
}

@Composable
private fun AnthemChip() {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(colors.onPrimary, CircleShape)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.social_circle_anthem),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = colors.primary
        )
    }
}

/** Each listener's share of the anthem, scaled against whoever played it most. */
@Composable
private fun TopListeners(anthem: AgroAnthem) {
    val extra = MaterialTheme.extraColors
    val most = anthem.byMember.maxOf { it.value }.coerceAtLeast(1L)
    Surface(shape = RoundedCornerShape(20.dp), color = extra.primarySoftPanel, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.social_top_listeners_room),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                color = extra.onPrimaryVariant
            )
            anthem.byMember.forEach { member ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CuteAvatar(seed = member.name, size = 28.dp, shape = PersonShape)
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(52.dp)
                    )
                    Box(modifier = Modifier.weight(1f).height(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((member.value.toFloat() / most).coerceIn(0.02f, 1f))
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(4.dp))
                        )
                    }
                    Text(
                        text = member.value.toString(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
                    )
                }
            }
        }
    }
}
