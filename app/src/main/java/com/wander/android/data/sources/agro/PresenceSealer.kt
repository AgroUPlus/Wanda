package com.wander.android.data.sources.agro

import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.data.repository.FriendKeyDirectory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seals what this device is playing, once per friend device that can open it.
 *
 * The handoff envelope is sealed under a subkey of this account's own vault key, so only this
 * account's other devices can read it. That is what a handoff is for — resuming on your own
 * laptop — but the social feed reads the same row, and a sealed session leaves it holding a
 * placeholder. Friends saw "Private Session" no matter what the visibility flag said.
 *
 * The copies made here are the answer: the same metadata, sealed to a friend device's published
 * identity key, exactly as a drop is sealed. The server relays bytes it cannot read, and each
 * friend's device opens the one addressed to it.
 *
 * Nothing here decides *whether* a friend may see the session. That is the account's visibility
 * flag and it is enforced on the server, which has no way to tell a sealed session from any other.
 * Sealing changes who can read a session, never who is allowed one.
 */
@Singleton
internal class PresenceSealer @Inject constructor(
    private val identityKeyManager: IdentityKeyManager,
    private val friendKeys: FriendKeyDirectory
) {
    /**
     * One copy per friend device, for the friends who have published a key.
     *
     * A friend with no published key gets no copy and sees the placeholder. There is deliberately
     * no plaintext fallback: `AgroDropsApi` has one, because a drop is a note typed for one person
     * and failing to deliver it is worse than sending it in clear — but presence is published
     * continuously to everyone, and a fallback would quietly undo the encryption for the whole
     * feed the first time a lookup failed.
     *
     * Returns empty when nothing could be sealed, which the caller sends as an empty set: an
     * explicit "there are no copies", clearing whatever the last track left behind.
     */
    suspend fun sealFor(friends: List<String>, metadataJson: String): List<AgroPresenceCopy> {
        val copies = mutableListOf<AgroPresenceCopy>()
        for (friend in friends) {
            val keysByDevice = friendKeys.keysFor(friend)
            if (keysByDevice.isEmpty()) continue
            identityKeyManager.sealNoteToKeys(keysByDevice, metadataJson)
                .forEach { (deviceId, ciphertext) ->
                    copies += AgroPresenceCopy(friend, deviceId, ciphertext)
                }
        }
        return copies
    }
}

/** One sealed copy of a session, and the friend device it was sealed to. */
internal data class AgroPresenceCopy(
    val recipientUserId: String,
    val recipientDeviceId: String,
    val ciphertext: String
)
