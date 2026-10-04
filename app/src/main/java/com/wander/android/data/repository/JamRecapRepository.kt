package com.wander.android.data.repository

import com.wander.android.core.database.dao.JamRecapDao
import com.wander.android.core.database.entity.JamRecapEntity
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.RawImportPlaylist
import com.wander.android.data.importer.RawImportTrack
import com.wander.android.data.sources.agro.AgroJamRecapApi
import com.wander.android.data.sources.agro.JamRecap
import com.wander.android.data.sources.agro.JamRecapJson
import com.wander.android.data.sources.agro.StoredJamRecap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recaps of jams this account has left: fetched from Agro, kept in Room, read from Room.
 *
 * A recap arrives when the server writes it — a `JAM_RECAP` frame — and is re-read on every full
 * resync, so one written while this device was offline still turns up.
 */
@Singleton
internal class JamRecapRepository @Inject constructor(
    private val api: AgroJamRecapApi,
    private val dao: JamRecapDao,
    private val importer: PlaylistImportRepository
) {
    val recaps: Flow<List<StoredJamRecap>> = dao.observeAll().map { rows -> rows.map { it.toStored() } }

    suspend fun refresh(): Result<Unit> = api.recaps().mapCatching { fresh ->
        dao.replaceAll(
            fresh.map { JamRecapEntity(it.id, it.createdAt, JamRecapJson.encodeToString(JamRecap.serializer(), it.recap)) }
        )
    }

    /**
     * Forgets a recap here and on the server.
     *
     * Server first: removed only locally, the next refresh would bring it straight back. A recap
     * the server no longer had (dismissed on another device) is gone either way, so that is success.
     */
    suspend fun dismiss(id: String): Result<Unit> = api.dismiss(id).mapCatching { dao.delete(id) }

    /**
     * Turns a recap into a universal playlist named [title].
     *
     * Through the importer, because a recap names tracks by title and artist as the room heard them,
     * which is exactly what an imported playlist is: saved at once as placeholders, then matched to
     * whatever this device can play by `PlaylistImportWorker`.
     */
    suspend fun saveAsPlaylist(recap: JamRecap, title: String): Result<String> = importer.savePending(
        RawImportPlaylist(
            platform = PlatformType.PLAIN_TEXT,
            title = title,
            coverUrl = recap.tracks.firstNotNullOfOrNull { it.artworkUrl },
            tracks = recap.tracks.map { RawImportTrack(title = it.title, artist = it.artist, durationMs = it.durationMs) }
        )
    )

    /** On sign-out: another account's rooms are not this one's to remember. */
    suspend fun clear() = dao.clear()
}

private fun JamRecapEntity.toStored() = StoredJamRecap(
    id = id,
    createdAt = createdAt,
    recap = JamRecapJson.decodeFromString(JamRecap.serializer(), payloadJson)
)
