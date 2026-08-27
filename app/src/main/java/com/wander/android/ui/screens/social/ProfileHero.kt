package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.ui.components.CuteAvatar

/**
 * The top of somebody's page.
 *
 * It used to be a 72dp avatar and two lines of text crammed into the corner, with the bio — the
 * one thing on the page a person actually wrote — as another paragraph in the same left-aligned
 * stack, indistinguishable from the statistics below it. A profile is the one screen in this app
 * that is about a person rather than about music, and it now opens like one: centred, with room,
 * and with what they said about themselves given the width to be read.
 *
 * The wash behind it is the theme's own primary container fading to nothing. No cover images:
 * Wanda does not host uploads, and stretching somebody's avatar into a banner is a blur.
 */
@Composable
internal fun ProfileHero(profile: AgroProfile) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeroWashHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(top = 20.dp, bottom = 8.dp)
        ) {
            CuteAvatar(
                seed = profile.username,
                avatarUrl = profile.avatarUrl,
                size = 112.dp,
                showBorder = true
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = profile.name,
                style = MaterialTheme.typography.headlineMedium,
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
}

/** Deep enough to sit behind the avatar and stop at about its waist. */
private val HeroWashHeight = 132.dp
