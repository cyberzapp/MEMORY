package com.example.memory.ml

import android.graphics.Rect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * First-class intermediate representation between capture and storage.
 *
 * Every memory flows through this structure. It is a typed, structured
 * bundle of ALL signals extracted from a single capture event.
 * Not a raw string concatenation — a proper data structure.
 */
data class EvidenceBundle(
    // === Context (always present) ===
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,

    // === From ML Kit Object Detection ===
    val objects: List<DetectedObjectInfo> = emptyList(),

    // === From ML Kit Text Recognition ===
    val ocrText: String? = null,
    val ocrBlocks: List<OcrBlock> = emptyList(),

    // === From ML Kit Image Labeling ===
    val imageLabels: List<ImageLabelInfo> = emptyList(),

    // === From SpeechRecognizer (voice captures only) ===
    val transcript: String? = null,

    // === Capture metadata ===
    val captureType: CaptureType
) {
    /**
     * Flattened text representation for the embedding model.
     * Combines all textual signals into a single string that
     * all-MiniLM-L6-v2 can vectorize into 384 dimensions.
     *
     * Format: "objects | labels | ocr_text | transcript | location"
     *
     * This is THE most important method in the entire app.
     * The quality of this text directly determines search accuracy.
     */
    fun toEmbeddingText(): String {
        val text = listOfNotNull(
            objects.takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { it.label },
            imageLabels.takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { it.text },
            ocrText?.take(500),     // cap OCR for embedding quality
            transcript?.take(500),  // cap transcript similarly
            locationName
        ).joinToString(" | ")
        
        return text.ifBlank { "Blank memory" }
    }

    /**
     * Structured prompt for Gemma 3 1B memory extraction.
     * Feeds all evidence fields with clear labels so the LLM
     * can produce a human-readable one-sentence summary.
     */
    fun toGemmaPrompt(): String = buildString {
        appendLine("Summarize this observation in one clear sentence.")
        if (objects.isNotEmpty()) {
            appendLine("Objects detected: ${objects.joinToString { "${it.label} (${(it.confidence * 100).toInt()}%)" }}")
        }
        if (imageLabels.isNotEmpty()) {
            appendLine("Scene labels: ${imageLabels.joinToString { it.text }}")
        }
        if (!ocrText.isNullOrBlank()) {
            appendLine("Text found (OCR): $ocrText")
        }
        if (!transcript.isNullOrBlank()) {
            appendLine("Voice note: $transcript")
        }
        if (locationName != null) {
            appendLine("Location: $locationName")
        }
        appendLine("Time: ${formatTimestamp(timestamp)}")
        appendLine()
        append("Summary:")
    }

    /**
     * Template-based fallback summary — works WITHOUT Gemma loaded.
     * Used when:
     * - Gemma hasn't been initialized yet
     * - Device is under memory pressure
     * - Processing needs to be fast
     */
    fun fallbackSummary(): String {
        return buildString {
            // Prioritize Image Labels (400+ categories) over Object Detection (only 5 categories)
            // Image labels give much better results: "Book, Desk, Laptop" vs "Home goods"
            if (imageLabels.isNotEmpty()) {
                append(imageLabels.take(4).joinToString(", ") { it.text })
            }
            // Add specific object detection labels only if they add info
            val meaningfulObjects = objects.filter { it.label != "Object" }
            if (meaningfulObjects.isNotEmpty()) {
                if (isNotEmpty()) append(" — ")
                append(meaningfulObjects.joinToString(", ") { it.label })
            }
            if (!ocrText.isNullOrBlank()) {
                if (isNotEmpty()) append(" | ")
                append("\"${ocrText.take(80)}\"")
            }
            if (!transcript.isNullOrBlank()) {
                if (isNotEmpty()) append(" | ")
                append("\"${transcript.take(120)}\"")
            }
            if (locationName != null) {
                if (isNotEmpty()) append(" at ")
                append(locationName)
            }
        }.ifEmpty { "Photo captured at ${formatTimestamp(timestamp)}" }
    }

    /**
     * Verification gate — ensures extraction produced usable data.
     * Original media must NOT be deleted unless this returns true.
     */
    fun hasMinimumSignal(): Boolean {
        if (captureType == CaptureType.PHOTO) return true
        
        return objects.isNotEmpty() ||
               imageLabels.isNotEmpty() ||
               !ocrText.isNullOrBlank() ||
               !transcript.isNullOrBlank()
    }

    private fun formatTimestamp(epochMs: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }
}

data class DetectedObjectInfo(
    val label: String,
    val confidence: Float,
    val boundingBox: Rect? = null,
    val trackingId: Int? = null
)

data class OcrBlock(
    val text: String,
    val boundingBox: Rect? = null,
    val confidence: Float = 0f
)

data class ImageLabelInfo(
    val text: String,
    val confidence: Float,
    val index: Int = -1
)

enum class CaptureType { PHOTO, VOICE }
