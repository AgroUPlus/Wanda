package com.wander.android.core.playback

import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import com.wander.android.data.model.isOneShotTrackId
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles container mismatches (e.g. progressive vs HLS) and live edge rejoin budgeting.
 */
internal class LivePlaybackRetryHandler(
    private val liveRejoins: LiveRejoinBudget = LiveRejoinBudget()
) {
    /**
     * Ids already retried as a livestream, so a genuinely unplayable file cannot loop forever.
     */
    private val retriedAsLive = Collections.newSetFromMap(
        ConcurrentHashMap<String, Boolean>()
    )

    /**
     * Re-prepares the current item after it failed to parse as a media container (progressive vs HLS).
     */
    fun retryContainerMismatch(ctrl: MediaController, error: PlaybackException): Boolean {
        if (error.errorCode !in CONTAINER_PARSE_ERRORS) return false
        val item = runCatching { ctrl.currentMediaItem }.getOrNull() ?: return false
        val id = item.mediaId.takeIf { it.isNotBlank() } ?: return false

        // Never for a stream that can only be fetched once.
        if (isOneShotTrackId(id)) return false

        val uri = item.localConfiguration?.uri
        if (uri != null && uri.scheme != WANDA_SCHEME) return false

        if (!retriedAsLive.add(id)) return false

        val index = runCatching { ctrl.currentMediaItemIndex }.getOrNull() ?: return false
        val isHls = uri?.toString()?.endsWith(LIVE_SUFFIX) == true ||
            item.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8

        val newUri = if (isHls) {
            Uri.parse("$WANDA_SCHEME://track/${Uri.encode(id)}")
        } else {
            Uri.parse("$WANDA_SCHEME://track/${Uri.encode(id)}$LIVE_SUFFIX")
        }

        val builder = item.buildUpon().setUri(newUri)
        if (isHls) {
            builder.setMimeType(null)
        } else {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            builder.setLiveConfiguration(liveConfiguration())
        }

        ctrl.replaceMediaItem(index, builder.build())
        ctrl.prepare()
        ctrl.play()
        return true
    }

    /**
     * Puts a livestream back on the air after a load error, instead of reporting one.
     */
    fun rejoinLiveEdge(ctrl: MediaController, isCurrentTrackLive: Boolean): Boolean {
        if (!isCurrentTrackLive) return false
        val id = runCatching { ctrl.currentMediaItem?.mediaId }.getOrNull() ?: return false
        if (!liveRejoins.allow(id)) return false

        ctrl.seekToDefaultPosition()
        ctrl.prepare()
        ctrl.play()
        return true
    }

    companion object {
        val CONTAINER_PARSE_ERRORS = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
        )
    }
}
