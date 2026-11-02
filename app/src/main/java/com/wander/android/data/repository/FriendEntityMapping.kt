package com.wander.android.data.repository

import com.wander.android.core.database.entity.FriendEntity
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.data.sources.agro.FriendState

// The Room cache row for a friend, and back. Used by [SocialRepository].

internal fun AgroProfile.toEntity(state: FriendState, now: Long) = FriendEntity(
    username = username,
    displayName = displayName,
    bio = bio,
    avatarUrl = avatarUrl,
    state = if (state == FriendState.ACCEPTED) "accepted" else "pending",
    outgoing = outgoing,
    showNowPlaying = showNowPlaying,
    showStats = showStats,
    syncedAt = now
)

internal fun FriendEntity.toProfile() = AgroProfile(
    username = username,
    displayName = displayName,
    bio = bio,
    avatarUrl = avatarUrl,
    // Not cached: it is only ever "when this row was written", which is not something the UI shows.
    createdAt = "",
    friendState = if (state == "accepted") FriendState.ACCEPTED else FriendState.PENDING,
    outgoing = outgoing,
    showNowPlaying = showNowPlaying,
    showStats = showStats,
    // Whether a friend is listed in the public directory is not this device's business, and the
    // cache would only ever have a stale answer.
    discoverable = false
)
