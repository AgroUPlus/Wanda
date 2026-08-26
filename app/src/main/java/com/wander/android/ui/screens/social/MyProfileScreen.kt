package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.headerInset

/**
 * Your own page: who other people see, and the way into your listening statistics.
 *
 * It exists at all because there was no self-view. `updateProfile` had been on the repository
 * since profiles did, with no caller anywhere — display name, bio and avatar were fields the
 * server stored and the app could never set, so everyone was their username forever.
 *
 * Reachable with no server paired and with no friends. Statistics are computed from this device's
 * own history and are about you either way; gating the screen on having an audience would hide
 * your own listening behind other people's existence.
 */
@Composable
internal fun MyProfileScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenStats: () -> Unit,
    viewModel: MyProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    // Seeded from the server's answer rather than kept in sync with it, so a reload landing while
    // someone is mid-sentence does not overwrite what they are typing.
    LaunchedEffect(state.profile?.username) {
        displayName = state.profile?.displayName.orEmpty()
        bio = state.profile?.bio.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding.headerInset())
            .verticalScroll(rememberScrollState())
            .padding(bottom = contentPadding.calculateBottomPadding())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(text = "My profile", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
            CuteAvatar(
                seed = state.username.ifBlank { "wanda" },
                avatarUrl = state.profile?.avatarUrl,
                size = 88.dp
            )
            Text(
                text = state.profile?.name ?: state.username.ifBlank { "Not signed in" },
                style = MaterialTheme.typography.headlineSmall
            )
            if (state.isPaired) {
                Text(
                    text = "@" + state.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FilledTonalButton(
            onClick = onOpenStats,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        ) {
            Icon(Icons.Rounded.BarChart, contentDescription = null)
            Text("My listening", modifier = Modifier.padding(start = 8.dp))
        }

        if (!state.isPaired) {
            Text(
                text = "Pair an Agro server in Settings to have a name, a bio and friends. " +
                    "Your listening statistics work without one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
            return@Column
        }

        Text(
            text = "How you appear",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp)
        )

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display name") },
            supportingText = { Text("Shown instead of your username. Leave it empty to just be @${state.username}.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
        )

        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("Bio") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
        )

        state.error?.let { message ->
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Button(
            onClick = { viewModel.save(displayName, bio) },
            enabled = !state.isSaving,
            modifier = Modifier
                .align(Alignment.End)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(if (state.isSaving) "Saving…" else "Save")
        }
    }
}
