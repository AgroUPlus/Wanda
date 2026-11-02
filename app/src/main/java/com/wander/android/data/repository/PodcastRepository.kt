package com.wander.android.data.repository

import android.util.Xml
import androidx.room.withTransaction
import com.wander.android.core.database.WanderDatabase
import com.wander.android.core.database.dao.EpisodeExtrasDao
import com.wander.android.core.database.dao.PodcastDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.PodcastEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.database.entity.currentUrl
import com.wander.android.core.work.PodcastSyncScheduler
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.podcast.FeedFetch
import com.wander.android.data.podcast.Opml
import com.wander.android.data.podcast.OpmlFeed
import com.wander.android.data.podcast.PodcastEpisodes
import com.wander.android.data.podcast.PodcastFeedClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** An episode a sync pass found that was not there before. */
data class NewEpisode(val trackId: String, val showTitle: String, val title: String)

/**
 * What one pass over the subscriptions did. [failed] counts feeds that errored; they are tried again
 * next pass. [newEpisodes] leaves out a new subscription's first episodes, which nobody is waiting for.
 */
data class PodcastSyncSummary(
    val updated: Int,
    val unchanged: Int,
    val failed: Int,
    val newEpisodes: List<NewEpisode> = emptyList()
)

data class OpmlImportResult(val added: Int, val alreadySubscribed: Int, val invalid: Int)

/**
 * Subscriptions and their episodes. Room is the source of truth: a refresh writes the feed's
 * episodes as `tracks` rows and the screens read them back as Flows, so the Inbox works offline.
 */
@Singleton
class PodcastRepository @Inject constructor(
    private val database: WanderDatabase,
    private val podcastDao: PodcastDao,
    private val trackDao: TrackDao,
    private val extrasDao: EpisodeExtrasDao,
    private val client: PodcastFeedClient,
    private val scheduler: PodcastSyncScheduler,
    private val autoDownloads: PodcastAutoDownloads
) {
    val subscriptions: Flow<List<PodcastEntity>> = podcastDao.observeAll()

    /** Unplayed episodes of every subscription, newest first. */
    val inbox: Flow<List<UnifiedTrack>> = podcastDao.observeInbox().map { rows -> rows.map(TrackEntity::toUnifiedTrack) }

    /**
     * Subscribes and loads the first episodes now, so the listener sees something on return.
     *
     * Idempotent: a feed already subscribed is refreshed, not duplicated.
     */
    suspend fun subscribe(rawUrl: String): Result<PodcastEntity> {
        val url = normalizeFeedUrl(rawUrl)
            ?: return Result.failure(IllegalArgumentException("That is not a web address"))
        return refresh(podcastDao.get(url) ?: podcastDao.getByFetchUrl(url) ?: stub(url, title = url))
    }

    /** Whether [feedUrl]'s new episodes download on their own, on Wi-Fi. */
    suspend fun setAutoDownload(feedUrl: String, enabled: Boolean) = autoDownloads.setEnabled(feedUrl, enabled)

    /** Removes the subscription and its episodes, keeping any the listener liked, downloaded or played. */
    suspend fun unsubscribe(feedUrl: String) {
        database.withTransaction {
            podcastDao.deleteUntouchedEpisodes(PodcastEpisodes.albumIdOf(feedUrl))
            podcastDao.delete(feedUrl)
            extrasDao.deleteOrphans()
        }
    }

    /** Asks every feed whether it has something new. One broken feed does not stop the others. */
    suspend fun refreshAll(): PodcastSyncSummary {
        var updated = 0
        var unchanged = 0
        var failed = 0
        val fresh = mutableListOf<NewEpisode>()
        for (podcast in podcastDao.getAll()) {
            val outcome = fetchAndStore(podcast)
            when {
                outcome.isFailure -> failed++
                outcome.getOrThrow().changed -> updated++
                else -> unchanged++
            }
            outcome.getOrNull()?.let { fresh += it.newEpisodes }
        }
        return PodcastSyncSummary(updated, unchanged, failed, fresh)
    }

    /** Fetches [podcast]'s feed with its validators and stores whatever is new. */
    suspend fun refresh(podcast: PodcastEntity): Result<PodcastEntity> =
        fetchAndStore(podcast).map { it.podcast }

    /** The stored podcast, whether the feed had changed since the last pass, and what it added. */
    private class Stored(val podcast: PodcastEntity, val changed: Boolean, val newEpisodes: List<NewEpisode>)

    private suspend fun fetchAndStore(podcast: PodcastEntity): Result<Stored> {
        val first = podcast.lastSyncAt == null
        val fetched = try {
            client.fetch(podcast.currentUrl, podcast.takeUnless { first })
        } catch (e: IOException) {
            return Result.failure(e)
        }
        val now = System.currentTimeMillis()
        // Read again rather than trusted from the pass's start: the listener may have toggled
        // auto-download meanwhile, and the REPLACE below would put the old value back.
        val autoDownload = podcastDao.get(podcast.feedUrl)?.autoDownload ?: podcast.autoDownload
        return when (fetched) {
            is FeedFetch.NotModified -> {
                val touched = podcast.redirectedTo(fetched.movedTo).copy(lastSyncAt = now, autoDownload = autoDownload)
                podcastDao.upsert(touched)
                if (touched.autoDownload) autoDownloads.trim(touched.feedUrl)
                Result.success(Stored(touched, changed = false, newEpisodes = emptyList()))
            }
            is FeedFetch.Updated -> {
                val limit = if (first) INITIAL_EPISODES else MAX_NEW_EPISODES
                val parsed = fetched.feed.episodes.sortedByDescending { it.publishedAt ?: 0L }.take(limit)
                val episodes = parsed.map { PodcastEpisodes.toTrack(podcast.feedUrl, fetched.feed, it, now) }
                val extras = parsed.zip(episodes).mapNotNull { (episode, track) -> PodcastEpisodes.extrasOf(track.id, episode) }
                val saved = podcast.redirectedTo(fetched.movedTo).copy(
                    title = fetched.feed.title,
                    author = fetched.feed.author,
                    artworkUrl = fetched.feed.artworkUrl,
                    etag = fetched.etag,
                    lastModified = fetched.lastModified,
                    lastSyncAt = now,
                    autoDownload = autoDownload
                ).announcedAt(fetched.feed.newFeedUrl)
                val inserted = database.withTransaction {
                    podcastDao.upsert(saved)
                    // IGNORE keeps the state of an episode already here; only new ones are added.
                    val rowIds = trackDao.insertNewTracks(episodes)
                    extrasDao.upsert(extras)
                    podcastDao.pruneEpisodes(PodcastEpisodes.albumIdOf(podcast.feedUrl), KEEP_EPISODES)
                    extrasDao.deleteOrphans()
                    episodes.zip(rowIds).filter { (_, rowId) -> rowId != -1L }.map { (track, _) -> track }
                }
                if (saved.autoDownload) {
                    autoDownloads.trim(saved.feedUrl)
                    if (!first) autoDownloads.queue(inserted)
                }
                val fresh = if (first) emptyList() else inserted.map { NewEpisode(it.id, saved.title, it.title) }
                Result.success(Stored(saved, changed = true, newEpisodes = fresh))
            }
        }
    }

    /**
     * Adds every feed in an OPML file without fetching any: a 200-show export would otherwise block
     * on 200 downloads. The episodes arrive with the next sync, which is started here.
     */
    suspend fun importOpml(input: InputStream): Result<OpmlImportResult> {
        val feeds = try {
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            Opml.parse(parser)
        } catch (e: IOException) {
            return Result.failure(e)
        } catch (e: org.xmlpull.v1.XmlPullParserException) {
            return Result.failure(IOException("The file is not valid XML", e))
        }
        val now = System.currentTimeMillis()
        val known = podcastDao.getAll().flatMapTo(mutableSetOf()) { listOfNotNull(it.feedUrl, it.fetchUrl) }
        var invalid = 0
        val fresh = LinkedHashMap<String, PodcastEntity>()
        for (feed in feeds) {
            val url = normalizeFeedUrl(feed.feedUrl)
            if (url == null) invalid++ else if (url !in known) fresh.putIfAbsent(url, stub(url, feed.title ?: url, now))
        }
        database.withTransaction { podcastDao.insertAllIfAbsent(fresh.values.toList()) }
        if (fresh.isNotEmpty()) scheduler.syncNow()
        return Result.success(OpmlImportResult(fresh.size, feeds.size - invalid - fresh.size, invalid))
    }

    suspend fun exportOpml(title: String): String =
        Opml.write(podcastDao.getAll().map { OpmlFeed(it.title, it.currentUrl) }, title)

    private fun stub(url: String, title: String, now: Long = System.currentTimeMillis()) = PodcastEntity(
        feedUrl = url, title = title, author = null, artworkUrl = null,
        etag = null, lastModified = null, lastSyncAt = null, subscribedAt = now
    )

    private companion object {
        /** A new subscription shows its latest few episodes, not the whole back catalogue. */
        const val INITIAL_EPISODES = 10
        const val MAX_NEW_EPISODES = 50
        const val KEEP_EPISODES = 100
    }
}

/**
 * [this] at the address a permanent redirect led to. The validators stay: they came from the final
 * answer, at that address.
 */
private fun PodcastEntity.redirectedTo(address: String?): PodcastEntity =
    if (address == null || address == currentUrl) this else copy(fetchUrl = address.takeIf { it != feedUrl })

/**
 * [this] at the address the feed's own `<itunes:new-feed-url>` names. HTTPS only, since a public
 * host over plain HTTP would be refused on every later fetch. The validators belong to the old
 * address, so they go, and the next pass fetches the new one in full.
 */
private fun PodcastEntity.announcedAt(raw: String?): PodcastEntity {
    val address = raw?.let(::normalizeFeedUrl)?.takeIf { it.startsWith("https://") } ?: return this
    if (address == currentUrl) return this
    return copy(fetchUrl = address.takeIf { it != feedUrl }, etag = null, lastModified = null)
}

/** `feed://` and `pcast://` are the same address over https; anything but http(s) is refused. */
internal fun normalizeFeedUrl(raw: String): String? {
    var text = raw.trim()
    for (prefix in listOf("feed://", "pcast://", "itpc://")) {
        if (text.startsWith(prefix, ignoreCase = true)) text = "https://" + text.substring(prefix.length)
    }
    val url = text.toHttpUrlOrNull() ?: return null
    return url.toString()
}
