package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cleanup_logs")
data class CleanupLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val cleanedBytes: Long,
    val scanDurationMs: Long,
    val junkDetailsJson: String, // JSON mapping of categories to sizes
    val status: String,          // SUCCESS, FAILED
    val backupStatus: String,    // PENDING, COMPLETED, FAILED
    val backupUrl: String? = null,
    val pdfUriPath: String? = null
)
