package com.example.memory.ml

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.example.memory.ml.embedding.EmbeddingEngine
import com.example.memory.ml.llm.GemmaEngine
import com.example.memory.ml.vision.ImageAnalyzer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Model Lifecycle Manager — controls loading/unloading of on-device models.
 *
 * Strategy:
 * - ALWAYS WARM (lightweight): ML Kit models, all-MiniLM-L6-v2 (~50MB runtime)
 * - LOAD ON DEMAND → USE → UNLOAD: Gemma 3 1B (~1.5-2 GB runtime)
 *
 * On 6GB devices: Gemma loads, runs, then unloads to free RAM.
 * On 8GB+ devices: Gemma can stay warm between operations.
 *
 * This prevents OOM kills while keeping critical paths fast.
 */
class ModelLifecycleManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelLifecycle"
        private const val MEMORY_PRESSURE_THRESHOLD_MB = 1500L
    }

    // === Lightweight models — always warm ===

    val embeddingEngine: EmbeddingEngine by lazy {
        EmbeddingEngine(context).also { it.initialize() }
    }

    val imageAnalyzer: ImageAnalyzer by lazy {
        ImageAnalyzer(context)
    }

    // === Heavy model — load/unload on demand ===

    private var gemmaEngine: GemmaEngine? = null
    private val gemmaLock = Mutex()

    /**
     * Execute a block with Gemma loaded.
     * Automatically handles loading and memory-pressure-aware unloading.
     *
     * Usage:
     * ```
     * val summary = modelLifecycle.withGemma { gemma ->
     *     gemma.extractMemory(evidence)
     * }
     * ```
     */
    suspend fun <T> withGemma(block: suspend (GemmaEngine) -> T): T {
        return gemmaLock.withLock {
            val engine = gemmaEngine ?: GemmaEngine(context).also { newEngine ->
                Log.i(TAG, "Loading Gemma 3 1B...")
                newEngine.initialize()
                gemmaEngine = newEngine
                Log.i(TAG, "Gemma 3 1B loaded")
            }
            try {
                block(engine)
            } finally {
                // On low-memory devices: unload after use
                val availMb = getAvailableMemoryMB()
                Log.d(TAG, "Available memory: ${availMb}MB")
                if (availMb < MEMORY_PRESSURE_THRESHOLD_MB) {
                    Log.i(TAG, "Memory pressure (${availMb}MB < ${MEMORY_PRESSURE_THRESHOLD_MB}MB), unloading Gemma")
                    gemmaEngine?.close()
                    gemmaEngine = null
                }
            }
        }
    }

    /**
     * Check if Gemma is currently available without loading it.
     */
    val isGemmaLoaded: Boolean
        get() = gemmaEngine != null

    /**
     * Force unload Gemma to free memory.
     */
    suspend fun unloadGemma() {
        gemmaLock.withLock {
            gemmaEngine?.close()
            gemmaEngine = null
            Log.i(TAG, "Gemma force-unloaded")
        }
    }

    /**
     * Get available device memory in MB.
     */
    private fun getAvailableMemoryMB(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024)
    }

    /**
     * Clean up all models.
     */
    fun close() {
        embeddingEngine.close()
        imageAnalyzer.close()
        gemmaEngine?.close()
        gemmaEngine = null
    }
}
