package com.example.memory.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.memory.data.db.MemoryDao
import com.example.memory.data.db.MemoryEntity
import com.example.memory.data.db.ProcessingStatus
import com.example.memory.ml.embedding.toEmbeddingBytes
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.memory.data.db.DetectedObjectJson
import com.example.memory.data.db.ImageLabelJson
import java.io.File
import java.io.FileOutputStream

/**
 * Background processing worker implementing the 5-stage media lifecycle:
 *
 * 1. CAPTURE — Original photo/audio already saved (by CaptureScreen)
 * 2. PROCESS — ML Kit (OCR + Object Detection + Labels) + embedding + Gemma summary
 * 3. VERIFY — Confirm extraction succeeded (embedding non-null, minimum signal present)
 * 4. COMPRESS — Generate evidence thumbnail (480px, ~30-60 KB)
 * 5. DELETE ORIGINAL — Remove full-resolution media (only after verify passes)
 *
 * NEVER deletes original before confirming memory was successfully created.
 */
class MemoryProcessingWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MemoryWorker"
        const val KEY_MEMORY_ID = "memory_id"
        const val THUMBNAIL_MAX_SIZE = 480
    }

    override suspend fun doWork(): Result {
        val memoryId = inputData.getString(KEY_MEMORY_ID) ?: return Result.failure()
        Log.i(TAG, "Processing memory: $memoryId")

        // Get dependencies (manual DI in Worker — Hilt WorkManager integration can be added)
        val database = androidx.room.Room.databaseBuilder(
            context, com.example.memory.data.db.MemoryDatabase::class.java, "memory_database"
        ).build()
        val dao = database.memoryDao()
        val modelLifecycle = ModelLifecycleManager(context)

        try {
            // Load memory
            val memory = dao.getMemoryById(memoryId) ?: run {
                Log.e(TAG, "Memory not found: $memoryId")
                return Result.failure()
            }

            // Mark as processing
            dao.updateMemory(memory.copy(processingStatus = ProcessingStatus.PROCESSING))

            // === STAGE 2: PROCESS ===
            val evidence = when (memory.type) {
                "PHOTO" -> processPhoto(memory, modelLifecycle)
                "VOICE" -> processVoice(memory)
                else -> null
            }

            if (evidence == null) {
                Log.e(TAG, "Failed to build evidence bundle")
                dao.updateMemory(memory.copy(processingStatus = ProcessingStatus.NEEDS_RETRY))
                return Result.retry()
            }

            // Generate embedding (THE CRITICAL STEP)
            val embeddingText = evidence.toEmbeddingText()
            Log.d(TAG, "Embedding text: $embeddingText")
            val embedding = modelLifecycle.embeddingEngine.embed(embeddingText)

            // Generate Gemma summary (nice to have — fallback works)
            val summary = try {
                modelLifecycle.withGemma { gemma ->
                    gemma.extractMemory(evidence.toGemmaPrompt())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemma extraction failed, using fallback", e)
                ""
            }
            val finalSummary = summary.ifBlank { evidence.fallbackSummary() }

            // === STAGE 3: VERIFY ===
            if (embedding.size != 384 || !evidence.hasMinimumSignal()) {
                Log.e(TAG, "Verification failed: embedding=${embedding.size}, signal=${evidence.hasMinimumSignal()}")
                dao.updateMemory(memory.copy(processingStatus = ProcessingStatus.NEEDS_RETRY))
                return Result.retry()
            }

            // === STAGE 4: COMPRESS ===
            val thumbnailPath = if (memory.type == "PHOTO" && memory.originalMediaPath != null) {
                generateThumbnail(memory.originalMediaPath, memoryId)
            } else null

            // Serialize ML Kit results to JSON
            val objectsJson = Json.encodeToString(
                evidence.objects.map { DetectedObjectJson(it.label, it.confidence) }
            )
            val labelsJson = Json.encodeToString(
                evidence.imageLabels.map { ImageLabelJson(it.text, it.confidence) }
            )

            // Update Room with all extracted data
            dao.updateMemory(memory.copy(
                rawOcrText = evidence.ocrText,
                rawDetectedObjects = objectsJson,
                rawImageLabels = labelsJson,
                voiceTranscript = evidence.transcript ?: memory.voiceTranscript,
                structuredSummary = finalSummary,
                embedding = embedding.toEmbeddingBytes(),
                thumbnailPath = thumbnailPath ?: memory.thumbnailPath,
                processingStatus = ProcessingStatus.DONE,
                updatedAt = System.currentTimeMillis()
            ))

            Log.i(TAG, "Memory processed successfully: $memoryId")
            Log.i(TAG, "Summary: $finalSummary")

            // === STAGE 5: DELETE ORIGINAL ===
            // For now, we keep originals. User setting can enable auto-delete.
            // This is safe because we verified in Stage 3.

            return Result.success(workDataOf(KEY_MEMORY_ID to memoryId))

        } catch (e: Exception) {
            Log.e(TAG, "Processing failed", e)
            try {
                val memory = dao.getMemoryById(memoryId)
                if (memory != null) {
                    dao.updateMemory(memory.copy(processingStatus = ProcessingStatus.NEEDS_RETRY))
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to update status", e2)
            }
            return Result.retry()
        } finally {
            modelLifecycle.close()
            database.close()
        }
    }

    private suspend fun processPhoto(
        memory: MemoryEntity,
        modelLifecycle: ModelLifecycleManager
    ): EvidenceBundle? {
        val mediaPath = memory.originalMediaPath ?: return null
        val bitmap = BitmapFactory.decodeFile(mediaPath) ?: return null

        val visionResult = modelLifecycle.imageAnalyzer.analyze(bitmap)

        return EvidenceBundle(
            timestamp = memory.capturedAt,
            latitude = memory.latitude,
            longitude = memory.longitude,
            locationName = memory.locationName,
            objects = visionResult.detectedObjects,
            ocrText = visionResult.ocrText,
            ocrBlocks = visionResult.ocrBlocks,
            imageLabels = visionResult.imageLabels,
            transcript = memory.voiceTranscript,
            captureType = CaptureType.PHOTO
        )
    }

    private fun processVoice(memory: MemoryEntity): EvidenceBundle {
        // Voice transcript was already captured by SpeechRecognizer at capture time
        return EvidenceBundle(
            timestamp = memory.capturedAt,
            latitude = memory.latitude,
            longitude = memory.longitude,
            locationName = memory.locationName,
            transcript = memory.voiceTranscript,
            captureType = CaptureType.VOICE
        )
    }

    /**
     * Generate a compressed thumbnail from the original photo.
     * 480px max dimension, JPEG quality 80 → ~30-60 KB.
     */
    private fun generateThumbnail(originalPath: String, memoryId: String): String? {
        return try {
            val original = BitmapFactory.decodeFile(originalPath) ?: return null

            val scale = THUMBNAIL_MAX_SIZE.toFloat() / maxOf(original.width, original.height)
            val thumbWidth = (original.width * scale).toInt()
            val thumbHeight = (original.height * scale).toInt()

            val thumbnail = Bitmap.createScaledBitmap(original, thumbWidth, thumbHeight, true)

            val thumbDir = File(context.filesDir, "thumbnails")
            thumbDir.mkdirs()
            val thumbFile = File(thumbDir, "${memoryId}.jpg")

            FileOutputStream(thumbFile).use { out ->
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            thumbnail.recycle()
            if (thumbnail != original) original.recycle()

            thumbFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Thumbnail generation failed", e)
            null
        }
    }
}
