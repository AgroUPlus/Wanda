package com.wander.android.ui.screens.home.layout

import com.wander.android.core.security.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's Home shelf layout. Lives in [SecureStorage], so the local backup and the Agro cloud
 * backup carry it with the rest of the settings and a restore re-emits it.
 */
@Singleton
class HomeLayoutStore @Inject constructor(private val storage: SecureStorage) {

    /** Empty until the user customises Home. */
    val layout: Flow<List<ShelfConfig>> = storage.displayPrefs.homeLayout
        .map(ShelfConfigCodec::decode)
        .distinctUntilChanged()

    private val _editing = MutableStateFlow(false)

    /** The customizer is open. Shared so the radio button, which lives in the app shell, can step aside. */
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    fun setEditing(on: Boolean) {
        _editing.value = on
    }

    private val _editRequested = MutableStateFlow(false)

    /** Settings asked for the customizer; Home opens it as soon as it can and clears this. */
    val editRequested: StateFlow<Boolean> = _editRequested.asStateFlow()

    fun requestEditing() {
        _editRequested.value = true
    }

    fun clearEditRequest() {
        _editRequested.value = false
    }

    fun save(configs: List<ShelfConfig>) =
        storage.displayPrefs.setHomeLayout(ShelfConfigCodec.encode(configs))
}
