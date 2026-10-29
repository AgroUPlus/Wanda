package com.wander.android.core.sync

import android.net.Uri
import android.provider.MediaStore

/**
 * The stream URI of a track this device may hand to a peer, or null.
 *
 * Only MediaStore audio is served. A track's stream URI can come from a restored backup or a
 * podcast feed, and `ContentResolver.openInputStream` also opens `file://`, so a crafted one could
 * otherwise point at the app's own database and have it streamed to whoever asked for that hash.
 */
internal object PeerServableUri {
    fun of(streamUri: String?): Uri? {
        val uri = streamUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        return uri?.takeIf { it.scheme == "content" && it.authority == MediaStore.AUTHORITY }
    }
}
