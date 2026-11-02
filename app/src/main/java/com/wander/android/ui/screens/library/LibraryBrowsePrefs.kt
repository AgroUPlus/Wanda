package com.wander.android.ui.screens.library

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.wander.android.data.model.LibrarySort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How the Library was last left: which section was open, and how each list was sorted.
 *
 * Plain preferences rather than `SecureStorage`: none of this is a secret, and it is a device's
 * own habit rather than a setting worth carrying in a backup.
 */
@Singleton
class LibraryBrowsePrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The section last selected, or null when the Library has never been opened. */
    fun lastTab(): LibraryTab? =
        prefs.getString(KEY_TAB, null)?.let { name -> LibraryTab.entries.firstOrNull { it.name == name } }

    fun setLastTab(tab: LibraryTab) = prefs.edit { putString(KEY_TAB, tab.name) }

    /** The order [tab]'s list was last given, or its [default] when it has never been changed. */
    fun sortFor(tab: LibraryTab, default: LibrarySort): LibrarySort =
        prefs.getString(KEY_SORT_PREFIX + tab.name, null)
            ?.let { name -> LibrarySort.entries.firstOrNull { it.name == name } }
            ?: default

    fun setSort(tab: LibraryTab, sort: LibrarySort) =
        prefs.edit { putString(KEY_SORT_PREFIX + tab.name, sort.name) }

    private companion object {
        const val PREFS_NAME = "wander_library_browse"
        const val KEY_TAB = "last_tab"
        const val KEY_SORT_PREFIX = "sort_"
    }
}
