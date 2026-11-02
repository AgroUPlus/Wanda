package com.wander.android.data.repository

import com.wander.android.core.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * The recent searches offered under an empty search field.
 *
 * A search is recorded when it was *used* — a result played or opened — rather than on every
 * pause in typing, which would file "ra", "rad" and "radi" on the way to "radiohead". Nothing is
 * recorded while incognito: going quiet has to mean this device keeps no trail either.
 */
@Singleton
class SearchHistoryRepository @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val store get() = secureStorage.searchHistory

    val recent: StateFlow<List<String>> get() = store.entries

    fun record(query: String) {
        if (secureStorage.isIncognitoMode) return
        val updated = store.entries.value.withQuery(query)
        if (updated != store.entries.value) store.write(updated)
    }

    fun remove(query: String) {
        store.write(store.entries.value.filterNot { it == query })
    }

    fun clear() {
        store.write(emptyList())
    }
}

/** How many recent searches are kept. */
internal const val MAX_SEARCH_HISTORY = 20

/**
 * [query] at the front, on one line, with any earlier entry for the same words — in any case —
 * dropped, and the oldest falling off past [max]. A blank query changes nothing.
 */
internal fun List<String>.withQuery(query: String, max: Int = MAX_SEARCH_HISTORY): List<String> {
    val entry = query.trim().replace(WHITESPACE, " ")
    if (entry.isEmpty()) return this
    return (listOf(entry) + filterNot { it.equals(entry, ignoreCase = true) }).take(max)
}

private val WHITESPACE = Regex("\\s+")
