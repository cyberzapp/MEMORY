package com.example.memory.data.vector

import com.example.memory.data.db.MemoryDao
import com.example.memory.ml.embedding.toEmbeddingFloats
import kotlin.math.sqrt

/**
 * On-device semantic search engine.
 *
 * Performs cosine similarity search over all stored embeddings.
 * NO AI model needed — this is pure math on the Local CPU.
 *
 * For < 10K memories, in-memory cosine is ~2ms. This is the MVP path.
 * Upgrade to sqlite-vec for 100K+ memories.
 */
class VectorSearchEngine(private val dao: MemoryDao) {

    /**
     * Search all memories by vector similarity.
     *
     * @param queryVector L2-normalized 384-dim vector from EmbeddingEngine
     * @param topK Maximum number of results to return
     * @return Ranked list of (memoryId, similarityScore) pairs, descending by score
     */
    suspend fun search(queryVector: FloatArray, topK: Int = 10): List<MemoryMatch> {
        // Load all embeddings from Room
        val embeddingRows = dao.getAllEmbeddings()

        if (embeddingRows.isEmpty()) return emptyList()

        // Compute cosine similarity for each memory
        val scored = embeddingRows.mapNotNull { row ->
            val storedVector = row.embedding.toEmbeddingFloats()
            if (storedVector.size != queryVector.size) return@mapNotNull null

            val score = cosineSimilarity(queryVector, storedVector)
            MemoryMatch(
                memoryId = row.id,
                similarityScore = score
            )
        }

        // Sort descending by similarity, take top K
        return scored
            .sortedByDescending { it.similarityScore }
            .take(topK)
            .filter { it.similarityScore > 0.2f } // drop irrelevant noise
    }

    companion object {
        /**
         * Cosine similarity between two vectors.
         * When vectors are L2-normalized, this is just the dot product.
         *
         * This is the entire retrieval engine — 8 lines of math.
         * No AI model. No NPU. No GPU. Just arithmetic.
         */
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            var dot = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denom = sqrt(normA) * sqrt(normB)
            return if (denom < 1e-12f) 0f else dot / denom
        }
    }
}

/**
 * A single search result with its relevance score.
 */
data class MemoryMatch(
    val memoryId: String,
    val similarityScore: Float    // 0.0 to 1.0
)
