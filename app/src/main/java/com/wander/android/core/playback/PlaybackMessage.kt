package com.wander.android.core.playback

import android.content.Context
import androidx.annotation.StringRes

/**
 * A snackbar's worth of text from playback, resolved against resources only when it is shown, so
 * it follows the app's language rather than whatever was hardcoded where it was raised.
 */
sealed interface PlaybackMessage {

    fun resolve(context: Context): String

    /** A string resource and its format arguments. */
    data class Resource(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : PlaybackMessage {
        override fun resolve(context: Context): String = context.getString(id, *args.toTypedArray())
    }

    /**
     * Text that is already the message: localized by whoever raised it, or a failure reason from
     * a source that no mapping recognises and is shown as it came.
     */
    data class Verbatim(val text: String) : PlaybackMessage {
        override fun resolve(context: Context): String = text
    }
}

/**
 * A notice for the snackbar: its [message] and, when what it reports can be taken back, the
 * [undo] that does so, offered as the snackbar's action.
 */
data class PlaybackNotice(val message: PlaybackMessage, val undo: (() -> Unit)? = null)
