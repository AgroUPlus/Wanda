package com.wander.android.ui.screens.library

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.R
import com.wander.android.core.database.entity.PodcastEntity
import com.wander.android.data.repository.OpmlImportResult
import com.wander.android.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** A message for the snackbar: a string resource and its format arguments, resolved where it is shown. */
class UiMessage(@param:StringRes val res: Int, val args: List<Any> = emptyList())

@HiltViewModel
class PodcastSubscriptionsViewModel @Inject constructor(
    private val podcasts: PodcastRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    /** Null until the first read, so the screen shows neither the empty state nor a list too early. */
    val subscriptions: StateFlow<List<PodcastEntity>?> = podcasts.subscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun add(url: String) = busy {
        podcasts.subscribe(url)
            .onSuccess { say(R.string.podcasts_added, it.title) }
            .onFailure { say(R.string.podcasts_add_failed, it.message.orEmpty()) }
    }

    fun remove(feedUrl: String) {
        viewModelScope.launch { podcasts.unsubscribe(feedUrl) }
    }

    fun refresh() = busy {
        val summary = podcasts.refreshAll()
        if (summary.failed == 0) say(R.string.podcasts_refreshed) else say(R.string.podcasts_refresh_failed, summary.failed)
    }

    fun importOpml(uri: Uri) = busy {
        val result = withContext<Result<OpmlImportResult>>(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IOException(context.getString(R.string.podcasts_file_unreadable)))
            input.use { podcasts.importOpml(it) }
        }
        result
            .onSuccess { say(R.string.podcasts_import_done, it.added, it.alreadySubscribed, it.invalid) }
            .onFailure { say(R.string.podcasts_import_failed, it.message.orEmpty()) }
    }

    fun exportOpml(uri: Uri) = busy {
        val xml = podcasts.exportOpml(context.getString(R.string.podcasts_opml_title))
        try {
            withContext(Dispatchers.IO) {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException(context.getString(R.string.podcasts_file_unreadable))
                output.use { it.write(xml.toByteArray(Charsets.UTF_8)) }
            }
            say(R.string.podcasts_export_done)
        } catch (e: IOException) {
            say(R.string.podcasts_export_failed, e.message.orEmpty())
        }
    }

    private fun say(@StringRes res: Int, vararg args: Any) {
        _messages.tryEmit(UiMessage(res, args.toList()))
    }

    /** Runs [block] with the progress bar showing; one operation at a time. */
    private fun busy(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }
}
