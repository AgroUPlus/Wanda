package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.theme.sectionTitle

@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** The redesigned screens' section heading: heavier and larger than [SectionHeader]. */
@Composable
internal fun SectionTitle(title: String, top: Dp = 28.dp) {
    Text(
        text = title,
        style = MaterialTheme.typography.sectionTitle,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = top, bottom = 12.dp)
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
