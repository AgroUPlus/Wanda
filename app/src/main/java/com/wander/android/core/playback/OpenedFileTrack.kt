package com.wander.android.core.playback

import android.net.Uri
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack

/**
 * An audio file another app asked Wanda to open ("Open with"), played once and kept nowhere.
 *
 * Its URI is a `content://` grant that lasts only as long as the app that handed it over allows,
 * or a `file://` path outside the library. Neither belongs in the library, the history or the
 * saved queue: a restored queue would hold a grant that has since lapsed. So the id is marked,
 * and [isOpenedFileId] is what the play counter and the saved queue check to leave it out.
 */
private const val OPENED_FILE_PREFIX = "opened:"

internal fun isOpenedFileId(id: String): Boolean = id.startsWith(OPENED_FILE_PREFIX)

/** [uri] as a queue item. The stream is the URI itself: [toMediaItem] addresses it directly. */
internal fun openedFileTrack(uri: Uri, title: String, artist: String): UnifiedTrack = UnifiedTrack(
    // The same file opened twice is the same item.
    id = OPENED_FILE_PREFIX + Integer.toHexString(uri.toString().hashCode()),
    source = SourceType.LOCAL,
    title = title,
    artist = artist,
    streamUri = uri.toString()
)
