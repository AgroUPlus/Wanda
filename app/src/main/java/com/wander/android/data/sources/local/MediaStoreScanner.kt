package com.wander.android.data.sources.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")

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
    @param:ApplicationContext private val context: Context
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
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            if (sinceSeconds > 0L) append(" AND ${MediaStore.Audio.Media.DATE_MODIFIED} > ?")
        }
        val args = if (sinceSeconds > 0L) arrayOf(sinceSeconds.toString()) else null

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
                    year = c.getInt(yearCol).takeIf { it > 0 },
                    format = c.getString(mimeCol),
                    isDownloaded = true,
                    isCached = true
                )
            }
        }

        MediaStoreScan(tracks, watermark)
    }
}
