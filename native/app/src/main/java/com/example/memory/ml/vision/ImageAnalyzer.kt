package com.example.memory.ml.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.example.memory.ml.DetectedObjectInfo
import com.example.memory.ml.OcrBlock
import com.example.memory.ml.ImageLabelInfo
import com.example.memory.ml.vision.MediaPipeAnalyzer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * On-device image analysis pipeline using ML Kit.
 *
 * Runs three models in PARALLEL via coroutines:
 * 1. Object Detection → detected objects with labels + bounding boxes
 * 2. Text Recognition (OCR) → extracted text with positions
 * 3. Image Labeling → scene classification labels (THIS IS THE BEST ONE)
 *
 * KEY INSIGHT: ML Kit's base Object Detection model only classifies into
 * 5 broad categories (Fashion, Food, Home goods, Places, Plants).
 * For richer object identification, Image Labeling is far superior — it
 * recognizes 400+ categories (Book, Laptop, Furniture, Vehicle, etc.).
 *
 * The summary generation now prioritizes Image Labels over Object Detection
 * labels for more accurate descriptions.
 *
 * Total time: < 500ms on modern device (parallel execution).
 * All processing is on-device — zero network calls.
 */
class ImageAnalyzer(context: Context) {

    companion object {
        private const val TAG = "ImageAnalyzer"
    }

    private val mediaPipeAnalyzer = MediaPipeAnalyzer(context)

    private val textRecognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    // Lower threshold to capture more labels — we filter by quality later
    private val imageLabeler: ImageLabeler =
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.4f)
                .build()
        )

    /**
     * Analyze a photo with ML Kit models in parallel.
     *
     * @param bitmap The captured photo
     * @return VisionResult containing all extracted signals
     */
    suspend fun analyze(bitmap: Bitmap): VisionResult = coroutineScope {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        // Run in parallel
        val objectsDeferred = async { mediaPipeAnalyzer.detectObjects(bitmap) }
        val ocrDeferred = async { runTextRecognition(inputImage) }
        val labelsDeferred = async { runImageLabeling(inputImage) }

        val objectsResult = objectsDeferred.await()
        val ocrResult = ocrDeferred.await()
        val labelsResult = labelsDeferred.await()

        Log.i(TAG, "Analysis complete: ${objectsResult.size} objects, ${labelsResult.size} labels, OCR=${ocrResult.fullText?.take(50)}")
        Log.i(TAG, "Labels: ${labelsResult.map { "${it.text}(${(it.confidence * 100).toInt()}%)" }}")

        VisionResult(
            detectedObjects = objectsResult,
            ocrText = ocrResult.fullText,
            ocrBlocks = ocrResult.blocks,
            imageLabels = labelsResult
        )
    }

    private suspend fun runTextRecognition(image: InputImage): OcrResult {
        return try {
            val result = textRecognizer.process(image).await()
            OcrResult(
                fullText = result.text.ifEmpty { null },
                blocks = result.textBlocks.map { block ->
                    OcrBlock(
                        text = block.text,
                        boundingBox = block.boundingBox,
                        confidence = block.lines.firstOrNull()
                            ?.confidence ?: 0f
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            OcrResult(null, emptyList())
        }
    }

    private suspend fun runImageLabeling(image: InputImage): List<ImageLabelInfo> {
        return try {
            val results = imageLabeler.process(image).await()
            // Image Labeling has 400+ categories — much richer than Object Detection
            // Take top 8 labels for comprehensive scene understanding
            results.take(8).map { label ->
                ImageLabelInfo(
                    text = label.text,
                    confidence = label.confidence,
                    index = label.index
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image labeling failed", e)
            emptyList()
        }
    }

    fun close() {
        textRecognizer.close()
        imageLabeler.close()
        mediaPipeAnalyzer.close()
    }
}

/**
 * Combined result from all ML Kit vision models.
 */
data class VisionResult(
    val detectedObjects: List<DetectedObjectInfo>,
    val ocrText: String?,
    val ocrBlocks: List<OcrBlock>,
    val imageLabels: List<ImageLabelInfo>
)

private data class OcrResult(
    val fullText: String?,
    val blocks: List<OcrBlock>
)
