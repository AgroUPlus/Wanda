package com.wander.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.wander.android.data.repository.FetchProgress
import com.wander.android.data.sources.agro.SyncRoute

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
    onOpenSyncDetails: () -> Unit,
    /** Cover art for the first few offered tracks, resolved from the local library. */
    syncCovers: List<String>,
    fetchProgress: FetchProgress,
    /** Measured before a fetch starts; see `PeerReachability`. */
    offerRoute: SyncRoute?,
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

    // Animated rather than appearing outright: these are unsolicited cards over whatever the user
    // was reading, and something that pops into existence at the bottom of the screen reads as a
    // glitch. The spring comes from `MotionScheme` like every other transition in the app rather
    // than being hand-rolled here.
    val enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
        slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it }
    val exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
        slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it }

    AnimatedVisibility(visible = syncOffer.isNotEmpty(), enter = enter, exit = exit, modifier = slot) {
        // The route is decided by whoever measured it — `offerRoute` probes the peer's local
        // address before a fetch, and `fetchProgress.route` reports what a running transfer is
        // actually using. Neither is inferred from the offer, which is what made the card claim
        // "Direct Wi-Fi" on a phone that could not reach the peer at all.
        val source = remember(syncOffer) { syncOffer.firstOrNull()?.peerSources?.firstOrNull() }
        val route = fetchProgress.route ?: offerRoute

        SyncOfferCard(
            count = syncOffer.size,
            covers = syncCovers,
            route = route,
            peerName = source?.petname,
            isFetching = isFetchingSync,
            progress = fetchProgress.takeIf { it.total > 0 && isFetchingSync }
                ?.let { it.done.size.toFloat() / it.total },
            onAccept = onAcceptSync,
            onOpenDetails = onOpenSyncDetails,
            onDismiss = onDismissSync
        )
    }

    AnimatedVisibility(visible = handoff != null, enter = enter, exit = exit, modifier = slot) {
        // Held across the exit animation so the card does not blank out as it leaves.
        val shown = remember(handoff) { handoff } ?: return@AnimatedVisibility
        ResumeHandoffCard(
            handoff = shown,
            deviceName = remember(shown, agroDevices) {
                agroDevices.firstOrNull { it.deviceId == shown.deviceId }?.petname
                    ?: "another device"
            },
            isResuming = isResuming,
            isLive = remember(shown, agroDevices) {
                agroDevices.any { it.deviceId == shown.deviceId && it.isOnline }
            },
            artworkUrl = sessionArtwork,
            onResume = { onResume(shown) },
            onDismiss = { onDismissHandoff(shown) }
        )
    }
}
