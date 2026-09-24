package com.wander.android.data.repository

import com.wander.android.data.sources.agro.AgroFriendNowPlaying

/** A live listen-along, as the UI needs to describe it. */
internal data class ListenAlongSession(
    val host: String,
    val listenerCount: Int,
    val nowPlaying: AgroFriendNowPlaying?,
    val resolvedFrom: ResolvedFrom?,
    /** Set when the host is playing something this device cannot find anywhere. */
    val unresolvable: String? = null
)
