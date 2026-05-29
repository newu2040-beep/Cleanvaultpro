package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CleanupLogDao {
    @Query("SELECT * FROM cleanup_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<CleanupLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CleanupLogEntity): Long

    @Update
    suspend fun updateLog(log: CleanupLogEntity)

    @Query("SELECT * FROM cleanup_logs WHERE backupStatus = 'PENDING'")
    suspend fun getPendingBackupLogs(): List<CleanupLogEntity>

    @Query("DELETE FROM cleanup_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)
}

@Dao
interface AppExceptionDao {
    @Query("SELECT * FROM app_exceptions ORDER BY appName ASC")
    fun getAllExceptions(): Flow<List<AppExceptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertException(exception: AppExceptionEntity)

    @Query("SELECT packageName FROM app_exceptions WHERE isExcluded = 1")
    suspend fun getExcludedPackagesSync(): List<String>

    @Delete
    suspend fun deleteException(exception: AppExceptionEntity)
}

@Dao
interface CleaningScheduleDao {
    @Query("SELECT * FROM cleaning_schedules WHERE id = :id LIMIT 1")
    fun getSchedule(id: String): Flow<CleaningScheduleEntity?>

    @Query("SELECT * FROM cleaning_schedules WHERE id = :id LIMIT 1")
    suspend fun getScheduleSync(id: String): CleaningScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: CleaningScheduleEntity)
}
