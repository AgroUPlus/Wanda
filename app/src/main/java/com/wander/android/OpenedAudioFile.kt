package com.wander.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import com.wander.android.core.network.loggableMessage
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.openedFileTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays an audio file another app opened with Wanda — a file manager, a download, an attachment.
 *
 * Played as it is, once: not scanned into the library, not written to history, not saved with the
 * queue — see [openedFileTrack]. The read grant that came with the intent belongs to this app's
 * process, which is also where the player runs, so nothing needs to be persisted for it either.
 */
@Singleton
internal class OpenedAudioFile @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerConnection: PlayerConnection
) {

    fun play(scope: CoroutineScope, uri: Uri) {
        scope.launch {
            val title = try {
                displayName(uri)
            } catch (denied: SecurityException) {
                // No grant came with the intent: the player could not read the file either.
                Log.w(TAG, "Opened file is not readable: ${denied.loggableMessage()}")
                Toast.makeText(context, R.string.opened_file_no_access, Toast.LENGTH_LONG).show()
                return@launch
            }
            val track = openedFileTrack(
                uri,
                title = title ?: context.getString(R.string.opened_file_untitled),
                artist = context.getString(R.string.opened_file_unknown_artist)
            )
            playerConnection.play(listOf(track))
        }
    }

    /** The file's name without its extension, as the sending app reports it or the path ends. */
    private suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        val name = if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } else {
            uri.lastPathSegment
        }
        name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "OpenedAudioFile"
    }
}
