package com.wander.android

import com.wander.android.data.model.SearchKind
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.SourceCapabilities
import com.wander.android.data.sources.StreamInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every backend gets for free from [IMusicSource]. A source that overrides nothing must
 * answer "unsupported" or "empty", never invent a result, because the UI trusts the capability
 * flags and a default that fabricated data would show up as a feature that does not exist.
 */
class MusicSourceContractTest {
    private class BareSource(configured: Boolean = true) : IMusicSource {
        val configuredFlow = MutableStateFlow(configured)
        var searches = 0

        override val sourceType = SourceType.NAVIDROME
        override val displayName = "Bare"
        override val capabilities = SourceCapabilities()
        override val isConfigured: StateFlow<Boolean> = configuredFlow

        override suspend fun search(query: String): Result<List<UnifiedTrack>> {
            searches++
            return Result.success(emptyList())
        }

        override suspend fun getStreamInfo(trackId: String): Result<StreamInfo> =
            Result.failure(UnsupportedOperationException("no streams"))
    }

    @Test
    fun capabilitiesDefaultToNothingExceptHoldingTracks() {
        val none = SourceCapabilities()

        assertFalse(none.search)
        assertFalse(none.albums)
        assertFalse(none.artists)
        assertFalse(none.playlists)
        assertFalse(none.playlistWrite)
        assertFalse(none.likes)
        assertFalse(none.scrobble)
        assertFalse(none.radio)
        assertFalse(none.lyrics)
        assertFalse(none.recommendations)
        assertFalse(none.share)
        assertFalse(none.videoClips)
        assertTrue(none.holdsTracks)
    }

    @Test
    fun searchabilityAndStreamabilityFollowConfigurationByDefault() {
        val source = BareSource(configured = false)

        assertFalse(source.isSearchable.value)
        assertFalse(source.isStreamable.value)

        source.configuredFlow.value = true

        assertTrue(source.isSearchable.value)
        assertTrue(source.isStreamable.value)
    }

    @Test
    fun aKindOtherThanTracksIsEmptyAndNeverReachesTheBackend() = runTest {
        val source = BareSource()

        assertTrue(source.search("anything", SearchKind.VIDEOS).getOrThrow().isEmpty())
        assertTrue(source.search("anything", SearchKind.EPISODES).getOrThrow().isEmpty())
        assertEquals(0, source.searches)

        source.search("anything", SearchKind.TRACKS)

        assertEquals(1, source.searches)
    }

    @Test
    fun readsAreEmptyRatherThanInvented() = runTest {
        val source = BareSource()

        assertTrue(source.getLikedTracks().getOrThrow().isEmpty())
        assertTrue(source.getRecentTracks().getOrThrow().isEmpty())
        assertTrue(source.getAlbums().getOrThrow().isEmpty())
        assertTrue(source.getAlbumTracks("a").getOrThrow().isEmpty())
        assertTrue(source.getPlaylists().getOrThrow().isEmpty())
        assertTrue(source.getPlaylistTracks("p").getOrThrow().isEmpty())
        assertTrue(source.getRadio("seed").getOrThrow().isEmpty())
        assertTrue(source.getRecommendations().getOrThrow().isEmpty())
        assertEquals(null, source.getTrack("t").getOrThrow())
        assertEquals(null, source.getLyrics("t").getOrThrow())
        assertEquals(null, source.getPlaylist("p").getOrThrow())
    }

    @Test
    fun writesThatNeedASupportingBackendFailExplicitly() = runTest {
        val source = BareSource()

        val failures = listOf(
            source.createPlaylist("x", emptyList()),
            source.addToPlaylist("p", listOf("t")),
            source.deletePlaylist("p"),
            source.getVideoStreamInfo("t"),
            source.getArtist("a"),
            source.getArtistAlbumPage("b", null, "artist")
        )

        failures.forEach { result ->
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
        }
    }

    @Test
    fun likingAndScrobblingAreHarmlessNoOpsWhenUnsupported() = runTest {
        val source = BareSource()

        assertTrue(source.setLiked("t", true).isSuccess)
        assertTrue(source.scrobble("t").isSuccess)
    }
}
