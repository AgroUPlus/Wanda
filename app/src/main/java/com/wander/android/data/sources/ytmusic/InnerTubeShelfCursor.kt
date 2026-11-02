package com.wander.android.data.sources.ytmusic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * One long InnerTube shelf, read page by page through its continuation tokens.
 *
 * The callers page by offset, and InnerTube pages by opaque token, so the rows already read and
 * the token for the next page are kept between calls: asking for offset 100 after offset 0 costs
 * one more request, not a re-read of everything before it. Offset 0 always starts over, which is
 * how a refresh sees rows added since the last one.
 *
 * A page that fails fails the whole call. Handing back the rows read so far as if they were all
 * of them would tell the caller the shelf ends where the network did.
 */
internal class InnerTubeShelfCursor<T>(
    private val firstPage: suspend () -> Result<JsonObject>,
    private val nextPage: suspend (InnerTubeContinuation) -> Result<JsonObject>,
    private val parse: (JsonObject) -> List<T>
) {
    private val mutex = Mutex()
    private val rows = ArrayList<T>()
    private var next: InnerTubeContinuation? = null
    private var started = false
    private var pages = 0
    private val seenTokens = HashSet<String>()

    /** Rows `[offset, offset + limit)`, reading as many further pages as that needs. */
    suspend fun slice(offset: Int, limit: Int): Result<List<T>> = mutex.withLock {
        runCatching {
            if (offset == 0 || !started) restart()
            val wanted = offset.toLong() + limit.coerceAtLeast(0)
            while (rows.size < wanted) {
                val token = next ?: break
                if (pages >= MAX_PAGES || !seenTokens.add(token.token)) break
                read(nextPage(token).getOrThrow())
            }
            rows.drop(offset).take(limit)
        }
    }

    /** Every row on the shelf, up to [MAX_PAGES] pages of it. */
    suspend fun all(): Result<List<T>> = slice(0, Int.MAX_VALUE)

    private suspend fun restart() {
        rows.clear()
        seenTokens.clear()
        next = null
        pages = 0
        started = false
        read(firstPage().getOrThrow())
        started = true
    }

    private fun read(page: JsonObject) {
        rows += parse(page)
        next = page.shelfContinuation()
        pages++
    }

    companion object {
        /**
         * A ceiling on requests per walk, not on the library: at ~100 rows a page it is 10,000
         * rows, and it exists so a server that kept answering with fresh tokens and no rows could
         * not keep a refresh going forever.
         */
        const val MAX_PAGES = 100
    }
}
