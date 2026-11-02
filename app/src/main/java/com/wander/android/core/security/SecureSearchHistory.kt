package com.wander.android.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val KEY_SEARCH_HISTORY = "key_search_history"

/**
 * The searches shown under an empty search field, newest first.
 *
 * Here rather than in Room: it is a couple of dozen short strings, read whole and written whole,
 * and what someone has been searching for is exactly the kind of thing this encrypted store is
 * for. Kept as one newline-separated string because the order is the point, which a string set
 * would not keep — `SearchHistoryRepository` folds every query onto one line before it is stored.
 */
internal class SecureSearchHistory(private val prefs: SharedPreferences) {

    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun write(entries: List<String>) {
        prefs.edit {
            if (entries.isEmpty()) {
                remove(KEY_SEARCH_HISTORY)
            } else {
                putString(KEY_SEARCH_HISTORY, entries.joinToString(SEPARATOR))
            }
        }
        _entries.value = entries
    }

    fun reloadFlows() {
        _entries.value = read()
    }

    private fun read(): List<String> =
        prefs.getString(KEY_SEARCH_HISTORY, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private companion object {
        const val SEPARATOR = "\n"
    }
}
