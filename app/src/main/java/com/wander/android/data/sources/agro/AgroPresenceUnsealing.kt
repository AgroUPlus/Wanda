package com.wander.android.data.sources.agro

import com.wander.android.core.security.IdentityKeyManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Opens a friend's sealed session, so the rest of the app sees an ordinary one.
 *
 * Called at every point presence arrives — the `friendsNowPlaying` query, the friends list, and
 * the live socket. One definition, for the reason `decryptIfNeeded` gives: two paths that unseal
 * slightly differently is a feed that shows a track over HTTP and a placeholder over the socket.
 *
 * A session that arrives sealed and will not open is returned marked [AgroFriendNowPlaying.isLocked]
 * rather than as its placeholder. The friend is playing something; this device cannot read what,
 * and saying "Private Session" as though that were the track name would be inventing an answer.
 */
internal fun AgroFriendNowPlaying.openIfSealed(
    identityKeyManager: IdentityKeyManager,
    extraCandidates: List<String> = emptyList()
): AgroFriendNowPlaying {
    // The query hands back the one copy the server picked for this device; the socket frame hands
    // back every copy the friend published to this account, because it does not know which device
    // is listening. Both end up here as candidates, and the private key decides — the same reason
    // `openAnyNote` does not match on device id.
    val candidates = (listOfNotNull(encryptedPresence) + extraCandidates).filter { it.isNotBlank() }
    if (candidates.isEmpty()) return this
    return withSealedMetadata(identityKeyManager.openAnyNote(candidates))
}

/**
 * Applies an opened envelope, or marks the session locked when there was not one.
 *
 * Separated from the unsealing above because this half is where the decisions are — which fields
 * an envelope may leave out, and what a malformed one means — and it can be tested without a
 * keystore, which [IdentityKeyManager] needs and a JVM unit test does not have.
 */
internal fun AgroFriendNowPlaying.withSealedMetadata(opened: String?): AgroFriendNowPlaying {
    if (opened == null) return copy(isLocked = true)

    val fields = runCatching { Json.parseToJsonElement(opened).jsonObject }.getOrNull()
        ?: return copy(isLocked = true)

    // Each field falls back to what was already there rather than to a blank: an envelope that
    // names only some of them is still an improvement on the placeholder beside it.
    return copy(
        trackUri = fields.str("trackUri") ?: trackUri,
        trackTitle = fields.str("trackTitle") ?: trackTitle,
        artistName = fields.str("artistName") ?: artistName,
        albumName = fields.str("albumName") ?: albumName,
        artworkUrl = fields.str("artworkUrl") ?: artworkUrl,
        // The hash is absent from the plaintext columns under a sealed session, so this is the only
        // place a listener can get it — and without it, following along can name the track but not
        // find the file.
        contentHash = fields.str("contentHash") ?: contentHash,
        isLocked = false
    )
}
