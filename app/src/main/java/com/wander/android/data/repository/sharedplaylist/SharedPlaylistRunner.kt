package com.wander.android.data.repository.sharedplaylist

import com.wander.android.R
import com.wander.android.core.database.dao.SharedPlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.SharedPlaylistEntity
import com.wander.android.core.sync.SharedPlaylistSyncScheduler
import com.wander.android.data.repository.PlaylistPublicationRepository
import com.wander.android.data.sources.agro.AgroSharedPlaylistApi
import com.wander.android.data.sources.agro.PlaylistRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When shared playlists sync, and what the user hears about it.
 *
 * Three triggers: an edit made here ([syncSoon]), a push from the server saying a playlist moved on
 * ([onRemoteChange]), and the periodic worker as a backstop ([syncAll]). Opening a playlist also
 * syncs it. Outcomes worth knowing — edits that no longer applied, a playlist no longer shared — go
 * to the app's snackbar; an ordinary success says nothing.
 */
@Singleton
class SharedPlaylistRunner @Inject constructor(
    private val api: AgroSharedPlaylistApi,
    private val dao: SharedPlaylistDao,
    private val trackDao: TrackDao,
    private val sync: SharedPlaylistSync,
    private val messages: PlaylistPublicationRepository,
    private val scheduler: SharedPlaylistSyncScheduler
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncSoon(agroId: String) {
        scope.launch { syncNow(agroId) }
    }

    /**
     * Syncs until nothing made here is left unsent, or a pass fails. Edits made while one pass is
     * in flight are picked up by the next, a few at most.
     */
    suspend fun syncNow(agroId: String): SyncOutcome {
        val wasRevoked = dao.get(agroId)?.syncState == SharedSyncState.REVOKED.name
        var outcome: SyncOutcome
        var passes = 0
        do {
            outcome = sync.sync(agroId)
            passes++
        } while (outcome is SyncOutcome.Synced && dao.ops(agroId).isNotEmpty() && passes < MAX_PASSES)
        // Edits made offline are not lost with the process: WorkManager sends them once it can.
        if (outcome is SyncOutcome.Failed && dao.ops(agroId).isNotEmpty()) scheduler.syncWhenOnline()
        report(agroId, outcome, wasRevoked)
        return outcome
    }

    /** [syncAll], without waiting: for a socket that came back having missed what changed. */
    fun syncAllSoon() {
        scope.launch { syncAll() }
    }

    /** A push named [agroId] at [revision]; null means it was deleted. */
    fun onRemoteChange(agroId: String, revision: Long?) {
        scope.launch {
            val copy = dao.get(agroId) ?: return@launch
            when {
                revision == null -> markRevoked(copy)
                revision != copy.revision -> syncNow(agroId)
            }
        }
    }

    /**
     * Brings every copy up to date: one cheap call says which changed, and only those are fetched.
     * Copies with unsent edits, or never read back, sync whatever the server says.
     */
    suspend fun syncAll(): Boolean {
        if (!api.isAvailable) return true
        val copies = dao.all()
        if (copies.isEmpty()) return true
        val revisions = api.revisions(copies.map { it.agroId }).getOrElse { return false }
            .associateBy { it.id }
        copies.forEach { copy ->
            val current = revisions[copy.agroId]
            when {
                current == null || !current.accessible -> markRevoked(copy)
                current.revision != copy.revision ||
                    copy.revision == SharedPlaylistEntity.UNSYNCED ||
                    dao.ops(copy.agroId).isNotEmpty() -> syncNow(copy.agroId)
            }
        }
        return true
    }

    private suspend fun markRevoked(copy: SharedPlaylistEntity) {
        if (copy.isBlend) return ended(copy)
        if (isOwn(copy)) return letGo(copy)
        if (copy.syncState == SharedSyncState.REVOKED.name) return
        dao.setState(copy.agroId, SharedSyncState.REVOKED.name)
        messages.report(R.string.shared_playlist_revoked, copy.title)
    }

    private suspend fun report(agroId: String, outcome: SyncOutcome, wasRevoked: Boolean) {
        val title = dao.get(agroId)?.title.orEmpty()
        when (outcome) {
            is SyncOutcome.Synced -> if (outcome.dropped > 0) {
                messages.report(R.string.shared_playlist_edits_dropped, outcome.dropped, title)
            }
            // Said once, when it happens, not on every later look at a copy already marked so.
            SyncOutcome.Revoked -> {
                val copy = dao.get(agroId)
                when {
                    copy != null && copy.isBlend -> ended(copy)
                    copy != null && isOwn(copy) -> letGo(copy)
                    !wasRevoked -> messages.report(R.string.shared_playlist_revoked, title)
                }
            }
            // Being offline is not news; the indicator on the playlist already says it is behind.
            is SyncOutcome.Failed -> Unit
        }
    }

    /**
     * This account published [copy], and its owner can always open a playlist that exists — so the
     * server not letting it means it was deleted, from Agro's own dashboard or another device. The
     * Wanda playlist is untouched; what goes is the record of a copy that no longer exists, so the
     * playlist reads as not shared and sharing it again makes a new copy rather than a dead link.
     */
    private suspend fun letGo(copy: SharedPlaylistEntity) {
        dao.forget(copy.agroId)
        trackDao.deleteUnreferencedUnresolved()
        messages.report(R.string.playlist_unshared_from_agro, copy.title)
    }

    /**
     * A blend this account can no longer open has ended — its creator ended it, or this account
     * left it on another device. Unlike a shared playlist there is nothing to keep: what it held
     * was written from listening that is no longer being shared, so the copy goes everywhere.
     */
    private suspend fun ended(copy: SharedPlaylistEntity) {
        dao.forget(copy.agroId)
        trackDao.deleteUnreferencedUnresolved()
        messages.report(R.string.blend_ended, copy.title)
    }

    /** Owned by the account signed in now: a copy made under another account is not its to judge. */
    private fun isOwn(copy: SharedPlaylistEntity): Boolean =
        copy.myRole == PlaylistRole.OWNER.name &&
            (copy.ownerId.isBlank() || copy.ownerId.equals(api.me, ignoreCase = true))

    private companion object {
        const val MAX_PASSES = 3
    }
}
