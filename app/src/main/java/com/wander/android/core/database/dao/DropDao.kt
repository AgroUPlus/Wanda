package com.wander.android.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wander.android.core.database.entity.DropEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DropDao {

    /** The inbox: what was sent to this account and not yet tidied away. */
    @Query("SELECT * FROM drops WHERE incoming = 1 AND archived = 0 ORDER BY createdAt DESC")
    fun observeInbox(): Flow<List<DropEntity>>

    @Query("SELECT * FROM drops WHERE incoming = 0 ORDER BY createdAt DESC")
    fun observeSent(): Flow<List<DropEntity>>

    /**
     * Everything exchanged with one person, oldest first, both directions in one list.
     *
     * The seam that makes this a conversation rather than a mailbox. `incoming` is still stored,
     * because a bubble has to know which side to sit on, but it is no longer what the list is
     * split by.
     *
     * Archived drops are included: archiving clears the *inbox*, which is a queue of things to
     * deal with, and dropping them from the record would leave a history with holes in it.
     */
    @Query(
        """
        SELECT * FROM drops
        WHERE (incoming = 1 AND fromUser = :username COLLATE NOCASE)
           OR (incoming = 0 AND toUser = :username COLLATE NOCASE)
        ORDER BY createdAt ASC
        """
    )
    fun observeConversation(username: String): Flow<List<DropEntity>>

    /**
     * One row per person, carrying the newest exchange with them — the conversation list.
     *
     * The counterpart is whoever is not this account, which is why it is chosen by `incoming`
     * rather than by comparing against a username the DAO does not have.
     */
    @Query(
        """
        SELECT * FROM drops
        WHERE id IN (
            SELECT id FROM drops d
            WHERE d.createdAt = (
                SELECT MAX(d2.createdAt) FROM drops d2
                WHERE (CASE WHEN d2.incoming = 1 THEN d2.fromUser ELSE d2.toUser END)
                    = (CASE WHEN d.incoming = 1 THEN d.fromUser ELSE d.toUser END)
            )
        )
        ORDER BY createdAt DESC
        """
    )
    fun observeThreads(): Flow<List<DropEntity>>

    /** Unread drops per counterpart, for the badge on each row of the conversation list. */
    @Query(
        """
        SELECT fromUser AS username, COUNT(*) AS count FROM drops
        WHERE incoming = 1 AND readAt IS NULL AND archived = 0
        GROUP BY fromUser COLLATE NOCASE
        """
    )
    fun observeUnreadByFriend(): Flow<List<UnreadCount>>

    @Query("SELECT COUNT(*) FROM drops WHERE incoming = 1 AND archived = 0 AND readAt IS NULL")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(drops: List<DropEntity>)

    @Query("DELETE FROM drops WHERE incoming = :incoming")
    suspend fun clear(incoming: Boolean)

    @Query("DELETE FROM drops")
    suspend fun clearAll()

    /**
     * Replaces one side of the table in a single transaction.
     *
     * Scoped to `incoming` so refreshing the inbox does not wipe the sent list, and transactional
     * for the same reason `FriendDao.replaceAll` is: a momentarily empty inbox is a visible flicker
     * of "nobody has sent you anything".
     *
     * The server's answer is the whole truth for that side, so anything absent from it has been
     * archived or withdrawn and should go — merging would leave tidied drops on screen forever.
     */
    @Transaction
    suspend fun replaceSide(incoming: Boolean, drops: List<DropEntity>) {
        clear(incoming)
        upsert(drops)
    }

    /**
     * Records a drop that arrived over the socket, without disturbing anything else.
     *
     * Not a `replaceSide`: a push frame is news about one drop, not a statement about the whole
     * inbox, and treating it as the latter would delete every other drop the moment one arrived.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(drop: DropEntity)

    /**
     * Marks one read locally.
     *
     * Written here as well as on the server so the badge clears the instant it is tapped rather
     * than after a round trip. The next refresh overwrites it with the server's answer, which will
     * agree unless the call failed — in which case the server is right and this corrects itself.
     */
    @Query("UPDATE drops SET readAt = :at WHERE id = :id AND readAt IS NULL")
    suspend fun markRead(id: String, at: String)

    @Query("UPDATE drops SET archived = 1 WHERE id = :id")
    suspend fun markArchived(id: String)

    /**
     * Records a reaction locally so the bubble answers the tap immediately.
     *
     * Applied on both sides of the table for one drop id: the same message exists as a received
     * row here and a sent row on the other device, and a refresh will confirm whichever this
     * account holds.
     */
    @Query("UPDATE drops SET reaction = :emoji WHERE id = :id")
    suspend fun setReaction(id: String, emoji: String?)
}

/** How many unread drops one friend has sent. */
data class UnreadCount(val username: String, val count: Int)
