package com.wander.android.core.cache

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Least-recently-used eviction that will not remove a liked track's cached audio while any
 * unliked span could be evicted instead.
 *
 * Media3's own `LeastRecentlyUsedCacheEvictor` has no hook for "never this key" — it is a straight
 * age ordering with no way to skip an entry, so a liked song played once weeks ago was evicted
 * exactly as readily as something played once and never again. This is that evictor's algorithm
 * (oldest [CacheSpan.lastTouchTimestamp] first) with one added step: a protected span is skipped
 * for eviction as long as an unprotected one is available to take its place. The cap is still a
 * hard cap — once *only* protected spans remain, they become evictable too, rather than let the
 * cache grow without bound.
 *
 * The cache key Media3's `CacheDataSource` uses by default is the stream URI itself, so protected
 * keys are checked against that — see [AudioCacheManager.updateProtectedStreamUris].
 */
@OptIn(UnstableApi::class)
class ProtectingCacheEvictor(
    private val maxSizeBytes: Long
) : CacheEvictor {

    /** Stream URIs (cache keys) that should survive eviction while anything else can go instead. */
    private val protectedKeys = CopyOnWriteArraySet<String>()

    private val leastRecentlyUsed = TreeSet<CacheSpan> { a, b ->
        val delta = a.lastTouchTimestamp - b.lastTouchTimestamp
        when {
            delta > 0 -> 1
            delta < 0 -> -1
            else -> a.compareTo(b)
        }
    }

    fun updateProtectedKeys(keys: Set<String>) {
        protectedKeys.clear()
        protectedKeys.addAll(keys)
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) = Unit

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        evictExcess(cache)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        leastRecentlyUsed.remove(oldSpan)
        leastRecentlyUsed.add(newSpan)
    }

    /**
     * One fresh scan per removal rather than a held iterator: `cache.removeSpan` calls
     * [onSpanRemoved] synchronously, which mutates [leastRecentlyUsed] — mutating a `TreeSet`
     * through anything but a live iterator's own `remove()` invalidates that iterator, so a scan
     * that survived past the removal it triggered would throw on its very next step.
     */
    private fun evictExcess(cache: Cache) {
        while (cache.cacheSpace > maxSizeBytes) {
            val next = leastRecentlyUsed.firstOrNull { it.key !in protectedKeys }
                ?: leastRecentlyUsed.firstOrNull()
                ?: return
            cache.removeSpan(next)
        }
    }
}
