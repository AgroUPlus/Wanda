package com.wander.android.data.repository

import android.net.Uri
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.ytmusic.youTubeVideoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a link someone sent you into a track this app can play.
 *
 * Only YouTube and YouTube Music links resolve today — they are the ones that name a single
 * recording in a way any device can look up. A Navidrome share link points at a specific server's
 * public page, and opening one on a device signed into a *different* server would resolve to the
 * wrong file or nothing at all, so those are left to the browser rather than guessed at.
 */
@Singleton
class LinkRepository @Inject constructor(
    private val musicRepository: MusicRepository,
    private val shareLinkRewriter: ShareLinkRewriter
) {

    /** Whether [uri] is a link this app can do something with. Cheap, no network. */
    fun canOpen(uri: Uri): Boolean = youTubeVideoId(target(uri)) != null

    /**
     * The link itself, or what it stands for. A link shared through the user's own domain wraps
     * one of these, so it has to be unwrapped before anything can be made of it — otherwise your
     * own shared links were the one kind Wanda could not open.
     */
    private fun target(uri: Uri): Uri = shareLinkRewriter.unwrap(uri) ?: uri

    suspend fun resolve(uri: Uri): Result<UnifiedTrack> = withContext(Dispatchers.IO) {
        val videoId = youTubeVideoId(target(uri))
            ?: return@withContext Result.failure(
                IllegalArgumentException("That link doesn't point at a track Wanda can play.")
            )

        val source = musicRepository.sources.firstOrNull { it.sourceType == SourceType.YTMUSIC }
            ?: return@withContext Result.failure(
                IllegalStateException("YouTube Music is unavailable.")
            )

        // Deliberately not gated on being signed in: YouTube Music search and playback both work
        // signed out, so a link a friend sent should open whether or not an account is connected.
        source.getTrack("ytm:$videoId").mapCatching { track ->
            track ?: throw IOException("YouTube Music has nothing playable behind that link.")
        }
    }
}
