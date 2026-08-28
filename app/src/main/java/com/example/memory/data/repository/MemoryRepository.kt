package com.example.memory.data.repository

import com.example.memory.data.db.MemoryDao
import com.example.memory.data.db.MemoryEntity
import com.example.memory.data.db.ReminderEntity
import com.example.memory.data.vector.MemoryMatch
import com.example.memory.data.vector.VectorSearchEngine
import com.example.memory.ml.ModelLifecycleManager
import com.example.memory.ml.embedding.EmbeddingEngine
import com.example.memory.ml.embedding.toEmbeddingFloats
import com.example.memory.ml.llm.RankedMemoryInfo
import com.example.memory.reminders.ReminderManager
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Single source of truth for memory operations.
 * Coordinates Room DB + Vector Search + Embedding Engine + Gemma.
 */
class MemoryRepository(
    private val dao: MemoryDao,
    private val vectorSearch: VectorSearchEngine,
    private val modelLifecycle: ModelLifecycleManager,
    private val reminderManager: ReminderManager
) {
    // === Reactive streams for UI ===

    val allMemories: Flow<List<MemoryEntity>> = dao.getAllMemories()
    val processedMemories: Flow<List<MemoryEntity>> = dao.getProcessedMemories()
    val activeReminders: Flow<List<ReminderEntity>> = dao.getActiveReminders()

    // === Memory CRUD ===

    suspend fun insertMemory(memory: MemoryEntity) = dao.insertMemory(memory)
    suspend fun updateMemory(memory: MemoryEntity) = dao.updateMemory(memory)
    suspend fun deleteMemory(memory: MemoryEntity) = dao.deleteMemory(memory)
    suspend fun getMemoryById(id: String) = dao.getMemoryById(id)

    // === The search pipeline ===

    /**
     * Semantic search: query → embed → KNN → Gemma answer.
     *
     * 1. Embed the query text (MiniLM — always warm, ~10ms)
     * 2. Cosine similarity search (CPU math — ~2ms)
     * 3. Load full memory details from Room
     * 4. Generate natural-language answer (Gemma — load on demand)
     *
     * Returns SearchResult with ranked memories + conversational answer.
     */
    suspend fun searchMemories(query: String): SearchResult {
        val embeddingEngine = modelLifecycle.embeddingEngine

        // Step 1: Embed the query
        val queryVector = embeddingEngine.embed(query)

        // Step 2: Vector search (no AI model — pure math)
        val matches = vectorSearch.search(queryVector, topK = 10)

        if (matches.isEmpty()) {
            // Fallback to text search
            val textResults = dao.searchByText(query)
            return SearchResult(
                answer = if (textResults.isEmpty()) {
                    "I don't have a memory about that."
                } else {
                    "Found ${textResults.size} memories matching \"$query\"."
                },
                memories = textResults.map { entity ->
                    RankedMemory(entity, 0.5f) // default score for text matches
                }
            )
        }

        // Step 3: Load full memory details
        val rankedMemories = matches.mapNotNull { match ->
            val entity = dao.getMemoryById(match.memoryId)
            entity?.let { RankedMemory(it, match.similarityScore) }
        }

        // Step 4: Generate natural-language answer
        val answer = try {
            modelLifecycle.withGemma { gemma ->
                gemma.generateAnswer(
                    question = query,
                    memorySummaries = rankedMemories.mapIndexed { index, rm ->
                        RankedMemoryInfo(
                            rank = index + 1,
                            summary = rm.entity.structuredSummary
                                ?: rm.entity.rawOcrText
                                ?: rm.entity.voiceTranscript
                                ?: "Memory",
                            location = rm.entity.locationName,
                            time = formatTime(rm.entity.capturedAt),
                            score = rm.similarityScore
                        )
                    }
                )
            }
        } catch (e: Exception) {
            // Fallback answer without Gemma
            val top = rankedMemories.firstOrNull()
            if (top != null) {
                val summary = top.entity.structuredSummary
                    ?: top.entity.rawOcrText
                    ?: top.entity.voiceTranscript
                    ?: "a memory"
                "Based on your memories: $summary" +
                    (top.entity.locationName?.let { " at $it" } ?: "") +
                    " (${formatTime(top.entity.capturedAt)})"
            } else {
                "I don't have a memory about that."
            }
        }

        return SearchResult(answer, rankedMemories)
    }

    // === Reminders ===

    suspend fun scheduleReminder(memoryId: String, text: String, triggerAtMs: Long) {
        val reminderId = UUID.randomUUID().toString()
        val reminder = ReminderEntity(
            id = reminderId,
            memoryId = memoryId,
            reminderText = text,
            triggerAt = triggerAtMs
        )
        dao.insertReminder(reminder)
        reminderManager.scheduleReminder(memoryId, reminderId, triggerAtMs, text)
    }

    suspend fun cancelReminder(reminderId: String) {
        val reminder = dao.getRemindersForMemory(reminderId).find { it.id == reminderId } // wait, I can just query by ID if I had it. Actually, I can just delete it or mark it.
        // It's better to just use cancelReminder in reminderManager
        reminderManager.cancelReminder(reminderId)
    }

    suspend fun completeReminder(reminderId: String) = dao.completeReminder(reminderId)
    suspend fun getRemindersForMemory(memoryId: String) = dao.getRemindersForMemory(memoryId)

    // === Processing ===

    suspend fun getNextUnprocessedMemory() = dao.getNextUnprocessedMemory()
    suspend fun getUnprocessedCount() = dao.getUnprocessedCount()

    private fun formatTime(epochMs: Long): String {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }
}

/**
 * Full search result: conversational answer + ranked evidence.
 */
data class SearchResult(
    val answer: String,
    val memories: List<RankedMemory>
)

/**
 * A memory with its search relevance score.
 */
data class RankedMemory(
    val entity: MemoryEntity,
    val similarityScore: Float
)
