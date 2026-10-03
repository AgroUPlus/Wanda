package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.PersonShape
import com.wander.android.ui.components.avatarGradient
import com.wander.android.ui.theme.profileName

/**
 * The top of somebody's page: their face, their name at a size that is unmistakably the subject,
 * and what they wrote about themselves.
 *
 * Left-aligned, like every other page in the app, rather than centred under a gradient banner. The
 * colour that banner carried survives as the large blob behind it — see [ProfileBackdrop] — so two
 * people's pages still differ from each other exactly as much as their avatars do.
 */
@Composable
internal fun ProfileHero(
    profile: AgroProfile,
    /** On the name alone — how the page's collapsing bar tracks it. */
    titleModifier: Modifier = Modifier
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp)) {
        CuteAvatar(
            seed = profile.username,
            avatarUrl = profile.avatarUrl,
            size = 120.dp,
            shape = PersonShape
        )
        Text(
            text = profile.name,
            style = MaterialTheme.typography.profileName,
            maxLines = ProfileNameMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 20.dp).then(titleModifier)
        )
        Text(
            text = "@" + profile.username,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (!profile.bio.isNullOrBlank()) {
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

/** Shared with the page's collapsing bar, which must wrap the name exactly as the hero does. */
internal const val ProfileNameMaxLines = 2

/**
 * A large cookie in a dark wash of the person's own avatar colour, behind the top of the page.
 *
 * It sits in the screen's root, under the top bar and the list, so it reads as part of the page
 * rather than as something that scrolls past.
 */
@Composable
internal fun ProfileBackdrop(username: String, modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    val tint = remember(username) { avatarGradient(username).first }
    // A dark surface takes a faint wash; a light one needs more of the colour to register at all.
    val strength = if (surface.luminance() < 0.5f) 0.1f else 0.35f
    Box(
        modifier = modifier
            .offset(x = 90.dp, y = (-40).dp)
            .size(340.dp)
            .clip(PersonShape)
            .background(tint.copy(alpha = strength).compositeOver(surface))
    )
}
