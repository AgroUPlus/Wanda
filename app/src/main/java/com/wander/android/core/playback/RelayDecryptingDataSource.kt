package com.wander.android.core.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.wander.android.core.security.AudioStreamCipher
import com.wander.android.core.security.AudioStreamKeys
import com.wander.android.core.security.DecryptingRelayStream
import com.wander.android.core.security.IdentityKeyManager
import java.io.IOException
import java.io.InputStream
import android.net.Uri

/**
 * Decrypts a relayed stream on its way into the player.
 *
 * Sits directly on the network source, *below* the cache, for two reasons. Ciphertext must never
 * reach the cache — the key is per track and gone within the minute, so a cached encrypted file
 * could never be played again. And plaintext must not reach it either: this is somebody else's
 * track, borrowed for one playback, and writing it to disk would quietly turn a relay into a copy.
 * The relay spec is marked uncacheable for that second reason; this class's placement handles the
 * first.
 *
 * A stream is decrypted only when the response says it is encrypted. Anything else — every
 * ordinary HTTP source the player opens — is passed through untouched, byte for byte.
 */
@UnstableApi
internal class RelayDecryptingDataSource(
    private val upstream: DataSource,
    private val identityKeyManager: IdentityKeyManager
) : DataSource {

    private var plaintext: InputStream? = null
    private var openedUri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        upstream.open(dataSpec)
        openedUri = upstream.uri

        val sealedKey = upstream.responseHeaders[SEALED_KEY_HEADER]?.firstOrNull()?.trim()
        if (sealedKey.isNullOrEmpty()) return lengthOf(dataSpec)

        val sessionId = dataSpec.uri.relaySessionId()
            ?: throw IOException("An encrypted relay stream arrived at a URL with no session")

        // Opened with this device's identity key. A stream sealed to anyone else fails here rather
        // than producing noise the player would try to decode.
        val roomKey = runCatching {
            AudioStreamKeys.decodeRoomKey(identityKeyManager.openNote(sealedKey))
        }.getOrElse { throw IOException("Could not open the relay's room key", it) }

        plaintext = DecryptingRelayStream(
            DataSourceInputStream(upstream, dataSpec),
            AudioStreamCipher(AudioStreamKeys.derive(roomKey, sessionId))
        )
        // Encryption adds framing, so the byte count the server advertised is not the audio's.
        // Unknown is honest, and a relayed stream is not seekable anyway.
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val stream = plaintext ?: return upstream.read(buffer, offset, length)
        val read = stream.read(buffer, offset, length)
        return if (read == -1) C.RESULT_END_OF_INPUT else read
    }

    override fun close() {
        try {
            plaintext?.close()
        } finally {
            plaintext = null
            openedUri = null
            upstream.close()
        }
    }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = openedUri ?: upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    private fun lengthOf(dataSpec: DataSpec): Long =
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else C.LENGTH_UNSET.toLong()

    @UnstableApi
    class Factory(
        private val upstream: DataSource.Factory,
        private val identityKeyManager: IdentityKeyManager
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            RelayDecryptingDataSource(upstream.createDataSource(), identityKeyManager)
    }

    private companion object {
        const val SEALED_KEY_HEADER = "x-agro-sealed-key"
    }
}

/**
 * The session id out of a relay URL, `…/api/v1/relay/{sessionId}/receive`.
 *
 * It is half of what derives the stream's key, so it is read from the URL actually fetched rather
 * than remembered from when the session was opened — the two cannot disagree that way.
 */
internal fun Uri.relaySessionId(): String? {
    val segments = pathSegments ?: return null
    val relayAt = segments.indexOf("relay")
    if (relayAt < 0 || relayAt + 1 >= segments.size) return null
    return segments[relayAt + 1].takeIf { it.isNotBlank() }
}
