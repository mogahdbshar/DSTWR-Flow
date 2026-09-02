package com.dstwr.flow.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Database migrations kept explicit so existing local data is preserved. */
object FlowDatabaseMigrations {
    val V1_TO_V2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE app_policies ADD COLUMN networkScope TEXT NOT NULL DEFAULT 'ALL'"
            )
        }
    }
}
