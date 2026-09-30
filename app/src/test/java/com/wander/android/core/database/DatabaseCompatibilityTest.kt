package com.wander.android.core.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseCompatibilityTest {

    @Test
    fun aFreshInstallHasNothingToCompare() {
        assertFalse(DatabaseCompatibility.isTooOld(storedVersion = null, minSupported = 35))
    }

    @Test
    fun aDatabaseWithoutAVersionIsNotTooOld() {
        assertFalse(DatabaseCompatibility.isTooOld(storedVersion = 0, minSupported = 35))
    }

    @Test
    fun aVersionBelowTheOldestMigrationIsTooOld() {
        assertTrue(DatabaseCompatibility.isTooOld(storedVersion = 34, minSupported = 35))
    }

    @Test
    fun theOldestSupportedVersionAndNewerAreFine() {
        assertFalse(DatabaseCompatibility.isTooOld(storedVersion = 35, minSupported = 35))
        assertFalse(DatabaseCompatibility.isTooOld(storedVersion = 36, minSupported = 35))
    }

    @Test
    fun theOldestSupportedVersionFollowsTheMigrationList() {
        assertTrue(DatabaseCompatibility.minSupportedVersion == WANDER_MIGRATIONS.minOf { it.startVersion })
    }
}
