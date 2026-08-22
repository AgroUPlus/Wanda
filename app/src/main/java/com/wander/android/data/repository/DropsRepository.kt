package com.wander.android.data.repository

import com.wander.android.core.database.dao.DropDao
import com.wander.android.core.database.entity.DropEntity
import com.wander.android.data.sources.agro.AgroDrop
import com.wander.android.data.sources.agro.AgroDropsApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The drop inbox: songs friends handed this account, and the ones it handed out.
 *
 * **Room is the source of truth here**, unlike presence in [SocialRepository]. A drop is a message
 * addressed to this account rather than a fact about somebody else's current listening, so it is
 * meant to survive the network being gone, the app being killed, and the friendship ending.
 *
 * The socket is an optimisation, not the delivery path. Wanda only holds a socket while it is
 * foregrounded, so a drop sent overnight arrives by [refresh] on the next resume — anything that
 * depended on the push frame would silently lose those.
 */
@Singleton
internal class DropsRepository @Inject constructor(
    private val dropsApi: AgroDropsApi,
    private val dropDao: DropDao
) {
    val inbox: Flow<List<AgroDrop>> =
        dropDao.observeInbox().map { rows -> rows.map { it.toDrop() } }

    val sent: Flow<List<AgroDrop>> =
        dropDao.observeSent().map { rows -> rows.map { it.toDrop() } }

    /** Counted from the cache rather than asked for, so it is right while offline too. */
    val unreadCount: Flow<Int> = dropDao.observeUnreadCount()

    /**
     * Re-reads both sides.
     *
     * Each side is replaced independently: a failure fetching one must not empty the other, and the
     * two are separate queries on the server anyway.
     */
    suspend fun refresh(): Result<Unit> {
        val now = Instant.now().toEpochMilli()
        val inboxResult = dropsApi.inbox().onSuccess { drops ->
            dropDao.replaceSide(incoming = true, drops = drops.map { it.toEntity(true, now) })
        }
        val sentResult = dropsApi.sent().onSuccess { drops ->
            dropDao.replaceSide(incoming = false, drops = drops.map { it.toEntity(false, now) })
        }
        return inboxResult.mapCatching { sentResult.getOrThrow() }.map { }
    }

    /**
     * Records a drop that arrived over the socket.
     *
     * Inserted rather than triggering a refresh: the frame carries the whole drop, and a round trip
     * here would be a second chance to miss it before the app is backgrounded again.
     */
    suspend fun onPushed(drop: AgroDrop) {
        dropDao.insert(drop.toEntity(incoming = true, syncedAt = Instant.now().toEpochMilli()))
    }

    /**
     * Marks one read, locally first.
     *
     * The badge should clear when it is tapped, not a round trip later. If the server call fails
     * the next [refresh] restores its answer, which is the correct one.
     */
    suspend fun markRead(id: String): Result<Boolean> {
        dropDao.markRead(id, Instant.now().toString())
        return dropsApi.markRead(id)
    }

    suspend fun archive(id: String): Result<Boolean> {
        dropDao.markArchived(id)
        return dropsApi.archive(id)
    }

    /** Hands a track to a friend. Nothing is cached until the server has accepted it. */
    suspend fun drop(
        to: String,
        trackTitle: String,
        artistName: String,
        albumName: String? = null,
        artworkUrl: String? = null,
        contentHash: String? = null,
        trackUri: String? = null,
        note: String? = null
    ): Result<AgroDrop> = dropsApi.drop(
        to = to,
        trackTitle = trackTitle,
        artistName = artistName,
        albumName = albumName,
        artworkUrl = artworkUrl,
        contentHash = contentHash,
        trackUri = trackUri,
        note = note
    ).onSuccess { drop ->
        dropDao.insert(drop.toEntity(incoming = false, syncedAt = Instant.now().toEpochMilli()))
    }

    /** Called when the account is unpaired. Somebody else's inbox must not be waiting for them. */
    suspend fun clear() {
        dropDao.clearAll()
    }
}

private fun AgroDrop.toEntity(incoming: Boolean, syncedAt: Long) = DropEntity(
    id = id,
    fromUser = fromUser,
    toUser = toUser,
    trackTitle = trackTitle,
    artistName = artistName,
    albumName = albumName,
    artworkUrl = artworkUrl,
    contentHash = contentHash,
    trackUri = trackUri,
    note = note,
    createdAt = createdAt,
    readAt = readAt,
    archived = archived,
    incoming = incoming,
    syncedAt = syncedAt
)

private fun DropEntity.toDrop() = AgroDrop(
    id = id,
    fromUser = fromUser,
    toUser = toUser,
    trackTitle = trackTitle,
    artistName = artistName,
    albumName = albumName,
    artworkUrl = artworkUrl,
    contentHash = contentHash,
    trackUri = trackUri,
    note = note,
    createdAt = createdAt,
    readAt = readAt,
    archived = archived
)
