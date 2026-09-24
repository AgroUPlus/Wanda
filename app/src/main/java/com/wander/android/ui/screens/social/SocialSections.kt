package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import com.wander.android.data.sources.agro.AgroProfile

@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
internal fun NotPairedNotice(onOpenSettings: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.social_friends_need_agro_server),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.social_pair_one_create_account_one),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilledTonalButton(onClick = onOpenSettings, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.social_open_settings))
        }
    }
}

@Composable
internal fun SocialErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
internal fun ListeningNowRow(
    playing: List<Pair<AgroProfile, AgroFriendNowPlaying>>,
    isListeningAlong: (String) -> Boolean,
    onOpenProfile: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        items(
            count = playing.size,
            key = { index -> "presence_" + playing[index].first.username }
        ) { index ->
            val (profile, now) = playing[index]
            FriendPresenceCard(
                profile = profile,
                nowPlaying = now,
                isListeningAlong = isListeningAlong(profile.username),
                onOpenProfile = { onOpenProfile(profile.username) }
            )
        }
    }
}
