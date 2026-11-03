package com.wander.android.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem

/** Metadata-extras key under which [QueueInsert.token] rides on every item one insert added. */
private const val EXTRA_INSERT_TOKEN = "wanda.insert"

/**
 * One "play next" or "add to queue", as a handle its undo can find again.
 *
 * Found by [token] rather than by track id or position: by the time the undo lands the queue may
 * have moved on, been shuffled or taken other inserts, and the same track can sit in it twice. The
 * token goes in the media metadata's extras, the part of an item that already carries the queued
 * track across to the playback service and back (see [toMediaItem]); a `localConfiguration` tag
 * would not survive that trip.
 *
 * [queueWasEmpty] is whether the insert landed on an empty player, which "play next" starts.
 */
class QueueInsert internal constructor(internal val token: String, internal val queueWasEmpty: Boolean)

internal fun MediaItem.taggedWith(insert: QueueInsert): MediaItem {
    val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply { putString(EXTRA_INSERT_TOKEN, insert.token) }
    return buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build()).build()
}

/**
 * [this] carrying [from]'s insert token, if it had one: for an item the service rebuilt from its id
 * alone, so a looked-up copy can still be undone.
 */
internal fun MediaItem.keepingInsertOf(from: MediaItem): MediaItem {
    val token = from.insertToken ?: return this
    return taggedWith(QueueInsert(token, queueWasEmpty = false))
}

internal val MediaItem.insertToken: String? get() = mediaMetadata.extras?.getString(EXTRA_INSERT_TOKEN)

/** What undoing a [QueueInsert] does to the queue as it stands now. */
internal sealed interface InsertUndo {
    /** Nothing of the insert is left that may be taken out. */
    data object Unchanged : InsertUndo

    /** The queue is the insert and nothing else: empty it again, which also ends its playback. */
    data object Clear : InsertUndo

    /** Remove these item indices, highest first so each removal leaves the rest where they are. */
    data class Remove(val indices: List<Int>) : InsertUndo
}

/**
 * Decides [InsertUndo] from [tokens] (each queue item's insert token, by player index) and the
 * player's [currentIndex].
 *
 * The item playing now stays. It became current after the insert, so the listener has already
 * heard it start, and taking it out would be a skip they did not ask for; the undo is only
 * on offer for a few seconds, so this is the rare insert that came up while it still was.
 * The exception is an insert that filled an empty queue and is still all of it: undoing that is
 * putting the player back as it was, empty, so it is cleared, current item and all.
 */
internal fun planInsertUndo(tokens: List<String?>, currentIndex: Int, insert: QueueInsert): InsertUndo {
    val inserted = tokens.indices.filter { tokens[it] == insert.token }
    if (inserted.isEmpty()) return InsertUndo.Unchanged
    if (insert.queueWasEmpty && inserted.size == tokens.size) return InsertUndo.Clear
    val removable = inserted.filter { it != currentIndex }.sortedDescending()
    return if (removable.isEmpty()) InsertUndo.Unchanged else InsertUndo.Remove(removable)
}
