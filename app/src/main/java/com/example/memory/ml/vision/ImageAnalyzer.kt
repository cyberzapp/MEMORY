package com.example.memory.ml.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.example.memory.ml.DetectedObjectInfo
import com.example.memory.ml.OcrBlock
import com.example.memory.ml.ImageLabelInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * On-device image analysis pipeline using ML Kit.
 *
 * Runs three models in PARALLEL via coroutines:
 * 1. Object Detection → detected objects with labels + bounding boxes
 * 2. Text Recognition (OCR) → extracted text with positions
 * 3. Image Labeling → scene classification labels
 *
 * Total time: < 500ms on modern device (parallel execution).
 * All processing is on-device — zero network calls.
 */
class ImageAnalyzer(context: Context) {

    private val textRecognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    private val objectDetector: ObjectDetector =
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
        )

    private val imageLabeler: ImageLabeler =
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.5f)
                .build()
        )

    /**
     * Analyze a photo with all three ML Kit models in parallel.
     *
     * @param bitmap The captured photo
     * @return VisionResult containing all extracted signals
     */
    suspend fun analyze(bitmap: Bitmap): VisionResult = coroutineScope {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        // Run all three in parallel
        val ocrDeferred = async { runTextRecognition(inputImage) }
        val objectsDeferred = async { runObjectDetection(inputImage) }
        val labelsDeferred = async { runImageLabeling(inputImage) }

        val ocrResult = ocrDeferred.await()
        val objectsResult = objectsDeferred.await()
        val labelsResult = labelsDeferred.await()

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
            OcrResult(null, emptyList())
        }
    }

    private suspend fun runObjectDetection(image: InputImage): List<DetectedObjectInfo> {
        return try {
            val results = objectDetector.process(image).await()
            results.flatMap { detectedObject ->
                detectedObject.labels.map { label ->
                    DetectedObjectInfo(
                        label = label.text,
                        confidence = label.confidence,
                        boundingBox = detectedObject.boundingBox,
                        trackingId = detectedObject.trackingId
                    )
                }
            }.ifEmpty {
                // Object detected but no classification labels
                results.map { obj ->
                    DetectedObjectInfo(
                        label = "Object",
                        confidence = 0f,
                        boundingBox = obj.boundingBox,
                        trackingId = obj.trackingId
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun runImageLabeling(image: InputImage): List<ImageLabelInfo> {
        return try {
            val results = imageLabeler.process(image).await()
            results.map { label ->
                ImageLabelInfo(
                    text = label.text,
                    confidence = label.confidence,
                    index = label.index
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun close() {
        textRecognizer.close()
        objectDetector.close()
        imageLabeler.close()
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
