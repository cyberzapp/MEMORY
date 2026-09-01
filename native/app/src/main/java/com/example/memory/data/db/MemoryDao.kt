package com.example.memory.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for memories.
 * Provides reactive streams (Flow) for UI observation
 * and suspend functions for background operations.
 */
@Dao
interface MemoryDao {

    // === Timeline queries (reactive) ===

    @Query("SELECT * FROM memories ORDER BY capturedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE capturedAt BETWEEN :start AND :end ORDER BY capturedAt DESC")
    fun getMemoriesByDateRange(start: Long, end: Long): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE processingStatus = 'DONE' ORDER BY capturedAt DESC")
    fun getProcessedMemories(): Flow<List<MemoryEntity>>

    // === Single memory ===

    @Query("SELECT * FROM memories WHERE id = :memoryId")
    suspend fun getMemoryById(memoryId: String): MemoryEntity?

    // === Embedding search support ===

    @Query("SELECT id, embedding FROM memories WHERE embedding IS NOT NULL")
    suspend fun getAllEmbeddings(): List<EmbeddingRow>

    // === Text search fallback ===

    @Query("""
        SELECT * FROM memories 
        WHERE rawOcrText LIKE '%' || :query || '%' 
           OR voiceTranscript LIKE '%' || :query || '%' 
           OR structuredSummary LIKE '%' || :query || '%'
           OR rawDetectedObjects LIKE '%' || :query || '%'
        ORDER BY capturedAt DESC
    """)
    suspend fun searchByText(query: String): List<MemoryEntity>

    // === Unprocessed memories for background worker ===

    @Query("SELECT * FROM memories WHERE processingStatus = 'PENDING' OR processingStatus = 'NEEDS_RETRY' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextUnprocessedMemory(): MemoryEntity?

    @Query("SELECT COUNT(*) FROM memories WHERE processingStatus = 'PENDING' OR processingStatus = 'NEEDS_RETRY'")
    suspend fun getUnprocessedCount(): Int

    // === CRUD ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :memoryId")
    suspend fun deleteMemoryById(memoryId: String)

    // === Temporal Context — Memory Graph ===

    /**
     * Fetch all processed memories within a time window around a center timestamp.
     * Used to build temporal context for contextual search answers.
     */
    @Query("""
        SELECT * FROM memories 
        WHERE processingStatus = 'DONE' 
          AND capturedAt BETWEEN :centerMs - :windowMs AND :centerMs + :windowMs 
        ORDER BY capturedAt ASC
    """)
    suspend fun getTemporalContext(centerMs: Long, windowMs: Long): List<MemoryEntity>

    // === Reminder DAO ===

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY triggerAt ASC")
    fun getActiveReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY triggerAt DESC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE memoryId = :memoryId")
    suspend fun getRemindersForMemory(memoryId: String): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isCompleted = 1 WHERE id = :reminderId")
    suspend fun completeReminder(reminderId: String)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)
}
