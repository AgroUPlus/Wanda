package com.wander.android.ui.screens.home.layout

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Lets the app shell know the Home customizer is open. */
@HiltViewModel
class HomeEditingViewModel @Inject constructor(store: HomeLayoutStore) : ViewModel() {
    val editing = store.editing
}
