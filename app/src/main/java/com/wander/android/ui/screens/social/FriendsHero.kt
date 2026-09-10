package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.ui.components.AvatarGroup
import com.wander.android.ui.components.ImmersiveHero
import com.wander.android.ui.components.avatarGradient

/**
 * The top of the Friends tab, on the same shape the record and artist pages open on.
 *
 * Those pages lead with the one picture they are about. This one has no such picture — the tab is
 * about a group of people, and Wanda hosts no uploads to make a banner out of. What it does have
 * is everyone's colours: each avatar is drawn from a deterministic pair (see `avatarGradient`), and
 * a band of those pairs side by side is a picture of *this* roster and no other. Add someone and
 * the band changes; it is the group, drawn.
 *
 * The faces themselves go in the caption rather than the band. Avatars at the size that would fill
 * a hero are the blur the profile page's comment warns about, whereas a row of them at their proper
 * size, over the part of the fade that has already reached `surface`, stays legible and says who
 * these colours belong to.
 */
@Composable
internal fun FriendsHero(
    friends: List<AgroProfile>,
    listeningNow: Int,
    modifier: Modifier = Modifier
) {
    val stripes = remember(friends) {
        friends.take(MaxStripes).map { avatarGradient(it.username) }
    }
    val fallback = MaterialTheme.colorScheme.primaryContainer
    val fallbackEnd = MaterialTheme.colorScheme.surfaceContainerHighest

    ImmersiveHero(
        modifier = modifier,
        aspect = FriendsAspect,
        scrimHeight = 12.dp,
        backdrop = {
            if (stripes.isEmpty()) {
                // Nobody yet. The theme's own colours rather than a grey box — this is the state
                // most people see first, and it should still look like the top of a page.
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(fallback, fallbackEnd)))
                )
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    stripes.forEach { (start, end) ->
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Brush.verticalGradient(listOf(start, end)))
                        )
                    }
                }
            }
        },
        caption = {
            if (friends.isNotEmpty()) {
                AvatarGroup(
                    usernames = remember(friends) { friends.map { it.username } },
                    size = 40.dp,
                    overlap = 12.dp,
                    maxDisplay = MaxFaces
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                text = "Friends",
                style = MaterialTheme.typography.headlineMediumEmphasized,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitleFor(friends.size, listeningNow),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    )
}

/**
 * What the roster amounts to, in one line.
 *
 * "Listening now" leads when anyone is, because that is the thing on this screen that is only true
 * for the next few minutes; the total is still there behind it and will be just as true later.
 */
private fun subtitleFor(friends: Int, listeningNow: Int): String = when {
    friends == 0 -> "Nobody here yet — find people to get started"
    listeningNow > 0 -> "$listeningNow listening now · $friends ${plural(friends)}"
    else -> "$friends ${plural(friends)}"
}

private fun plural(n: Int) = if (n == 1) "friend" else "friends"

/**
 * Much wider than tall. This is a band across the top of a list, not a page that opens on a sleeve,
 * and at a record's proportion it filled the window before a single friend was visible.
 */
private const val FriendsAspect = 2.1f

/**
 * Enough stripes for the band to read as a group rather than a flag, and few enough that each is
 * still wide enough to show its colour. Past this the roster is represented, not enumerated.
 */
private const val MaxStripes = 8

/** The caption's faces. Beyond this [AvatarGroup] draws a +N counter of its own. */
private const val MaxFaces = 5

