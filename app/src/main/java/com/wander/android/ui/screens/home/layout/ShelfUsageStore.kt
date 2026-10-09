package com.wander.android.ui.screens.home.layout

import com.wander.android.core.security.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Counts plays started from Home shelves, on this device only, to suggest which shelves to replace. */
@Singleton
class ShelfUsageStore @Inject constructor(private val storage: SecureStorage) {

    val usage: Flow<ShelfUsage> = storage.displayPrefs.shelfUsage
        .map(ShelfUsageCodec::decode)
        .distinctUntilChanged()

    fun record(shelfId: String) {
        val current = ShelfUsageCodec.decode(storage.displayPrefs.shelfUsage.value)
        storage.displayPrefs.setShelfUsage(ShelfUsageCodec.encode(current.recorded(shelfId, System.currentTimeMillis())))
    }
}
