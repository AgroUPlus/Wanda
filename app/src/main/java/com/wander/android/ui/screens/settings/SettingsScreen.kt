package com.wander.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.agro.AgroSessionViewModel

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onNavidromeLogin: () -> Unit,
    onYouTubeLogin: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val navidrome by viewModel.navidromeConnected.collectAsStateWithLifecycle()
    val youTube by viewModel.youTubeConnected.collectAsStateWithLifecycle()
    val localReady by viewModel.localAvailable.collectAsStateWithLifecycle()
    val monet by viewModel.isMonetDynamic.collectAsStateWithLifecycle()
    val amoled by viewModel.isAmoledBlack.collectAsStateWithLifecycle()
    val offline by viewModel.isOfflineMode.collectAsStateWithLifecycle()
    val incognito by viewModel.isIncognito.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val agro by viewModel.agroConnected.collectAsStateWithLifecycle()
    val agroPairing by viewModel.agroPairing.collectAsStateWithLifecycle()
    val agroSyncSettings by viewModel.agroSyncSettings.collectAsStateWithLifecycle()
    val syncedNavidrome by viewModel.syncedNavidrome.collectAsStateWithLifecycle()
    val librarySync by viewModel.librarySyncEnabled.collectAsStateWithLifecycle()
    val syncProgress by viewModel.librarySyncProgress.collectAsStateWithLifecycle()
    val pendingUploads by viewModel.pendingUploads.collectAsStateWithLifecycle()
    val syncedTracks by viewModel.syncedTracks.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()

    // The system's own delete confirmation. It must be launched from an Activity, which is why the
    // ViewModel hands back an IntentSender rather than doing the deletion itself.
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* MediaStore removes the rows; the next scan reconciles Room. */ }

    // Same singleton repository the resume card reads, so the two never disagree about who is
    // listening. Refreshed on entry rather than polled.
    val agroViewModel: AgroSessionViewModel = hiltViewModel()
    val agroDevices by agroViewModel.devices.collectAsStateWithLifecycle()
    // The ungated session, so a device row stays resumable after the pop-up card is dismissed.
    val agroSession by agroViewModel.latestSession.collectAsStateWithLifecycle()
    val agroResuming by agroViewModel.isResuming.collectAsStateWithLifecycle()
    LaunchedEffect(agro) { if (agro) agroViewModel.refresh() }
    var showAgroDialog by rememberSaveable { mutableStateOf(false) }
    // Sign-out and unpair both discard credentials, so none of these happens on a single stray tap.
    var confirmNavidromeSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmYouTubeSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmAgroUnpair by rememberSaveable { mutableStateOf(false) }
    var showShareDomainDialog by rememberSaveable { mutableStateOf(false) }
    val shareDomain by viewModel.shareDomain.collectAsStateWithLifecycle()
    val agroShareDomain by viewModel.agroShareDomain.collectAsStateWithLifecycle()

    if (showShareDomainDialog) {
        ShareDomainDialog(
            current = shareDomain,
            onSave = { domain ->
                viewModel.setShareDomain(domain)
                showShareDomainDialog = false
            },
            onDismiss = { showShareDomainDialog = false }
        )
    }

    // Pairing succeeded: the row below now reports the connection, so the dialog has nothing left
    // to say.
    if (showAgroDialog && agroPairing is AgroPairingState.Paired) showAgroDialog = false

    if (confirmNavidromeSignOut) {
        ConfirmDialog(
            title = "Sign out of Navidrome?",
            message = "The server address, username and password are erased from this device, " +
                "and your library stops syncing. Anything already downloaded stays playable.",
            confirmLabel = "Sign out",
            onConfirm = viewModel::disconnectNavidrome,
            onDismiss = { confirmNavidromeSignOut = false }
        )
    }

    if (confirmYouTubeSignOut) {
        ConfirmDialog(
            title = "Sign out of YouTube Music?",
            message = "Your library and likes stop syncing until you sign in again. " +
                "Search keeps working signed out.",
            confirmLabel = "Sign out",
            onConfirm = viewModel::disconnectYouTube,
            onDismiss = { confirmYouTubeSignOut = false }
        )
    }

    if (confirmAgroUnpair) {
        ConfirmDialog(
            title = "Unpair from Agro?",
            message = "This device stops appearing to your other devices and can no longer pick " +
                "up their sessions. You will need the server address and passphrase to pair again.",
            confirmLabel = "Unpair",
            onConfirm = viewModel::disconnectAgro,
            onDismiss = { confirmAgroUnpair = false }
        )
    }

    if (showAgroDialog) {
        AgroPairingDialog(
            state = agroPairing,
            onPair = viewModel::pairAgro,
            onDismiss = {
                showAgroDialog = false
                viewModel.resetAgroPairing()
            }
        )
    }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item(key = "title") {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp)
            )
        }

        item(key = "sources") { SettingsSection("Sources") }
        item(key = "navidrome") {
            SettingsRow(
                title = "Navidrome",
                // With settings sync on, an unconnected Navidrome still knows where it should
                // point — saying so is the difference between "sync did nothing" and "sync told
                // this device where to sign in".
                subtitle = when {
                    navidrome -> "Connected to ${viewModel.navidromeServer}"
                    syncedNavidrome?.serverUrl != null ->
                        "Agro has ${syncedNavidrome?.serverUrl} — tap to sign in"
                    else -> "Not connected"
                },
                onClick = if (navidrome) { { confirmNavidromeSignOut = true } } else onNavidromeLogin
            )
        }
        item(key = "ytmusic") {
            SettingsRow(
                title = "YouTube Music",
                subtitle = if (youTube) "Signed in — tap to sign out"
                else "Signed out. Search still works; your library needs sign-in.",
                onClick = if (youTube) { { confirmYouTubeSignOut = true } } else onYouTubeLogin
            )
        }
        // No Internet Archive row: it needs no account, so it had nothing to configure and no
        // onClick — a dead entry sitting among actionable ones.
        item(key = "local") {
            SettingsRow(
                title = "Music on this device",
                subtitle = if (localReady) "Tap to rescan"
                else "Waiting for permission to read audio files",
                onClick = viewModel::rescanLocalLibrary
            )
        }

        item(key = "agro_section") { SettingsSection("Agro Background Sync") }
        item(key = "agro") {
            SettingsRow(
                title = "Agro Device",
                subtitle = if (agro) "${viewModel.agroDevicePetname} • ${viewModel.agroServer} • Tap to unpair"
                else "Not paired — scan the pairing QR, or tap to enter a server",
                onClick = if (agro) { { confirmAgroUnpair = true } } else { { showAgroDialog = true } }
            )
        }
        if (agro) {
            item(key = "agro_sync") {
                SettingsToggle(
                    title = "Sync settings with Agro",
                    subtitle = "Shares the Navidrome address and username between your devices. " +
                        "The password stays on each device — Agro never carries it.",
                    checked = agroSyncSettings,
                    onCheckedChange = viewModel::setAgroSyncSettings
                )
            }
            agroDevicesSection(
                devices = agroDevices,
                handoff = agroSession,
                isResuming = agroResuming,
                onResume = agroViewModel::resume
            )
            librarySyncSection(
                enabled = librarySync,
                onEnabledChange = viewModel::setLibrarySync,
                pendingCount = pendingUploads,
                syncedCount = syncedTracks,
                progress = syncProgress,
                serverSummary = if (navidrome) {
                    "Filed into your Navidrome library, then scanned automatically."
                } else {
                    "Held on your Agro server and offered to your other devices."
                },
                onSyncNow = viewModel::syncLibraryNow,
                onReviewDeletions = {
                    viewModel.buildDeleteRequest { sender ->
                        sender?.let {
                            deleteLauncher.launch(IntentSenderRequest.Builder(it).build())
                        }
                    }
                },
                canDelete = viewModel.canDeleteLocalFiles && syncedTracks > 0,
                localTrackCount = localTracks
            )
        }

        item(key = "appearance") { SettingsSection("Appearance") }
        item(key = "monet") {
            SettingsToggle(
                title = "Match system colours",
                subtitle = "Use the wallpaper palette (Android 12+)",
                checked = monet,
                onCheckedChange = viewModel::setMonetDynamic
            )
        }
        item(key = "amoled") {
            SettingsToggle(
                title = "True black",
                subtitle = "Unlit pixels on OLED screens use no power",
                checked = amoled,
                onCheckedChange = viewModel::setAmoledBlack
            )
        }

        item(key = "playback") { SettingsSection("Playback and storage") }
        item(key = "offline") {
            SettingsToggle(
                title = "Offline mode",
                subtitle = "Only play what is already on this device",
                checked = offline,
                onCheckedChange = viewModel::setOfflineMode
            )
        }
        item(key = "download") {
            SettingsRow(
                title = "Download liked tracks now",
                subtitle = "Otherwise this happens on Wi-Fi while charging",
                onClick = viewModel::downloadLikedNow
            )
        }
        item(key = "cache") {
            SettingsRow(
                title = "Clear streaming cache",
                subtitle = formatBytes(cacheBytes) + " in use",
                onClick = viewModel::clearCache
            )
        }

        item(key = "sharing") { SettingsSection("Sharing") }
        item(key = "share_domain") {
            SettingsRow(
                title = "Custom share domain",
                subtitle = when {
                    // Agro decides for the whole fleet when it has a domain, so saying what this
                    // device was typed into would name a value that is not the one in use.
                    agroShareDomain.isNotBlank() ->
                        "$agroShareDomain/listen — set on your Agro server"
                    shareDomain.isNotBlank() -> "Links go out as $shareDomain/listen — tap to change"
                    else -> "Off — share each backend's own link"
                },
                onClick = { showShareDomainDialog = true }
            )
        }

        item(key = "privacy") { SettingsSection("Privacy") }
        item(key = "incognito") {
            SettingsToggle(
                title = "Incognito",
                subtitle = "Do not record play counts or scrobble to your server",
                checked = incognito,
                onCheckedChange = viewModel::setIncognito
            )
        }
        item(key = "forget") {
            SettingsRow(
                title = "Forget all credentials",
                subtitle = "Signs out of every source and erases stored secrets",
                onClick = viewModel::forgetEverything,
                destructive = true
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes.toDouble() / (1L shl 20))
    else -> "0 MB"
}
