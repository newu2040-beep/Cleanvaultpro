package com.example.data.repository

import com.example.data.api.BackupLogPayload
import com.example.data.api.CloudBackupService
import com.example.data.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CacheCleanerRepository(
    private val cleanupLogDao: CleanupLogDao,
    private val appExceptionDao: AppExceptionDao,
    private val cleaningScheduleDao: CleaningScheduleDao,
    private val cloudBackupService: CloudBackupService = CloudBackupService.create()
) {
    // Cleanup Logs
    val allLogs: Flow<List<CleanupLogEntity>> = cleanupLogDao.getAllLogs()

    suspend fun insertLog(log: CleanupLogEntity): Long = withContext(Dispatchers.IO) {
        cleanupLogDao.insertLog(log)
    }

    suspend fun updateLog(log: CleanupLogEntity) = withContext(Dispatchers.IO) {
        cleanupLogDao.updateLog(log)
    }

    suspend fun deleteLogById(id: Long) = withContext(Dispatchers.IO) {
        cleanupLogDao.deleteLogById(id)
    }

    // App Exceptions
    val allExceptions: Flow<List<AppExceptionEntity>> = appExceptionDao.getAllExceptions()

    suspend fun insertException(exception: AppExceptionEntity) = withContext(Dispatchers.IO) {
        appExceptionDao.insertException(exception)
    }

    suspend fun deleteException(exception: AppExceptionEntity) = withContext(Dispatchers.IO) {
        appExceptionDao.deleteException(exception)
    }

    suspend fun getExcludedPackages() = withContext(Dispatchers.IO) {
        appExceptionDao.getExcludedPackagesSync()
    }

    // Schedule
    fun getSchedule(id: String = "DAILY_CLEANUP"): Flow<CleaningScheduleEntity?> {
        return cleaningScheduleDao.getSchedule(id)
    }

    suspend fun getScheduleSync(id: String = "DAILY_CLEANUP") = withContext(Dispatchers.IO) {
        cleaningScheduleDao.getScheduleSync(id)
    }

    suspend fun insertSchedule(schedule: CleaningScheduleEntity) = withContext(Dispatchers.IO) {
        cleaningScheduleDao.insertSchedule(schedule)
    }

    // Backup
    suspend fun performBackup(log: CleanupLogEntity): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = BackupLogPayload(
                id = log.id,
                timestamp = log.timestamp,
                cleanedBytes = log.cleanedBytes,
                scanDurationMs = log.scanDurationMs,
                junkDetailsJson = log.junkDetailsJson,
                status = log.status
            )
            val response = cloudBackupService.backupLog(payload)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                // Success! Record URL returned by httpbin echo
                val backupUrl = body.url ?: "https://httpbin.org/post"
                val updatedLog = log.copy(
                    backupStatus = "COMPLETED",
                    backupUrl = backupUrl
                )
                cleanupLogDao.updateLog(updatedLog)
                Result.success(backupUrl)
            } else {
                val updatedLog = log.copy(backupStatus = "FAILED")
                cleanupLogDao.updateLog(updatedLog)
                Result.failure(Exception("Cloud backup returned error code: ${response.code()}"))
            }
        } catch (e: Exception) {
            val updatedLog = log.copy(backupStatus = "FAILED")
            cleanupLogDao.updateLog(updatedLog)
            Result.failure(e)
        }
    }
}
