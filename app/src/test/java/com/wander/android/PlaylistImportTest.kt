package com.wander.android

import com.wander.android.core.security.SecureStorage
import com.wander.android.data.importer.DeezerPlaylistParser
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.SpotifyPlaylistParser
import com.wander.android.data.importer.TextPlaylistParser
import com.wander.android.data.importer.YouTubePlaylistParser
import com.wander.android.data.sources.deezer.DeezerAccountManager
import io.ktor.client.HttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistImportTest {

    /**
     * [SecureStorage] has no other constructor reachable from a plain JVM unit test —
     * `create(context)` needs a real Android Keystore — and nothing under test here reads a
     * preference, so a bare in-memory map is enough. Same shape as [LyricsParserTest]'s own.
     */
    private class FakeSharedPreferences : android.content.SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String?, defValue: String?) = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) =
            values[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun contains(key: String?) = values.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(
            listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        private inner class FakeEditor : android.content.SharedPreferences.Editor {
            override fun putString(key: String?, value: String?) = apply { values[key!!] = value }
            override fun putStringSet(key: String?, v: MutableSet<String>?) = apply { values[key!!] = v }
            override fun putInt(key: String?, value: Int) = apply { values[key!!] = value }
            override fun putLong(key: String?, value: Long) = apply { values[key!!] = value }
            override fun putFloat(key: String?, value: Float) = apply { values[key!!] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { values[key!!] = value }
            override fun remove(key: String?) = apply { values.remove(key) }
            override fun clear() = apply { values.clear() }
            override fun commit() = true
            override fun apply() = Unit
        }
    }

    private fun fakeDeezerAccountManager() = DeezerAccountManager(SecureStorage(FakeSharedPreferences()))

    @Test
    fun platformDetection() {
        assertEquals(
            PlatformType.SPOTIFY,
            PlatformType.detect("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=123")
        )
        assertEquals(
            PlatformType.DEEZER,
            PlatformType.detect("https://www.deezer.com/en/playlist/908622995")
        )
        assertEquals(
            PlatformType.YOUTUBE,
            PlatformType.detect("https://music.youtube.com/playlist?list=PL4fGSIqsQ87508gZ5r0Lq")
        )
        assertEquals(
            PlatformType.APPLE_MUSIC,
            PlatformType.detect("https://music.apple.com/us/playlist/todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb")
        )
        assertEquals(
            PlatformType.PLAIN_TEXT,
            PlatformType.detect("Daft Punk - One More Time\nJustice - Genesis")
        )
    }

    @Test
    fun spotifyPlaylistIdExtraction() {
        val parser = SpotifyPlaylistParser(HttpClient())
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            parser.extractPlaylistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc123xyz")
        )
        assertEquals(
            "7ABCxyz99",
            parser.extractPlaylistId("spotify:playlist:7ABCxyz99")
        )
    }

    @Test
    fun deezerPlaylistIdExtraction() {
        val parser = DeezerPlaylistParser(HttpClient(), fakeDeezerAccountManager())
        assertEquals(
            "908622995",
            parser.extractPlaylistId("https://www.deezer.com/fr/playlist/908622995")
        )
        assertEquals(
            "123456",
            parser.extractPlaylistId("https://deezer.com/playlist/123456")
        )
    }

    @Test
    fun youTubePlaylistIdExtraction() {
        val regex = Regex("""[?&]list=([a-zA-Z0-9_-]+)""")
        val url = "https://music.youtube.com/playlist?list=PL4fGSIqsQ87508gZ5r0Lq&si=abc"
        assertEquals(
            "PL4fGSIqsQ87508gZ5r0Lq",
            regex.find(url)?.groupValues?.getOrNull(1)
        )
    }

    @Test
    fun textPlaylistParsing() {
        val parser = TextPlaylistParser()
        val text = """
            # My Favorite Tracks
            Daft Punk - One More Time
            Justice – D.A.N.C.E.
            Around The World by Daft Punk
            Harder, Better, Faster, Stronger
        """.trimIndent()

        val result = parser.parse(text)
        assertTrue(result.isSuccess)
        val playlist = result.getOrThrow()
        assertEquals(4, playlist.tracks.size)

        assertEquals("Daft Punk", playlist.tracks[0].artist)
        assertEquals("One More Time", playlist.tracks[0].title)

        assertEquals("Justice", playlist.tracks[1].artist)
        assertEquals("D.A.N.C.E.", playlist.tracks[1].title)

        assertEquals("Daft Punk", playlist.tracks[2].artist)
        assertEquals("Around The World", playlist.tracks[2].title)

        assertEquals("Harder, Better, Faster, Stronger", playlist.tracks[3].title)
    }
}
