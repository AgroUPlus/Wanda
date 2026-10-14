package com.wander.android.core.database

import org.robolectric.RobolectricTestRunner

/**
 * Installs [FakeAndroidKeyStore] before Robolectric builds the real `Application`, which asks the
 * keystore for a master key as soon as it starts.
 */
class KeystoreRobolectricRunner(testClass: Class<*>) : RobolectricTestRunner(testClass) {
    init {
        FakeAndroidKeyStore.install()
    }
}
