package com.wander.android.ui.navigation

import com.wander.android.core.security.SecureStorage
import com.wander.android.ui.screens.library.LibraryTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which shortcuts the bottom bar carries and which sections the Library shows, in the user's order.
 * Kept with the other display settings, so the local backup and the Agro cloud backup carry them.
 */
@Singleton
class NavLayoutStore @Inject constructor(private val storage: SecureStorage) {

    val dockItems: Flow<List<DockItem>> = storage.displayPrefs.dockItems
        .map { NavLayoutCodec.decode(it, DockItem.entries, DockItem.Default).take(DockItem.MaxShown) }
        .distinctUntilChanged()

    /** Never empty: a Library with no sections would have nothing to show. */
    val libraryTabs: Flow<List<LibraryTab>> = storage.displayPrefs.libraryTabs
        .map { NavLayoutCodec.decode(it, LibraryTab.entries, LibraryTab.entries).ifEmpty { LibraryTab.entries } }
        .distinctUntilChanged()

    fun saveDockItems(items: List<DockItem>) =
        storage.displayPrefs.setDockItems(NavLayoutCodec.encode(items.take(DockItem.MaxShown)))

    fun saveLibraryTabs(tabs: List<LibraryTab>) =
        storage.displayPrefs.setLibraryTabs(NavLayoutCodec.encode(tabs.ifEmpty { LibraryTab.entries }))
}
