package com.wander.android.core.database

import androidx.room.migration.Migration

/**
 * Every Room migration still supported. Room applies whichever ones a given database still needs.
 *
 * The oldest supported database is the start version of the oldest migration here (see
 * [DatabaseCompatibility.minSupportedVersion]); anything older has no path to the current schema
 * and is offered a reset instead. Earlier steps were dropped on purpose — they remain in git history.
 */
val WANDER_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43)
