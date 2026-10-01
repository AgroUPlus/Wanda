package com.wander.android.ui.screens.player

import androidx.lifecycle.ViewModel
import com.wander.android.core.database.entity.EpisodeExtrasEntity
import com.wander.android.data.podcast.Chapter
import com.wander.android.data.podcast.TranscriptCue
import com.wander.android.data.repository.EpisodeExtrasRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Chapters and transcripts for the playing episode. Kept apart from [NowPlayingViewModel], which is already large. */
@HiltViewModel
internal class EpisodeExtrasViewModel @Inject constructor(
    private val extras: EpisodeExtrasRepository
) : ViewModel() {

    fun extras(trackId: String): Flow<EpisodeExtrasEntity?> = extras.observe(trackId)

    suspend fun chapters(url: String): Result<List<Chapter>> = extras.chapters(url)

    suspend fun transcript(url: String, mime: String?): Result<List<TranscriptCue>> = extras.transcript(url, mime)
}
