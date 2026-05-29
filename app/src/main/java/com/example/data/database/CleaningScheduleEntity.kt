package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cleaning_schedules")
data class CleaningScheduleEntity(
    @PrimaryKey val id: String = "DAILY_CLEANUP",
    val hour: Int = 2, // Default 2 AM
    val minute: Int = 0,
    val isEnabled: Boolean = true,
    val intervalDays: Int = 1,
    val cleanSystemCache: Boolean = true,
    val cleanTempLogs: Boolean = true,
    val cleanResidualFiles: Boolean = true,
    val cleanLargeFiles: Boolean = true
)
