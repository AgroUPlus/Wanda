package com.wander.android.ui.screens.replay

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.replay.ReplayArchivist
import com.wander.android.data.replay.ReplayRepository
import com.wander.android.data.replay.ReplayReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the story is doing right now. */
@Immutable
data class ReplayUiState(
    val year: Int = 0,
    val isLoading: Boolean = true,
    val deck: List<ReplayCard> = emptyList(),
    val report: ReplayReport? = null,
    /** Non-null when the recap could not be built at all. Nothing is drawn behind it. */
    val failure: String? = null,
    val isSaved: Boolean = false,
    /** How many plays the tidy-up would forget, so the confirmation names a real number. */
    val purgeableCount: Int = 0,
    val isWorking: Boolean = false,
    /** Set once the tidy-up has run, with how many plays it forgot. */
    val purgedCount: Int? = null,
    /** A server or database failure the user has to be told about, rather than a silent no-op. */
    val actionFailure: String? = null
)

@HiltViewModel
internal class ReplayViewModel @Inject constructor(
    private val repository: ReplayRepository,
    private val archivist: ReplayArchivist,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _state = MutableStateFlow(ReplayUiState())
    val state: StateFlow<ReplayUiState> = _state.asStateFlow()

    /**
     * Builds the story for [year].
     *
     * A year already saved is read back from disk rather than recomputed. That is not a cache: once
     * the plays behind a recap have been forgotten, the saved copy is the only thing that can still
     * answer, and recomputing would quietly produce a smaller year.
     */
    fun load(year: Int) {
        if (_state.value.year == year && !_state.value.isLoading) return
        _state.value = ReplayUiState(year = year, isLoading = true)

        // Written on open rather than on finish: a recap dismissed on the second card should not
        // be waiting again at the next launch.
        if (year > secureStorage.lastSeenReplayYear) {
            secureStorage.lastSeenReplayYear = year
        }

        viewModelScope.launch {
            val saved = archivist.saved(year)
            val report = saved ?: repository.report(year).getOrElse { cause ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    failure = cause.message ?: "That year could not be read"
                )
                return@launch
            }

            _state.value = _state.value.copy(
                isLoading = false,
                deck = buildReplayDeck(report),
                report = report,
                isSaved = saved != null,
                purgeableCount = archivist.purgeableCount(year)
            )
        }
    }

    fun save() {
        val report = _state.value.report ?: return
        _state.value = _state.value.copy(isWorking = true, actionFailure = null)

        viewModelScope.launch {
            archivist.save(report)
                .onSuccess { _state.value = _state.value.copy(isWorking = false, isSaved = true) }
                .onFailure { cause ->
                    _state.value = _state.value.copy(
                        isWorking = false,
                        actionFailure = cause.message ?: "The recap could not be saved"
                    )
                }
        }
    }

    /**
     * Saves, then forgets the plays from before the recap year.
     *
     * Save first, always, even when the recap is already saved — the archivist reads it back, and
     * that read is what the delete is allowed by. A failure at any step leaves every play in place.
     */
    fun saveAndPurge() {
        val report = _state.value.report ?: return
        _state.value = _state.value.copy(isWorking = true, actionFailure = null)

        viewModelScope.launch {
            archivist.save(report)
                .mapCatching { archivist.purgeBefore(report.year).getOrThrow() }
                .onSuccess { purged ->
                    _state.value = _state.value.copy(
                        isWorking = false,
                        isSaved = true,
                        purgedCount = purged,
                        purgeableCount = 0
                    )
                }
                .onFailure { cause ->
                    _state.value = _state.value.copy(
                        isWorking = false,
                        // Saving may still have succeeded; only the tidy-up is reported as failed,
                        // and nothing was deleted.
                        isSaved = archivist.saved(report.year) != null,
                        actionFailure = cause.message ?: "Nothing was deleted"
                    )
                }
        }
    }

    fun dismissActionFailure() {
        _state.value = _state.value.copy(actionFailure = null)
    }
}
