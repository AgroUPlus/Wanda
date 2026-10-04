package com.wander.android.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A restore has to change what the app shows, not only what is on disk for the next launch. */
class SecureStorageRestoreTest {

    @Test
    fun aRestoredSettingIsLiveWithoutARestart() {
        val storage = SecureStorage(FakeSharedPreferences())
        assertFalse(storage.isAmoledBlack.value)

        storage.importAll(mapOf(KEY_AMOLED_BLACK to true)) { true }

        assertTrue(storage.isAmoledBlack.value)
    }

    @Test
    fun clearingTheStoreBringsEveryFlowBackToItsDefault() {
        val storage = SecureStorage(FakeSharedPreferences())
        storage.importAll(mapOf(KEY_AMOLED_BLACK to true, KEY_COVER_ART_THEME to false)) { true }

        storage.clearAllCredentials()

        assertFalse(storage.isAmoledBlack.value)
        assertTrue(storage.isCoverArtThemeEnabled.value)
    }
}
