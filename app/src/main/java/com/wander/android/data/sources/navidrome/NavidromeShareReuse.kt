package com.wander.android.data.sources.navidrome

import java.time.Instant
import java.time.OffsetDateTime

/**
 * The share of [songId] on its own that [shares] already holds and that is still open at [now],
 * or null when a new one has to be made.
 *
 * Every share used to mint a fresh `createShare`, so sharing one song ten times left ten shares on
 * the server. Only a share of exactly that song is reused — one that bundles it with others is a
 * different link. A share whose expiry cannot be read is not reused: guessing it is still open
 * would hand out a link that may already be dead.
 */
internal fun reusableShare(shares: List<SubsonicShare>, songId: String, now: Instant): SubsonicShare? =
    shares.firstOrNull { share ->
        share.entry?.map { it.id } == listOf(songId) && share.url.isNotBlank() && share.isOpenAt(now)
    }

private fun SubsonicShare.isOpenAt(now: Instant): Boolean {
    val expiry = expires ?: return true
    val at = runCatching { OffsetDateTime.parse(expiry).toInstant() }.getOrNull() ?: return false
    return at.isAfter(now)
}
