package com.wander.android.core.sync

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a fetched track into the device's music library.
 *
 * Through MediaStore rather than into app-private storage, because the point of pulling a track
 * off the server is to *have* it: a file under `filesDir` is invisible to every other player on
 * the phone and disappears when the app is uninstalled. A MediaStore insert puts it in `Music/`
 * where it belongs, needs no storage permission (the app owns what it creates), and — because the
 * app owns it — is also the one case where deleting it later needs no system prompt.
 *
 * `IS_PENDING` is set for the duration of the write on API 29+, so nothing scans or plays a
 * half-written file.
 */
@Singleton
class MediaStoreWriter @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * Streams [source] into a new track under `Music/<artist>/<album>/`.
     *
     * Any partial row is removed on failure rather than left behind as an unplayable entry in the
     * user's library.
     *
     * [expectedHash] is verified as the bytes go past. A transfer that arrived corrupted but kept
     * its name would be indistinguishable from the real thing afterwards.
     *
     * The failure cases are told apart because they have different causes and different fixes. A
     * transfer that delivered *nothing* is a broken pipe — the sender never connected, or the
     * session went away — and reporting that as corruption sent a real relay bug looking for a
     * damaged file that did not exist.
     */
    suspend fun write(
        source: InputStream,
        title: String,
        artist: String,
        album: String?,
        extension: String,
        expectedHash: String
    ): WriteResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val relative = buildString {
            append(Environment.DIRECTORY_MUSIC)
            append('/')
            append(sanitize(artist))
            append('/')
            append(sanitize(album ?: "Unknown Album"))
        }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${sanitize(title)}.$extension")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, relative)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val uri = runCatching {
            resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return@withContext WriteResult.Failed("could not create the file")

        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        // The cause is kept, not just the fact of failure. Reading from [source] happens inside
        // this block, so a transfer that dies mid-stream surfaces here as a write failure — and
        // "the write itself failed" with no reason cannot tell a full disk from a dropped
        // connection.
        val failure = runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = source.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    bytes += read
                }
            } ?: error("could not open the new file for writing")
        }.exceptionOrNull()

        // Roll the row back before reporting anything. A pending entry left behind is invisible
        // but still occupies the library, and a mismatched one would be worse: silently wrong
        // audio under the right name.
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        val outcome = when {
            failure != null -> WriteResult.Failed(
                "${failure::class.simpleName}: ${failure.message ?: "no detail"} " +
                    "(after $bytes bytes)"
            )
            bytes == 0L -> WriteResult.Empty
            actual != expectedHash -> WriteResult.HashMismatch
            else -> null
        }
        if (outcome != null) {
            runCatching { resolver.delete(uri, null, null) }
            return@withContext outcome
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            runCatching { resolver.update(uri, done, null, null) }
        }
        WriteResult.Written(uri)
    }

    /** Path segments come from tags, so they get the same treatment the server gives them. */
    private fun sanitize(value: String): String {
        val cleaned = value
            .map { char ->
                if (char.isISOControl() || char in "/\\:*?\"<>|") ' ' else char
            }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trimEnd('.', ' ')
        return cleaned.take(MAX_SEGMENT).ifBlank { "Unknown" }
    }

    private companion object {
        const val MAX_SEGMENT = 100
    }
}

/** What became of a fetched track. */
sealed interface WriteResult {
    data class Written(val uri: Uri) : WriteResult

    /**
     * The transfer delivered no bytes at all.
     *
     * Not corruption. Nothing arrived, which means the other end never sent — a peer that could
     * not be reached, or a relay session that was torn down before the sender connected.
     */
    data object Empty : WriteResult

    /** Bytes arrived, but they are not the file that was asked for. */
    data object HashMismatch : WriteResult

    data class Failed(val reason: String) : WriteResult
}
