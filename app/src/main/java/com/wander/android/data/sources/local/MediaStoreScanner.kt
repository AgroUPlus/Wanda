package com.wander.android.data.sources.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.core.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")

/**
 * Extra fields the library-sync feature needs and nothing else does.
 *
 * `UnifiedTrack.extraData` rather than new fields on the model: the model is serialised into every
 * `MediaItem` in the playback queue, so a field added for one feature is carried by every track in
 * memory whether or not that feature is on.
 */
const val EXTRA_SIZE_BYTES = "sizeBytes"
const val EXTRA_EXTENSION = "extension"
const val EXTRA_ALBUM_ARTIST = "albumArtist"

/** SHA-256 of the file's bytes, filled in lazily by the hashing worker rather than at scan time. */
const val EXTRA_CONTENT_HASH = "contentHash"

data class MediaStoreScan(
    val tracks: List<UnifiedTrack>,
    /** Highest `DATE_MODIFIED` seen, in seconds — the watermark for the next incremental scan. */
    val watermarkSeconds: Long
)

/**
 * Reads audio from MediaStore. Callers persist the result in Room and query Room afterwards;
 * this cursor is only walked when something on disk has actually changed.
 */
@Singleton
class MediaStoreScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    /**
     * @param sinceSeconds only return items modified after this `DATE_MODIFIED`. Pass 0 for a
     *   full scan.
     */
    suspend fun scan(sinceSeconds: Long = 0L): MediaStoreScan = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            // For library sync: the server files by album artist and disc, and needs the size to
            // plan a transfer. DISPLAY_NAME supplies the extension for the archived filename —
            // deriving one from the MIME type guesses wrong on the m4a/mp4 pair in particular.
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Added in API 30. Below that they simply are not available, and the server falls back
            // to the track artist and an absent disc number.
            arrayOf(
                MediaStore.Audio.Media.ALBUM_ARTIST,
                MediaStore.Audio.Media.DISC_NUMBER
            )
        } else {
            emptyArray()
        }
        // A chosen folder narrows the scan; without one the whole volume is fair game, which is
        // the default and what most people want. `RELATIVE_PATH` is API 29+, so on older devices
        // the choice simply cannot be honoured and the row that offers it is not shown.
        val folder = secureStorage.localScanFolder
            ?.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q }
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            if (sinceSeconds > 0L) append(" AND ${MediaStore.Audio.Media.DATE_MODIFIED} > ?")
            // `LIKE folder%` rather than `= folder`, so sub-folders of the chosen one are
            // included. Picking "Music" and getting only the loose files sitting directly in it
            // would be a folder picker that does not pick a folder.
            if (folder != null) append(" AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?")
        }
        val args = buildList {
            if (sinceSeconds > 0L) add(sinceSeconds.toString())
            if (folder != null) add("$folder%")
        }.takeIf { it.isNotEmpty() }?.toTypedArray()

        val tracks = ArrayList<UnifiedTrack>()
        var watermark = sinceSeconds

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val modifiedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            // getColumnIndex, not ...OrThrow: these are absent below API 30 by design.
            val albumArtistCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            val discCol = c.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                watermark = maxOf(watermark, c.getLong(modifiedCol))

                tracks += UnifiedTrack(
                    id = "local:$id",
                    source = SourceType.LOCAL,
                    title = c.getString(titleCol) ?: "Unknown Title",
                    artist = c.getString(artistCol) ?: "Unknown Artist",
                    album = c.getString(albumCol),
                    albumId = "local:album:$albumId",
                    durationMs = c.getLong(durCol),
                    artworkUrl = ContentUris.withAppendedId(ALBUM_ART_URI, albumId).toString(),
                    streamUri = ContentUris
                        .withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        .toString(),
                    trackNumber = c.getInt(trackCol).takeIf { it > 0 },
                    discNumber = discCol.takeIf { it >= 0 }
                        ?.let { c.getInt(it) }
                        ?.takeIf { it > 0 },
                    year = c.getInt(yearCol).takeIf { it > 0 },
                    format = c.getString(mimeCol),
                    isDownloaded = true,
                    isCached = true,
                    // Carried in extraData rather than as first-class fields: they exist for
                    // library sync and nothing in playback or the UI reads them, so widening
                    // UnifiedTrack for them would put them in every MediaItem in the queue.
                    extraData = buildMap {
                        c.getLong(sizeCol).takeIf { it > 0 }?.let { put(EXTRA_SIZE_BYTES, it.toString()) }
                        c.getString(nameCol)
                            ?.substringAfterLast('.', "")
                            ?.takeIf { it.isNotBlank() && it.length <= 8 }
                            ?.let { put(EXTRA_EXTENSION, it.lowercase()) }
                        albumArtistCol.takeIf { it >= 0 }
                            ?.let { c.getString(it) }
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put(EXTRA_ALBUM_ARTIST, it) }
                    }
                )
            }
        }

        MediaStoreScan(tracks, watermark)
    }
}
