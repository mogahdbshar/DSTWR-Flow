package com.dstwr.flow.data.local

import android.content.Context
import androidx.room.Room

object FlowDatabaseProvider {
    @Volatile private var instance: FlowDatabase? = null

    fun get(context: Context): FlowDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            FlowDatabase::class.java,
            "dstwr_flow.db"
        )
            .addMigrations(FlowDatabaseMigrations.V1_TO_V2)
            .build()
            .also { instance = it }
    }
}
