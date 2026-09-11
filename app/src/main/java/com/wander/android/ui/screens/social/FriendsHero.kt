package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.ImmersiveHero
import com.wander.android.ui.components.avatarGradient

/**
 * The top of the Friends tab: you, and what the roster around you amounts to.
 *
 * It used to draw the roster as a band of everyone's colours, while *you* were a 40dp circle in the
 * corner of the header — the smallest thing on a screen otherwise made of people, and the only one
 * that was yourself. The two have swapped. Opening your own profile is what the hero is for, so it
 * is the hero that carries your face, at a size worth aiming at.
 *
 * The band is your own pair, from the same deterministic `avatarGradient` your avatar is drawn from,
 * so the top of the tab is your colours rather than a generic wash. No uploads are involved: Wanda
 * hosts none, and this needs none.
 *
 * The roster is not lost — it is the line under your name, which is what the count was always
 * saying, and the faces themselves are the row immediately below this.
 */
@Composable
internal fun FriendsHero(
    friends: List<AgroProfile>,
    listeningNow: Int,
    myUsername: String,
    myAvatarUrl: String?,
    onOpenMyProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fallback = MaterialTheme.colorScheme.primaryContainer
    val fallbackEnd = MaterialTheme.colorScheme.surfaceContainerHighest
    // Before the account is known there is no seed to derive a pair from, and inventing one would
    // mean the hero changed colour the moment the username arrived.
    val mine = remember(myUsername) {
        if (myUsername.isBlank()) null else avatarGradient(myUsername)
    }

    ImmersiveHero(
        modifier = modifier,
        aspect = FriendsAspect,
        scrimHeight = 12.dp,
        backdrop = {
            val (start, end) = mine ?: (fallback to fallbackEnd)
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(start, end)))
            )
        },
        caption = {
            CuteAvatar(
                seed = myUsername,
                avatarUrl = myAvatarUrl,
                size = 64.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenMyProfile)
                    .semantics { contentDescription = "My profile" }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (myUsername.isBlank()) "Friends" else "@$myUsername",
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

