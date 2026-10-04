package com.wander.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Migration 41 to 42: jam recaps, mirrored from Agro (see `JamRecapEntity`). */
internal val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `jam_recaps` (
                `id` TEXT NOT NULL,
                `createdAt` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}
