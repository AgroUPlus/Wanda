package com.wander.android.data.repository

import com.wander.android.core.notification.FriendNotifier
import com.wander.android.data.sources.agro.AgroLiveMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The things Agro leaves for this account to pick up, rather than streams to it: jam recaps and
 * blend invitations. Each push carries nothing but "look again", so both are handled the same way.
 */
@Singleton
internal class AgroSideInbox @Inject constructor(
    private val jamRecaps: JamRecapRepository,
    private val blends: BlendRepository,
    private val notifier: FriendNotifier
) {
    /** Re-reads both, as a full resync has to: the push that would have said so may be lost. */
    suspend fun refreshAll() {
        jamRecaps.refresh()
        blends.refreshInvites()
    }

    suspend fun onMessage(message: AgroLiveMessage) {
        when (message) {
            is AgroLiveMessage.JamRecapWritten -> jamRecaps.refresh()
            // Only on the push, never on a resync: an invitation already waiting at launch is
            // on the Activity screen and in the library, and saying so again at every start
            // would make the notification mean nothing.
            is AgroLiveMessage.BlendInvited -> blends.refreshInvites().onSuccess { fresh ->
                fresh.forEach(notifier::notifyBlendInvite)
            }
            else -> Unit
        }
    }
}
