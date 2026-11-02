package com.wander.android.data.sources.local.lyrics

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.wander.android.data.model.LyricsData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A local file's own lyrics: those tagged into it, and a `.lrc` file of the same name beside it.
 *
 * Timed lyrics win over untimed ones wherever they come from, so a hand-made `.lrc` next to a file
 * whose tag only holds plain text still scrolls in time. Between two of a kind the tag wins.
 *
 * The sidecar is a best effort. From Android 11 an app holding only the audio permission cannot
 * see other apps' non-media files, which an `.lrc` is, so on most devices it is only found when
 * it was indexed into MediaStore and is readable to us; when it is not, it is simply not there.
 */
@Singleton
class LocalLyricsReader @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /** Fails only when the audio file itself could not be opened. */
    suspend fun read(trackId: String, audio: Uri): Result<LyricsData?> = withContext(Dispatchers.IO) {
        val embedded = try {
            readEmbedded(audio)
        } catch (e: IOException) {
            return@withContext Result.failure(e)
        } catch (e: SecurityException) {
            return@withContext Result.failure(e)
        }
        if (embedded != null && embedded.isSynced) {
            return@withContext Result.success(embedded.toLyricsData(trackId, SOURCE_EMBEDDED))
        }
        val sidecar = FileLyrics.ofText(readSidecar(audio))?.toLyricsData(trackId, SOURCE_SIDECAR)
        Result.success(
            sidecar?.takeIf { it.isSynced || embedded == null } ?: embedded?.toLyricsData(trackId, SOURCE_EMBEDDED)
        )
    }

    private fun readEmbedded(audio: Uri): FileLyrics? {
        val descriptor = context.contentResolver.openFileDescriptor(audio, "r")
            ?: throw IOException("No descriptor for a local track")
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            EmbeddedLyrics.read(ChannelTagBytes(stream.channel))
        }
    }

    private fun readSidecar(audio: Uri): String? {
        return try {
            val location = locate(audio) ?: return null
            val name = location.displayName.substringBeforeLast('.') + ".lrc"
            // A plain file read works below Android 10, and wherever broad storage access is held.
            location.dataPath?.let { File(File(it).parentFile, name) }
                ?.takeIf { it.isFile && it.canRead() && it.length() <= MAX_LRC_BYTES }
                ?.readText()
                ?: findInMediaStore(audio, location.relativePath, name)?.let(::readUri)
        } catch (e: IOException) {
            Log.i(TAG, "Sidecar lyrics unreadable: ${e.javaClass.simpleName}")
            null
        } catch (e: SecurityException) {
            Log.i(TAG, "Sidecar lyrics not accessible: ${e.javaClass.simpleName}")
            null
        }
    }

    private class Location(val displayName: String, val relativePath: String?, val dataPath: String?)

    @Suppress("DEPRECATION") // DATA is the only way to the folder below Android 10.
    private fun locate(audio: Uri): Location? {
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = buildList {
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATA)
            if (hasRelativePath) add(MediaStore.MediaColumns.RELATIVE_PATH)
        }.toTypedArray()
        return context.contentResolver.query(audio, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val displayName = c.getString(0)?.takeIf { it.isNotBlank() } ?: return@use null
            Location(
                displayName = displayName,
                dataPath = c.getString(1)?.takeIf { it.isNotBlank() },
                relativePath = if (hasRelativePath) c.getString(2) else null
            )
        }
    }

    private fun findInMediaStore(audio: Uri, relativePath: String?, name: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || relativePath == null) return null
        val files = MediaStore.Files.getContentUri(MediaStore.getVolumeName(audio))
        return context.contentResolver.query(
            files,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(relativePath, name),
            null
        )?.use { c ->
            if (!c.moveToFirst() || c.getLong(1) > MAX_LRC_BYTES) null else ContentUris.withAppendedId(files, c.getLong(0))
        }
    }

    private fun readUri(uri: Uri): String? =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }

    private companion object {
        const val TAG = "LocalLyricsReader"
        const val SOURCE_EMBEDDED = "Embedded"
        const val SOURCE_SIDECAR = "LRC file"
        const val MAX_LRC_BYTES = 1024L * 1024L
    }
}
