package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroTrendsetter
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.PersonShape
import com.wander.android.ui.components.SunnyShape
import com.wander.android.ui.theme.trendBadge

/**
 * Whoever in the circle got to its favourite songs first, with the count on a sunny badge and the
 * songs themselves as chips. The recap names those songs but sends no artwork for them, so the
 * chips carry text only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrendsetterCard(trendsetter: AgroTrendsetter) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CuteAvatar(seed = trendsetter.username, size = 56.dp, shape = PersonShape)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(colors.tertiaryContainer, CircleShape)
                            .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = colors.onTertiaryContainer, modifier = Modifier.size(14.dp))
                        Text(
                            text = stringResource(R.string.social_trendsetter),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = colors.onTertiaryContainer
                        )
                    }
                    Text(
                        text = "@" + trendsetter.username,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(76.dp).background(colors.tertiary, SunnyShape)
                ) {
                    Text(trendsetter.firsts.toString(), style = MaterialTheme.typography.trendBadge, color = colors.onTertiary)
                }
            }
            Text(
                text = stringResource(R.string.social_first_discover_circle_s_top, trendsetter.firsts),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 1.4.em,
                    lineBreak = LineBreak.Paragraph
                )
            )
            if (trendsetter.examples.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trendsetter.examples.forEach { name ->
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .height(36.dp)
                                .background(colors.surfaceContainerHigh, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
