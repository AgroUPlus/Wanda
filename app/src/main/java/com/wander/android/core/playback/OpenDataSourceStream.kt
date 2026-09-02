package com.wander.android.core.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import java.io.InputStream

/**
 * An [InputStream] over a [DataSource] that is **already open**.
 *
 * Media3's own `DataSourceInputStream` takes a closed source and a `DataSpec` and opens it lazily
 * on the first read. That is the wrong shape for a source that had to be opened earlier in order to
 * read its response headers — using it there opens the same source a second time.
 *
 * On the relay that is not a harmless duplicate. A relay session hands out its receiving half
 * exactly once, so the second `open` is a second `GET /api/v1/relay/{id}/receive`, and the server
 * answers `409 relay sender already connected` — which surfaced as a 409 when playing a peer's
 * local file, with the first stream still perfectly healthy and unread.
 *
 * Closing this does **not** close the source. Whoever opened it closes it; a stream that closed
 * something it did not open would leave the outer `DataSource.close` releasing a closed source.
 */
@UnstableApi
internal class OpenDataSourceStream(private val source: DataSource) : InputStream() {

    private val singleByte = ByteArray(1)

    override fun read(): Int {
        val count = read(singleByte, 0, 1)
        return if (count == -1) -1 else singleByte[0].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        // Media3 signals exhaustion with its own constant rather than -1, and a caller reading this
        // as an ordinary stream would take that value for a byte count.
        val read = source.read(buffer, offset, length)
        return if (read == C.RESULT_END_OF_INPUT) -1 else read
    }

    override fun close() = Unit
}
