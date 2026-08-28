package com.wander.android.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroHandoffState
import com.wander.android.data.sources.agro.AgroNode
import com.wander.android.data.sources.agro.MissingTrack
import com.wander.android.ui.components.ResumeHandoffCard
import com.wander.android.ui.components.SyncOfferCard

/**
 * The two cards that rise from the bottom of the browsing surface: "your other device is playing
 * something" and "your server has music this device is missing".
 *
 * Split out of `WanderApp` purely for length — that file is the app shell and had grown past what
 * one file should hold. They belong together: both are bottom-anchored offers sharing one slot,
 * and in practice only one is ever up, since the resume card only appears while nothing is playing
 * here.
 *
 * Floating them over a full-screen Now Playing reads as a stray dialog, so both are gated on the
 * sheet being collapsed by the caller.
 */
@Composable
internal fun BoxScope.BottomOffers(
    syncOffer: List<MissingTrack>,
    isFetchingSync: Boolean,
    onAcceptSync: () -> Unit,
    onDismissSync: () -> Unit,
    handoff: AgroHandoffState?,
    agroDevices: List<AgroNode>,
    isResuming: Boolean,
    sessionArtwork: String?,
    onResume: (AgroHandoffState) -> Unit,
    onDismissHandoff: (AgroHandoffState) -> Unit,
    /** Height of the navigation bar, measured rather than assumed. */
    /** What the dock and the system inset already occupy — see `WanderApp`. */
    dockBottom: Dp
) {
    // Clears the docked strip when one is up, so a card never covers the player it is offering to
    // replace.
    val bottom = dockBottom + 12.dp
    val slot = Modifier
        .align(Alignment.BottomCenter)
        .padding(horizontal = 12.dp)
        .padding(bottom = bottom)

    if (syncOffer.isNotEmpty()) {
        SyncOfferCard(
            count = syncOffer.size,
            sample = remember(syncOffer) {
                syncOffer.take(3).map { "${it.artist} — ${it.title}" }
            },
            isFetching = isFetchingSync,
            onAccept = onAcceptSync,
            onDismiss = onDismissSync,
            modifier = slot
        )
    }

    if (handoff != null) {
        ResumeHandoffCard(
            handoff = handoff,
            deviceName = remember(handoff, agroDevices) {
                agroDevices.firstOrNull { it.deviceId == handoff.deviceId }?.petname
                    ?: "another device"
            },
            isResuming = isResuming,
            isLive = remember(handoff, agroDevices) {
                agroDevices.any { it.deviceId == handoff.deviceId && it.isOnline }
            },
            artworkUrl = sessionArtwork,
            onResume = { onResume(handoff) },
            onDismiss = { onDismissHandoff(handoff) },
            modifier = slot
        )
    }
}
