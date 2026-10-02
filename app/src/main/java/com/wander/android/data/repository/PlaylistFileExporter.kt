package com.wander.android.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.wander.android.data.importer.M3uWriter
import com.wander.android.data.model.UnifiedTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A playlist as an `.m3u8` file, written by [M3uWriter]: saved where the person chooses, or put in
 * the cache to hand to the share sheet.
 *
 * A file has no length limit, so it is also how a playlist too long for a link is shared.
 */
@Singleton
class PlaylistFileExporter @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Writes the playlist to [target], a document the person picked to create. */
    suspend fun saveTo(target: Uri, name: String, tracks: List<UnifiedTrack>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val stream = context.contentResolver.openOutputStream(target, "wt")
                    ?: throw IOException("Couldn't open the chosen file for writing")
                stream.bufferedWriter().use { it.write(M3uWriter.write(name, tracks)) }
            }
        }

    /**
     * Writes the playlist to the cache and returns a URI the share sheet can be granted. The folder
     * is cleared first, so it only ever holds the latest export.
     */
    suspend fun shareableFile(name: String, tracks: List<UnifiedTrack>): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, DIRECTORY)
                dir.listFiles()?.forEach { it.delete() }
                if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Couldn't create the export folder")
                val file = File(dir, M3uWriter.fileName(name))
                file.writeText(M3uWriter.write(name, tracks))
                FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
            }
        }

    companion object {
        const val MIME = "audio/x-mpegurl"
        private const val DIRECTORY = "playlists"
        private const val AUTHORITY_SUFFIX = ".playlistshare"
    }
}
