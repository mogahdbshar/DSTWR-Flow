package com.dstwr.flow.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "app_policies", primaryKeys = ["packageName"])
data class AppPolicyEntity(
    val packageName: String,
    val blocked: Boolean = false,
    val uploadLimitBytesPerSecond: Long = 0L,
    val downloadLimitBytesPerSecond: Long = 0L,
    val dailyQuotaBytes: Long = 0L,
    val monthlyQuotaBytes: Long = 0L,
    val scheduleEnabled: Boolean = false,
    val scheduleStartMinutes: Int = 0,
    val scheduleEndMinutes: Int = 1439,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface AppPolicyDao {
    @Query("SELECT * FROM app_policies ORDER BY packageName") suspend fun getAll(): List<AppPolicyEntity>
    @Query("SELECT * FROM app_policies WHERE packageName = :packageName LIMIT 1") suspend fun get(packageName: String): AppPolicyEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(policy: AppPolicyEntity)
    @Query("DELETE FROM app_policies WHERE packageName = :packageName") suspend fun delete(packageName: String)
}

@Entity(tableName = "usage_snapshots", primaryKeys = ["packageName", "startTime", "endTime", "networkType"])
data class UsageSnapshotEntity(
    val packageName: String,
    val startTime: Long,
    val endTime: Long,
    val networkType: Int,
    val rxBytes: Long,
    val txBytes: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface UsageSnapshotDao {
    @Query("SELECT * FROM usage_snapshots WHERE startTime >= :since ORDER BY startTime ASC")
    suspend fun since(since: Long): List<UsageSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(snapshot: UsageSnapshotEntity)
    @Query("DELETE FROM usage_snapshots WHERE startTime < :before") suspend fun deleteBefore(before: Long)
}

@Database(entities = [AppPolicyEntity::class, UsageSnapshotEntity::class], version = 1, exportSchema = false)
abstract class FlowDatabase : RoomDatabase() {
    abstract fun appPolicyDao(): AppPolicyDao
    abstract fun usageSnapshotDao(): UsageSnapshotDao
}
