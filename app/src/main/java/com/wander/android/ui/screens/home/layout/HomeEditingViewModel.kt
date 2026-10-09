package com.wander.android.ui.screens.home.layout

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Lets the app shell know the Home customizer is open. */
@HiltViewModel
class HomeEditingViewModel @Inject constructor(private val layoutStore: HomeLayoutStore) : ViewModel() {
    val editing = layoutStore.editing

    /** From Settings: open Home in the customizer. */
    fun requestEditing() = layoutStore.requestEditing()
}
