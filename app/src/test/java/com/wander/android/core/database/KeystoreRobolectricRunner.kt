package com.wander.android.core.database

import org.robolectric.RobolectricTestRunner

/**
 * Installs [FakeAndroidKeyStore] before Robolectric builds the real `Application`, which asks the
 * keystore for a master key as soon as it starts.
 *
 * Tests using it pin `@Config(sdk = [35])`: Robolectric runs SDK 36 only on JDK 21, and CI and the
 * project build on JDK 17.
 */
class KeystoreRobolectricRunner(testClass: Class<*>) : RobolectricTestRunner(testClass) {
    init {
        FakeAndroidKeyStore.install()
    }
}
