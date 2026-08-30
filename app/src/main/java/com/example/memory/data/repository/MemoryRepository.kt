package com.example.memory.data.repository

import android.util.Log
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
 *
 * The search pipeline implements TEMPORAL CONTEXT GRAPH:
 * query → embed → KNN → expand temporal window → filter by semantic relevance → build narrative
 */
class MemoryRepository(
    private val dao: MemoryDao,
    private val vectorSearch: VectorSearchEngine,
    private val modelLifecycle: ModelLifecycleManager,
    private val reminderManager: ReminderManager
) {
    companion object {
        private const val TAG = "MemoryRepository"
        private const val CONTEXT_WINDOW_MS = 15L * 60 * 1000  // ±15 minutes
        private const val CONTEXT_SIMILARITY_THRESHOLD = 0.25f  // min similarity to include in session
        private const val SESSION_GAP_MS = 10L * 60 * 1000     // 10 min gap = new session
    }

    // === Reactive streams for UI ===

    val allMemories: Flow<List<MemoryEntity>> = dao.getAllMemories()
    val processedMemories: Flow<List<MemoryEntity>> = dao.getProcessedMemories()
    val activeReminders: Flow<List<ReminderEntity>> = dao.getActiveReminders()

    // === Memory CRUD ===

    suspend fun insertMemory(memory: MemoryEntity) = dao.insertMemory(memory)
    suspend fun updateMemory(memory: MemoryEntity) = dao.updateMemory(memory)
    suspend fun deleteMemory(memory: MemoryEntity) = dao.deleteMemory(memory)
    suspend fun getMemoryById(id: String) = dao.getMemoryById(id)

    // === The Temporal Context Search Pipeline ===

    /**
     * Semantic search with temporal context graph.
     *
     * 1. Embed the query (MiniLM — ~10ms)
     * 2. Vector search top matches (cosine KNN — ~2ms)
     * 3. For each top match, expand ±15 min temporal window
     * 4. Filter neighbors by semantic relevance to the query
     * 5. Group into sessions (memories clustered within 10 min)
     * 6. Build narrative answer with temporal relationships
     *
     * This is what makes "What did sir say about integration?" return
     * the voice note AND the whiteboard photo AND the notebook — because
     * they happened together and are semantically related.
     */
    suspend fun searchMemories(query: String): SearchResult {
        val embeddingEngine = modelLifecycle.embeddingEngine

        // Step 1: Embed the query
        val queryVector = embeddingEngine.embed(query)

        // Step 2: Vector search (pure math — no AI model)
        val matches = vectorSearch.search(queryVector, topK = 5)

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
                    RankedMemory(entity, 0.5f)
                },
                sessions = emptyList()
            )
        }

        // Step 3: Load full memory details for top matches
        val rankedMemories = matches.mapNotNull { match ->
            val entity = dao.getMemoryById(match.memoryId)
            entity?.let { RankedMemory(it, match.similarityScore) }
        }

        // Step 4: Expand temporal context around top matches
        // Collect all unique temporal neighbors
        val contextMemoryIds = mutableSetOf<String>()
        val allContextMemories = mutableListOf<MemoryEntity>()

        for (rm in rankedMemories.take(3)) { // expand around top 3 matches
            val neighbors = dao.getTemporalContext(rm.entity.capturedAt, CONTEXT_WINDOW_MS)
            for (neighbor in neighbors) {
                if (neighbor.id !in contextMemoryIds) {
                    // Semantic relevance check: is this neighbor related to the query?
                    val isRelevant = if (neighbor.embedding != null) {
                        val neighborVector = neighbor.embedding.toEmbeddingFloats()
                        if (neighborVector.size == queryVector.size) {
                            VectorSearchEngine.cosineSimilarity(queryVector, neighborVector) > CONTEXT_SIMILARITY_THRESHOLD
                        } else true // include if we can't check
                    } else {
                        // No embedding — check text overlap as fallback
                        val neighborText = listOfNotNull(
                            neighbor.structuredSummary,
                            neighbor.rawOcrText,
                            neighbor.voiceTranscript
                        ).joinToString(" ").lowercase()
                        val queryWords = query.lowercase().split(" ")
                        queryWords.any { word -> word.length > 2 && neighborText.contains(word) }
                    }

                    if (isRelevant || rankedMemories.any { it.entity.id == neighbor.id }) {
                        contextMemoryIds.add(neighbor.id)
                        allContextMemories.add(neighbor)
                    }
                }
            }
        }

        // Step 5: Group into sessions (cluster by time proximity)
        val sessions = buildSessions(allContextMemories)
        Log.d(TAG, "Search found ${rankedMemories.size} direct matches, ${allContextMemories.size} context memories, ${sessions.size} sessions")

        // Step 6: Generate narrative answer with temporal context
        val answer = try {
            modelLifecycle.withGemma { gemma ->
                gemma.generateAnswer(
                    question = query,
                    memorySummaries = buildNarrativeContext(rankedMemories, sessions)
                )
            }
        } catch (e: Exception) {
            // Fallback narrative answer without Gemma
            buildFallbackNarrative(query, rankedMemories, sessions)
        }

        return SearchResult(answer, rankedMemories, sessions)
    }

    /**
     * Build sessions from a flat list of memories.
     * A session is a cluster of memories where each is within SESSION_GAP_MS of the next.
     */
    private fun buildSessions(memories: List<MemoryEntity>): List<MemorySession> {
        if (memories.isEmpty()) return emptyList()

        val sorted = memories.sortedBy { it.capturedAt }
        val sessions = mutableListOf<MemorySession>()
        var currentGroup = mutableListOf(sorted.first())

        for (i in 1 until sorted.size) {
            val gap = sorted[i].capturedAt - sorted[i - 1].capturedAt
            if (gap > SESSION_GAP_MS) {
                // Gap too large — start a new session
                sessions.add(MemorySession(
                    memories = currentGroup.toList(),
                    startTime = currentGroup.first().capturedAt,
                    endTime = currentGroup.last().capturedAt
                ))
                currentGroup = mutableListOf(sorted[i])
            } else {
                currentGroup.add(sorted[i])
            }
        }
        // Don't forget the last group
        sessions.add(MemorySession(
            memories = currentGroup.toList(),
            startTime = currentGroup.first().capturedAt,
            endTime = currentGroup.last().capturedAt
        ))

        return sessions
    }

    /**
     * Build ranked memory info list that includes temporal context for Gemma.
     */
    private fun buildNarrativeContext(
        directMatches: List<RankedMemory>,
        sessions: List<MemorySession>
    ): List<RankedMemoryInfo> {
        val result = mutableListOf<RankedMemoryInfo>()
        var rank = 1

        for (session in sessions) {
            for (memory in session.memories) {
                val directMatch = directMatches.find { it.entity.id == memory.id }
                result.add(RankedMemoryInfo(
                    rank = rank++,
                    summary = memory.structuredSummary
                        ?: memory.rawOcrText?.take(100)
                        ?: memory.voiceTranscript?.take(100)
                        ?: "Memory",
                    location = memory.locationName,
                    time = formatTime(memory.capturedAt),
                    score = directMatch?.similarityScore ?: 0.3f,
                    type = memory.type
                ))
            }
        }

        return result
    }

    /**
     * Template-based narrative answer with temporal relationships.
     * Used when Gemma is not available.
     *
     * This is the heart of the contextual recall system.
     * Instead of "Found: integration notes", it produces:
     * "At 10:32 AM, you recorded that your professor said the integration
     *  topic would be in the exam. You had photographed the related equation
     *  two minutes earlier."
     */
    private fun buildFallbackNarrative(
        query: String,
        directMatches: List<RankedMemory>,
        sessions: List<MemorySession>
    ): String {
        if (directMatches.isEmpty()) return "I don't have a memory about that."

        val top = directMatches.first()
        val topSummary = top.entity.structuredSummary
            ?: top.entity.rawOcrText?.take(100)
            ?: top.entity.voiceTranscript?.take(100)
            ?: "a memory"
        val topTime = formatTime(top.entity.capturedAt)
        val topType = typeVerb(top.entity.type)

        // Find the session containing the top match
        val relevantSession = sessions.find { session ->
            session.memories.any { it.id == top.entity.id }
        }

        return buildString {
            // Primary match
            append("At $topTime, you $topType: \"$topSummary\"")
            if (top.entity.locationName != null) {
                append(" at ${top.entity.locationName}")
            }
            append(".")

            // Add temporal context from the session
            if (relevantSession != null && relevantSession.memories.size > 1) {
                val others = relevantSession.memories.filter { it.id != top.entity.id }
                if (others.isNotEmpty()) {
                    append("\n\nRelated moments:")
                    for (other in others) {
                        val timeDiff = other.capturedAt - top.entity.capturedAt
                        val relativeTime = formatRelativeTime(timeDiff)
                        val otherSummary = other.structuredSummary
                            ?: other.rawOcrText?.take(60)
                            ?: other.voiceTranscript?.take(60)
                            ?: "a memory"
                        val otherVerb = typeVerb(other.type)
                        append("\n• $relativeTime, you $otherVerb: \"$otherSummary\"")
                    }
                }
            }
        }
    }

    /**
     * Human-readable verb for memory type.
     */
    private fun typeVerb(type: String): String = when (type) {
        "PHOTO" -> "photographed"
        "VOICE" -> "recorded a voice note"
        "NOTE" -> "wrote"
        else -> "captured"
    }

    /**
     * Human-readable relative time: "2 minutes earlier", "5 minutes later"
     */
    private fun formatRelativeTime(diffMs: Long): String {
        val absDiff = kotlin.math.abs(diffMs)
        val minutes = (absDiff / 60000).toInt()
        val direction = if (diffMs < 0) "earlier" else "later"

        return when {
            minutes < 1 -> "Moments $direction"
            minutes == 1 -> "1 minute $direction"
            minutes < 60 -> "$minutes minutes $direction"
            else -> {
                val hours = minutes / 60
                if (hours == 1) "1 hour $direction" else "$hours hours $direction"
            }
        }
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
 * A temporal session — a group of memories captured close together in time
 * that are semantically related to the search query.
 */
data class MemorySession(
    val memories: List<MemoryEntity>,
    val startTime: Long,
    val endTime: Long
)

/**
 * Full search result: conversational answer + ranked evidence + session context.
 */
data class SearchResult(
    val answer: String,
    val memories: List<RankedMemory>,
    val sessions: List<MemorySession> = emptyList()
)

/**
 * A memory with its search relevance score.
 */
data class RankedMemory(
    val entity: MemoryEntity,
    val similarityScore: Float
)
