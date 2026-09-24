package com.wander.android.core.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.AudioStreamCipher
import com.wander.android.core.security.AudioStreamKeys
import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.core.security.RelayStreamFraming
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Handles HTTP response body formatting for P2P audio streaming (plaintext and ChaCha20-Poly1305 encrypted).
 */
internal class P2PAudioResponseWriter(
    private val identityKeyManager: IdentityKeyManager
) {
    fun writeForbidden(output: OutputStream) {
        val body = "forbidden"
        output.write(
            ("HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\n" +
                "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                .toByteArray()
        )
        output.flush()
    }

    fun streamTrack(
        context: Context,
        track: TrackEntity?,
        request: String,
        queryUri: Uri,
        grantManager: P2PGrantManager,
        output: OutputStream
    ): Boolean {
        val (inputStream, totalLength) = openTrackStream(context, track) ?: return false

        try {
            inputStream.use { fileIn ->
                val mime = when (track?.format?.lowercase()) {
                    "flac" -> "audio/flac"
                    "opus", "webm" -> "audio/ogg"
                    "m4a", "mp4" -> "audio/mp4"
                    else -> "audio/mpeg"
                }
                val recipientKey = grantManager.sealingKeyFor(
                    grantManager.tokenOf(request),
                    grantManager.identityKeyOf(request)
                )
                val session = queryUri.getQueryParameter("session").orEmpty()
                val range = request.lineSequence().firstOrNull { it.startsWith("Range:", ignoreCase = true) }
                if (range != null) Log.w(TAG, "Ignoring a range request: ${range.trim()}")

                if (recipientKey != null && session.isNotBlank()) {
                    writeEncrypted(output, fileIn, mime, recipientKey, session)
                } else {
                    writePlain(output, fileIn, mime, totalLength)
                }
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed streaming track", e)
            return false
        }
    }

    private fun openTrackStream(context: Context, track: TrackEntity?): Pair<InputStream, Long>? {
        if (track?.localFilePath != null && File(track.localFilePath).exists()) {
            val f = File(track.localFilePath)
            return Pair(f.inputStream(), f.length())
        }
        if (track?.streamUri != null) {
            val uri = Uri.parse(track.streamUri)
            val s = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            val len = runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: -1L
            if (s != null) return Pair(s, len)
        }
        return null
    }

    private fun writeEncrypted(
        output: OutputStream,
        source: InputStream,
        mime: String,
        recipientPublicKeyB64: String,
        sessionId: String
    ) {
        val roomKey = AudioStreamKeys.newRoomKey()
        val sealed = identityKeyManager.sealNote(
            recipientPublicKeyB64,
            AudioStreamKeys.encodeRoomKey(roomKey)
        )
        output.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: $mime\r\n$SEALED_KEY_HEADER: $sealed\r\nConnection: close\r\n\r\n").toByteArray()
        )
        RelayStreamFraming.encrypt(
            source,
            output,
            AudioStreamCipher(AudioStreamKeys.derive(roomKey, sessionId))
        )
        output.flush()
    }

    private fun writePlain(
        output: OutputStream,
        source: InputStream,
        mime: String,
        totalLength: Long
    ) {
        val lengthHeader = if (totalLength > 0L) "Content-Length: $totalLength\r\n" else ""
        output.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: $mime\r\nAccept-Ranges: bytes\r\n${lengthHeader}Connection: close\r\n\r\n").toByteArray()
        )
        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (source.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        output.flush()
    }

    companion object {
        private const val TAG = "P2PAudioResponseWriter"
        const val SEALED_KEY_HEADER = "x-agro-sealed-key"
    }
}
