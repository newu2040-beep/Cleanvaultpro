package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_exceptions")
data class AppExceptionEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isExcluded: Boolean = true,
    val dateExcluded: Long = System.currentTimeMillis()
)
