package com.wander.android.ui.screens.home.layout

import com.wander.android.core.security.SecureStorage
import kotlinx.coroutines.flow.Flow
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

    fun save(configs: List<ShelfConfig>) =
        storage.displayPrefs.setHomeLayout(ShelfConfigCodec.encode(configs))
}
