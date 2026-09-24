package com.wander.android.data.repository

import com.wander.android.data.model.UnifiedTrack

/** Where a listen-along track was found, so the UI can be honest about what is playing. */
internal enum class ResolvedFrom {
    /** A local file on this device or in downloaded offline cache. */
    LOCAL_STORAGE,

    /** Streamed from your personal Navidrome server. */
    NAVIDROME,

    /** Matched and streamed from YouTube Music. */
    YOUTUBE_MUSIC,

    /** Streamed directly over LAN from the host/peer device via P2P HTTP chunks. */
    P2P_DIRECT,

    /**
     * Streamed over a direct radio link with no router involved at all — a car, a plane, a
     * festival.
     */
    P2P_OFFGRID,

    /** Streamed via Agro ephemeral server relay pipe. */
    AGRO_RELAY
}

internal data class ResolvedTrack(val track: UnifiedTrack, val from: ResolvedFrom)
