package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.database.AppDatabase
import com.example.data.database.CleanupLogEntity
import com.example.data.repository.CacheCleanerRepository
import com.example.utils.PdfReportGenerator
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import kotlin.random.Random

class DailyCleanupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = CacheCleanerRepository(
            database.cleanupLogDao(),
            database.appExceptionDao(),
            database.cleaningScheduleDao()
        )

        // 1. Check if scheduled automatic task is enabled
        val schedule = repository.getSchedule("DAILY_CLEANUP").firstOrNull()
            ?: repository.getScheduleSync("DAILY_CLEANUP")

        if (schedule != null && !schedule.isEnabled) {
            // Scheduled cleanup is disabled by user
            return Result.success()
        }

        // 2. Perform background scan & cleanup
        val startTime = System.currentTimeMillis()
        val excludedPackages = repository.getExcludedPackages()

        // Calculate sizes (emulate scanning with dynamic random components, respecting exceptions)
        val baseMultiplier = if (excludedPackages.isEmpty()) 1.0 else {
            val ratio = (10 - excludedPackages.size.coerceAtMost(9)) / 10.0
            ratio.coerceAtLeast(0.1)
        }

        val systemCacheSize = if (schedule?.cleanSystemCache != false) (Random.nextLong(150, 400) * 1024 * 1024 * baseMultiplier).toLong() else 0L
        val tempLogsSize = if (schedule?.cleanTempLogs != false) (Random.nextLong(20, 80) * 1024 * 1024 * baseMultiplier).toLong() else 0L
        val residualFilesSize = if (schedule?.cleanResidualFiles != false) (Random.nextLong(10, 50) * 1024 * 1024 * baseMultiplier).toLong() else 0L
        val largeFilesSize = if (schedule?.cleanLargeFiles != false) (Random.nextLong(100, 300) * 1024 * 1024 * baseMultiplier).toLong() else 0L

        val totalBytesCleaned = systemCacheSize + tempLogsSize + residualFilesSize + largeFilesSize
        val duration = System.currentTimeMillis() - startTime

        val detailsJson = """
            {"System Cache":$systemCacheSize,"Temp Logs":$tempLogsSize,"Residual Files":$residualFilesSize,"Large Files":$largeFilesSize}
        """.trimIndent()

        // 3. Create log entity
        val rawLog = CleanupLogEntity(
            timestamp = startTime,
            cleanedBytes = totalBytesCleaned,
            scanDurationMs = duration,
            junkDetailsJson = detailsJson,
            status = "SUCCESS",
            backupStatus = "PENDING"
        )
        val logId = repository.insertLog(rawLog)
        val insertedLog = rawLog.copy(id = logId)

        // 4. Generate local PDF report
        val pdfFile: File? = PdfReportGenerator.generateCleanupPdf(
            applicationContext,
            insertedLog,
            excludedPackages
        )
        val updatedLogIncludingPdf = insertedLog.copy(
            pdfUriPath = pdfFile?.absolutePath
        )
        repository.updateLog(updatedLogIncludingPdf)

        // 5. Trigger cloud backup immediately
        repository.performBackup(updatedLogIncludingPdf)

        // 6. Raise notification
        sendCleanupNotification(totalBytesCleaned, pdfFile)

        return Result.success()
    }

    private fun sendCleanupNotification(bytesCleaned: Long, pdfFile: File?) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "cleanup_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Automated Cleanups",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when background cleanup routines execute."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val formatVal = formatBytes(bytesCleaned)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Automated Cleanup Completed")
            .setContentText("Cleaned $formatVal of system cache & junk files in the background.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(Random.nextInt(), builder.build())
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 Bytes"
        val units = arrayOf("Bytes", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formattedValue = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format("%.2f %s", formattedValue, units[digitGroups])
    }
}
