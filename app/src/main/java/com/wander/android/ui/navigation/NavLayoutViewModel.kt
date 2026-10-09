package com.wander.android.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.ui.screens.library.LibraryTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The bottom bar and Library layout, for the screens that show or change them. */
@HiltViewModel
class NavLayoutViewModel @Inject constructor(private val store: NavLayoutStore) : ViewModel() {

    val dockItems: StateFlow<List<DockItem>> =
        store.dockItems.stateIn(viewModelScope, SharingStarted.Eagerly, DockItem.Default)

    val libraryTabs: StateFlow<List<LibraryTab>> =
        store.libraryTabs.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryTab.entries)

    fun saveDockItems(items: List<DockItem>) = store.saveDockItems(items)

    fun saveLibraryTabs(tabs: List<LibraryTab>) = store.saveLibraryTabs(tabs)
}
