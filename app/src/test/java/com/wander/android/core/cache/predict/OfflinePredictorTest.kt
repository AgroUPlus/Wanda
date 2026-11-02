package com.wander.android.core.cache.predict

import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class OfflinePredictorTest {

    private val zone = ZoneOffset.UTC
    private val predictor = OfflinePredictor(zone)

    /** A Wednesday, 22:00 UTC: the overnight download run. */
    private val now = ZonedDateTime.of(2026, 11, 4, 22, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val day = 86_400_000L
    private val hour = 3_600_000L

    private fun track(
        id: String,
        source: SourceType = SourceType.NAVIDROME,
        isLibrary: Boolean = true,
        addedAt: Long = now - 365 * day,
        isDownloaded: Boolean = false,
        localFilePath: String? = null,
        isLive: Boolean = false
    ) = TrackEntity(
        id = id, sourceTrackId = id, source = source, title = id, artist = "a", album = null, albumId = null,
        artistId = null, durationMs = 180_000L, artworkUrl = null, streamUri = null, trackNumber = null,
        discNumber = null, year = null, genre = null, bitRateKbps = null, format = null,
        isLive = isLive, isDownloaded = isDownloaded, isLibrary = isLibrary, localFilePath = localFilePath,
        addedTimestamp = addedAt
    )

    private fun nav(name: String) = "${SourceType.NAVIDROME.idPrefix}$name"

    @Test
    fun `recent plays outrank a larger number of old ones`() {
        val fresh = nav("fresh")
        val stale = nav("stale")
        val plays = List(3) { PastPlay(fresh, now - (it + 1) * day) } +
            List(6) { PastPlay(stale, now - (90 + it) * day) }

        assertEquals(listOf(fresh, stale), predictor.rank(listOf(track(fresh), track(stale)), plays, now, 10))
    }

    @Test
    fun `plays at the listener's usual hour and coming weekday count for more`() {
        val commute = nav("commute")
        val other = nav("other")
        // Most listening is at 08:00; `commute` is played then, on Thursdays (tomorrow).
        val thursdayMorning = ZonedDateTime.of(2026, 10, 29, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val sundayNight = ZonedDateTime.of(2026, 11, 1, 23, 0, 0, 0, zone).toInstant().toEpochMilli()
        val filler = List(5) { PastPlay(nav("filler$it"), thursdayMorning + it * hour / 10) }
        val plays = filler + PastPlay(commute, thursdayMorning) + PastPlay(other, sundayNight)

        val ranked = predictor.rank(listOf(track(commute), track(other)), plays, now, 10)
        assertEquals(listOf(commute, other), ranked)
    }

    @Test
    fun `nothing already on the device or borrowed is proposed`() {
        val downloaded = nav("dl")
        val local = "${SourceType.LOCAL.idPrefix}file"
        val live = nav("live")
        val oneShot = "relay:session-1"
        val wanted = nav("wanted")
        val tracks = listOf(
            track(downloaded, isDownloaded = true),
            track(local, source = SourceType.LOCAL, localFilePath = "/music/a.flac"),
            track(live, isLive = true),
            track(oneShot),
            track(wanted)
        )
        val plays = tracks.map { PastPlay(it.id, now - day) }

        assertEquals(listOf(wanted), predictor.rank(tracks, plays, now, 10))
    }

    @Test
    fun `a recently added library track fills room listening leaves`() {
        val played = nav("played")
        val added = nav("added")
        val untouched = nav("untouched")
        val tracks = listOf(track(played), track(added, addedAt = now - 2 * day), track(untouched))

        val ranked = predictor.rank(tracks, listOf(PastPlay(played, now - day)), now, 10)
        assertEquals(listOf(played, added), ranked)
    }

    @Test
    fun `a first sync does not make the whole library look newly added`() {
        val tracks = List(30) { track(nav("t$it"), addedAt = now - day) }
        assertEquals(emptyList<String>(), predictor.rank(tracks, emptyList(), now, 10))
    }

    @Test
    fun `the limit is respected and ties break by id`() {
        val tracks = listOf("c", "a", "b").map { track(nav(it)) }
        val plays = tracks.map { PastPlay(it.id, now - day) }
        assertEquals(listOf(nav("a"), nav("b")), predictor.rank(tracks, plays, now, 2))
    }
}
