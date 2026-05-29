package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CleanupLogEntity::class,
        AppExceptionEntity::class,
        CleaningScheduleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cleanupLogDao(): CleanupLogDao
    abstract fun appExceptionDao(): AppExceptionDao
    abstract fun cleaningScheduleDao(): CleaningScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cache_cleaner_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
