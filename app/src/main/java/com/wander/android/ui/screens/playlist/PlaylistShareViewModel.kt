package com.wander.android.ui.screens.playlist

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.PlaylistFileExporter
import com.wander.android.data.repository.PlaylistPublication
import com.wander.android.data.repository.PlaylistPublicationRepository
import com.wander.android.data.repository.PlaylistWriteRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.repository.UniversalPlaylistLink
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistRunner
import com.wander.android.data.sources.ShareKind
import com.wander.android.data.sources.ShareTarget
import com.wander.android.data.sources.agro.EditAccess
import com.wander.android.data.sources.agro.PlaylistVisibility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

/** Which of the playlist screen's sheets is open. */
enum class PlaylistSheet { SHARE, VISIBILITY, COLLABORATION, CONVERT }

/**
 * Everything that sends a playlist somewhere else: a link, an `.m3u8` file, a copy on Agro and who
 * can open it, or a Wanda copy of a playlist that belongs to one backend.
 *
 * The playlist and its tracks are passed in by the screen, which already holds them through
 * [PlaylistViewModel], rather than loaded a second time here.
 */
@HiltViewModel
class PlaylistShareViewModel @Inject constructor(
    private val shareRepository: ShareRepository,
    private val publications: PlaylistPublicationRepository,
    private val exporter: PlaylistFileExporter,
    private val playlistWrites: PlaylistWriteRepository,
    private val runner: SharedPlaylistRunner,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: String = savedStateHandle.get<String>("playlistId")
        .orEmpty()
        .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

    private val _sheet = MutableStateFlow<PlaylistSheet?>(null)
    val sheet: StateFlow<PlaylistSheet?> = _sheet.asStateFlow()

    /** This playlist's Agro copy; null when it has none. */
    val publication: StateFlow<PlaylistPublication?> = publications.publication(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _working = MutableStateFlow<Int?>(null)

    /** What is being done on the server right now, as a label for the loading indicator; null when idle. */
    val working: StateFlow<Int?> = _working.asStateFlow()

    private val _fileShares = MutableSharedFlow<Uri>(extraBufferCapacity = 1)

    /** An exported file ready for the share sheet, which needs an Activity to open. */
    val fileShares: SharedFlow<Uri> = _fileShares.asSharedFlow()

    private val _converted = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** The id of the Wanda playlist a conversion just made, to open it. */
    val converted: SharedFlow<String> = _converted.asSharedFlow()

    val canPublishToAgro: Boolean get() = shareRepository.canPublishToAgro

    fun canShareOriginal(playlist: UnifiedPlaylist) =
        playlist.source != SourceType.LOCAL && shareRepository.canShare(playlist.source)

    fun canConvert(playlist: UnifiedPlaylist, tracks: List<UnifiedTrack>) =
        playlist.source != SourceType.LOCAL && tracks.isNotEmpty()

    fun open(sheet: PlaylistSheet) {
        _sheet.value = sheet
    }

    fun dismiss() {
        _sheet.value = null
    }

    fun shareOriginal(playlist: UnifiedPlaylist) {
        dismiss()
        viewModelScope.launch {
            shareRepository.share(
                ShareTarget(
                    kind = ShareKind.PLAYLIST,
                    source = playlist.source,
                    id = playlist.id,
                    title = playlist.name,
                    subtitle = playlist.comment
                )
            )
        }
    }

    /** A link that lists the tracks; one too long for a link goes out as a file instead. */
    fun shareAsLink(playlist: UnifiedPlaylist, tracks: List<UnifiedTrack>) {
        if (tracks.size > UniversalPlaylistLink.MAX_TRACKS) {
            publications.report(R.string.playlist_shared_as_file)
            shareAsFile(playlist, tracks)
            return
        }
        dismiss()
        shareRepository.shareUniversalPlaylist(playlist, tracks)
    }

    fun shareAsFile(playlist: UnifiedPlaylist, tracks: List<UnifiedTrack>) {
        dismiss()
        viewModelScope.launch {
            working(R.string.playlist_working_file) { exporter.shareableFile(playlist.name, tracks) }.fold(
                onSuccess = { _fileShares.tryEmit(it) },
                onFailure = { publications.report(R.string.playlist_file_failed, it.message.orEmpty()) }
            )
        }
    }

    /** Writes the file the person chose to create in the system's save dialog. */
    fun saveFile(target: Uri, playlist: UnifiedPlaylist, tracks: List<UnifiedTrack>) {
        viewModelScope.launch {
            exporter.saveTo(target, playlist.name, tracks).fold(
                onSuccess = { publications.report(R.string.playlist_file_saved, playlist.name) },
                onFailure = { publications.report(R.string.playlist_file_failed, it.message.orEmpty()) }
            )
        }
    }

    /**
     * Publishes the playlist with [visibility], or, when it already has an Agro copy, changes who
     * can open that copy instead of making a second one.
     *
     * Only a Wanda playlist is published as it is. One that belongs to a backend is converted
     * first and the copy published, because only a Wanda playlist is kept in step with Agro: the
     * backend's own copy changes behind Wanda's back, and its edits belong on that backend.
     */
    fun pickVisibility(visibility: PlaylistVisibility, playlist: UnifiedPlaylist, tracks: List<UnifiedTrack>) {
        dismiss()
        viewModelScope.launch {
            publication.value?.let { current ->
                working(R.string.playlist_working_updating) { publications.changeVisibility(playlistId, visibility) }
                // The server counted that as a change; read the new revision back before the next edit.
                runner.syncSoon(current.agroId)
                return@launch
            }
            working(R.string.playlist_working_sharing) {
                if (playlist.source != SourceType.LOCAL) {
                    convertAndPublish(playlist, tracks, visibility)
                } else {
                    shareRepository.shareLocalPlaylist(playlist, tracks, visibility)
                        ?.let { agroId -> publications.record(playlistId, agroId, visibility, tracks) }
                }
            }
        }
    }

    /** Makes a Wanda copy of a backend's playlist, publishes that, and opens it. */
    private suspend fun convertAndPublish(playlist: UnifiedPlaylist, tracks: List<UnifiedTrack>, visibility: PlaylistVisibility) {
        val keep = tracks.filter { it.source != SourceType.UNRESOLVED }
        val copyId = playlistWrites.createPlaylist(SourceType.LOCAL, playlist.name, keep.map { it.id }, keep)
            .getOrElse { return }
        val copy = UnifiedPlaylist(id = copyId, source = SourceType.LOCAL, name = playlist.name, songCount = keep.size)
        shareRepository.shareLocalPlaylist(copy, keep, visibility)
            ?.let { agroId -> publications.record(copyId, agroId, visibility, keep) }
        _converted.tryEmit(copyId)
    }

    /** Whether "Through Agro" shares this playlist as it is, or a Wanda copy of it. */
    fun publishesACopy(playlist: UnifiedPlaylist) = playlist.source != SourceType.LOCAL

    /** Lets friends edit the shared copy, or anyone who can open it add to it, or no one. */
    fun pickEditAccess(access: EditAccess) {
        dismiss()
        val current = publication.value ?: return
        viewModelScope.launch {
            working(R.string.playlist_working_updating) { publications.changeEditAccess(playlistId, access) }
            runner.syncSoon(current.agroId)
        }
    }

    /** Sends the link to the existing Agro copy again; never makes a second copy. */
    fun resendAgroLink(playlist: UnifiedPlaylist, tracks: List<UnifiedTrack>) {
        val current = publication.value ?: return
        dismiss()
        shareRepository.shareAgroPlaylist(playlist, current.agroId, tracks.size)
    }

    /** Deletes the Agro copy; the screen has already asked. */
    fun unshare() {
        dismiss()
        viewModelScope.launch { working(R.string.playlist_working_unsharing) { publications.unshare(playlistId) } }
    }

    /** Copies a backend's playlist into a Wanda playlist, which can then hold any source's tracks. */
    fun convert(playlist: UnifiedPlaylist, tracks: List<UnifiedTrack>) {
        dismiss()
        val keep = tracks.filter { it.source != SourceType.UNRESOLVED }
        viewModelScope.launch {
            playlistWrites.createPlaylist(SourceType.LOCAL, playlist.name, keep.map { it.id }, keep)
                .onSuccess { _converted.tryEmit(it) }
        }
    }

    /** Runs [block] with [label] on the loading indicator, cleared however it ends. */
    private suspend fun <T> working(@StringRes label: Int, block: suspend () -> T): T {
        _working.value = label
        try {
            return block()
        } finally {
            _working.value = null
        }
    }
}
