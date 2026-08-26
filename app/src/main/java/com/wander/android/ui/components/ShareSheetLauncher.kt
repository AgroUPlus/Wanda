package com.wander.android.ui.components

import android.content.Context
import android.content.Intent
import com.wander.android.data.repository.ShareLink
import com.wander.android.data.sources.ShareKind

/**
 * Hands a minted link to Android's own share sheet.
 *
 * The link, the title and the artist all travel: a bare URL in a chat window says nothing about
 * what is behind it, and most targets will show the subject line.
 */
internal fun Context.launchShareSheet(link: ShareLink) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, link.target.description)
        putExtra(Intent.EXTRA_TEXT, "${link.target.description}\n${link.url}")
    }
    // Always the chooser, never a remembered default: sharing a track is a deliberate act aimed
    // at one person, and silently reusing last time's target would send it to the wrong one.
    startActivity(
        Intent.createChooser(intent, "Share ${link.target.kind.label}")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** Lower case, for use mid-sentence in a chooser title. */
private val ShareKind.label: String
    get() = when (this) {
        ShareKind.TRACK -> "track"
        ShareKind.ALBUM -> "album"
        ShareKind.ARTIST -> "artist"
        ShareKind.PLAYLIST -> "playlist"
    }
