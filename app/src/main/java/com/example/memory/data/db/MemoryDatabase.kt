package com.example.memory.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for MEMORY.
 * All user data stored locally — zero cloud sync.
 */
@Database(
    entities = [MemoryEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
}
