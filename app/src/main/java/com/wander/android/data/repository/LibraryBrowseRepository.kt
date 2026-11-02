package com.wander.android.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.wander.android.core.database.dao.LibraryBrowseDao
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.LibrarySort
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The Library's lists, read from Room in the order and for the sources the user picked.
 *
 * An empty [sources] set means every source — see [LibraryBrowseDao] for why the query is always
 * handed an explicit list.
 */
@Singleton
class LibraryBrowseRepository @Inject constructor(private val dao: LibraryBrowseDao) {

    fun pagedTracks(sort: LibrarySort, sources: Set<SourceType>): Flow<PagingData<UnifiedTrack>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { dao.pagedLibraryTracks(sort.name, sources.orEvery()) }
    ).flow.map { page -> page.map(TrackEntity::toUnifiedTrack) }

    suspend fun trackIds(sort: LibrarySort, sources: Set<SourceType>): List<String> =
        withContext(Dispatchers.IO) { dao.libraryTrackIds(sort.name, sources.orEvery()) }

    fun likedTracks(sort: LibrarySort, sources: Set<SourceType>): Flow<List<UnifiedTrack>> =
        dao.observeLikedTracks(sort.name, sources.orEvery()).toTracks()

    fun downloadedTracks(sort: LibrarySort, sources: Set<SourceType>): Flow<List<UnifiedTrack>> =
        dao.observeDownloadedTracks(sort.name, sources.orEvery()).toTracks()

    fun albums(sort: LibrarySort, sources: Set<SourceType>): Flow<List<UnifiedAlbum>> {
        val rows = if (sort.needsTrackActivity) {
            // Re-runs on every write to `tracks`; most of those (a play elsewhere) change nothing here.
            dao.observeLibraryAlbumsByActivity(sort.name, sources.orEvery()).distinctUntilChanged()
        } else {
            dao.observeLibraryAlbums(sort.name, sources.orEvery())
        }
        return rows.map { list -> list.map(AlbumEntity::toUnifiedAlbum) }.flowOn(Dispatchers.Default)
    }

    private fun Flow<List<TrackEntity>>.toTracks(): Flow<List<UnifiedTrack>> =
        map { list -> list.map(TrackEntity::toUnifiedTrack) }.flowOn(Dispatchers.Default)

    private fun Set<SourceType>.orEvery(): List<SourceType> =
        if (isEmpty()) SourceType.entries else toList()

    private companion object {
        const val PAGE_SIZE = 60
    }
}
