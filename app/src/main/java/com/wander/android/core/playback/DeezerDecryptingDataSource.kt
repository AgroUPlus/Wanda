package com.wander.android.core.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.wander.android.core.security.DeezerStreamCipher
import java.io.IOException

/**
 * Decrypts a Deezer audio stream on its way from the CDN into the player.
 *
 * Sits in the network pipeline below the cache, so plaintext rather than ciphertext
 * reaches the cache and extractors.
 *
 * If a request carries [DEEZER_TRACK_ID_HEADER], chunks are decrypted using Blowfish CBC
 * when `chunkIndex % 3 == 0`. All other streams pass through unmodified.
 */
@UnstableApi
internal class DeezerDecryptingDataSource(
    private val upstream: DataSource
) : DataSource {

    private var cipher: DeezerStreamCipher? = null
    private var currentChunkIndex: Long = 0
    private val chunkBuffer = ByteArray(DeezerStreamCipher.CHUNK_SIZE)
    private var chunkBufferLimit = 0
    private var chunkBufferPos = 0

    override fun open(dataSpec: DataSpec): Long {
        val trackId = dataSpec.httpRequestHeaders[DEEZER_TRACK_ID_HEADER]?.trim()
        if (trackId.isNullOrEmpty()) {
            cipher = null
            return upstream.open(dataSpec)
        }

        val streamCipher = DeezerStreamCipher(trackId)
        cipher = streamCipher

        val originalPosition = dataSpec.position
        val skipInChunk = (originalPosition % DeezerStreamCipher.CHUNK_SIZE).toInt()
        val alignedPosition = originalPosition - skipInChunk
        currentChunkIndex = alignedPosition / DeezerStreamCipher.CHUNK_SIZE

        val alignedSpec = if (skipInChunk > 0) {
            dataSpec.buildUpon()
                .setPosition(alignedPosition)
                .setLength(if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length + skipInChunk else C.LENGTH_UNSET.toLong())
                .build()
        } else {
            dataSpec
        }

        val resolvedLength = upstream.open(alignedSpec)

        chunkBufferPos = 0
        chunkBufferLimit = 0

        if (skipInChunk > 0) {
            var discarded = 0
            while (discarded < skipInChunk) {
                if (!fillChunkBuffer(streamCipher)) {
                    throw IOException("Premature end of stream while aligning to position $originalPosition")
                }
                val toDiscard = minOf(skipInChunk - discarded, chunkBufferLimit - chunkBufferPos)
                chunkBufferPos += toDiscard
                discarded += toDiscard
            }
        }

        return if (resolvedLength != C.LENGTH_UNSET.toLong()) {
            maxOf(0L, resolvedLength - skipInChunk)
        } else {
            C.LENGTH_UNSET.toLong()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val activeCipher = cipher ?: return upstream.read(buffer, offset, length)

        var totalRead = 0
        while (totalRead < length) {
            if (chunkBufferPos >= chunkBufferLimit) {
                if (!fillChunkBuffer(activeCipher)) {
                    return if (totalRead > 0) totalRead else C.RESULT_END_OF_INPUT
                }
            }

            val available = chunkBufferLimit - chunkBufferPos
            val toCopy = minOf(length - totalRead, available)
            System.arraycopy(chunkBuffer, chunkBufferPos, buffer, offset + totalRead, toCopy)
            chunkBufferPos += toCopy
            totalRead += toCopy
        }

        return totalRead
    }

    private fun fillChunkBuffer(activeCipher: DeezerStreamCipher): Boolean {
        var bytesRead = 0
        while (bytesRead < DeezerStreamCipher.CHUNK_SIZE) {
            val count = upstream.read(chunkBuffer, bytesRead, DeezerStreamCipher.CHUNK_SIZE - bytesRead)
            if (count == C.RESULT_END_OF_INPUT) break
            bytesRead += count
        }

        if (bytesRead <= 0) {
            chunkBufferLimit = 0
            chunkBufferPos = 0
            return false
        }

        chunkBufferLimit = bytesRead
        chunkBufferPos = 0

        if (DeezerStreamCipher.isChunkEncrypted(currentChunkIndex, bytesRead)) {
            val decrypted = activeCipher.decryptChunk(chunkBuffer, 0, bytesRead)
            System.arraycopy(decrypted, 0, chunkBuffer, 0, bytesRead)
        }

        currentChunkIndex++
        return true
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        try {
            upstream.close()
        } finally {
            cipher = null
            chunkBufferPos = 0
            chunkBufferLimit = 0
            currentChunkIndex = 0
        }
    }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    class Factory(
        private val upstream: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            DeezerDecryptingDataSource(upstream.createDataSource())
    }

    companion object {
        const val DEEZER_TRACK_ID_HEADER = "X-Deezer-Track-Id"
    }
}
