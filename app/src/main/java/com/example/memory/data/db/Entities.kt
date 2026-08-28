package com.example.memory.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Core memory entity — represents a single captured moment.
 * After ML processing, this contains extracted intelligence
 * (objects, text, transcript, summary) plus the embedding vector.
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val type: String,                      // "PHOTO", "VOICE", "NOTE"
    val capturedAt: Long,                  // epoch ms
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,
    val thumbnailPath: String? = null,     // compressed evidence crop (~30-60 KB)
    val originalMediaPath: String? = null,
    val isMediaRetained: Boolean = true,   // false after verified deletion
    val rawOcrText: String? = null,        // ML Kit Text Recognition output
    val rawDetectedObjects: String? = null,// JSON: [{"label":"charger","confidence":0.92}]
    val rawImageLabels: String? = null,    // JSON: [{"text":"Electronics","confidence":0.89}]
    val voiceTranscript: String? = null,   // SpeechRecognizer output
    val structuredSummary: String? = null, // Gemma-generated one-liner
    val embedding: ByteArray? = null,      // 384 floats → 1,536 bytes BLOB
    val processingStatus: String = ProcessingStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Processing status constants.
 * Using strings instead of enum for Room compatibility without type converters.
 */
object ProcessingStatus {
    const val PENDING = "PENDING"
    const val PROCESSING = "PROCESSING"
    const val DONE = "DONE"
    const val NEEDS_RETRY = "NEEDS_RETRY"
    const val FAILED = "FAILED"
}

/**
 * Capture type constants.
 */
object MemoryType {
    const val PHOTO = "PHOTO"
    const val VOICE = "VOICE"
    const val NOTE = "NOTE"
}

/**
 * Reminder linked to a specific memory.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val memoryId: String,
    val reminderText: String,
    val triggerAt: Long,                   // epoch ms
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Lightweight projection for embedding search — avoids loading full entity.
 */
data class EmbeddingRow(
    val id: String,
    val embedding: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingRow) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Serializable representations for JSON storage in Room.
 */
@Serializable
data class DetectedObjectJson(
    val label: String,
    val confidence: Float
)

@Serializable
data class ImageLabelJson(
    val text: String,
    val confidence: Float
)
