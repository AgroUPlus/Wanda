package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.ImmersiveHero
import com.wander.android.ui.components.avatarGradient

/**
 * The top of somebody's page.
 *
 * It used to be a 72dp avatar and two lines of text crammed into the corner, with the bio — the
 * one thing on the page a person actually wrote — as another paragraph in the same left-aligned
 * stack, indistinguishable from the statistics below it. A profile is the one screen in this app
 * that is about a person rather than about music, and it now opens like one: centred, with room,
 * and with what they said about themselves given the width to be read.
 *
 * It opens on the same shape every other page in the app opens on — see [ImmersiveHero] — so a
 * profile reached from an artist page reads as the same kind of thing. There is still no cover
 * image, and there is still not going to be one: Wanda hosts no uploads, and blowing an avatar up
 * into a banner is a blur. What fills the picture's place is the pair of colours that avatar is
 * already drawn from, so a page and the face on it agree, and two people's pages differ from each
 * other exactly as much as their avatars do.
 */
@Composable
internal fun ProfileHero(profile: AgroProfile) {
    val (start, end) = remember(profile.username) { avatarGradient(profile.username) }

    ImmersiveHero(
        aspect = ProfileAspect,
        scrimHeight = 8.dp,
        horizontalPadding = 28.dp,
        backdrop = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(start, end)))
            )
        },
        caption = {
            CuteAvatar(
                seed = profile.username,
                avatarUrl = profile.avatarUrl,
                size = 112.dp,
                showBorder = true
            )
            Spacer(Modifier.height(2.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "@" + profile.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!profile.bio.isNullOrBlank()) {
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    )
}

/**
 * Wider than tall, unlike a record's hero.
 *
 * The caption here is an avatar and up to four lines under it rather than a title, so the colour
 * behind it only has to run far enough to read before the fade takes over. Holding a sleeve's
 * proportion pushed the name most of the way down the window.
 */
private const val ProfileAspect = 1.35f
