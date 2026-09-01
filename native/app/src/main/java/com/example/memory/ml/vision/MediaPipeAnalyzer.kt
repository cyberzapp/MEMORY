package com.example.memory.ml.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.memory.ml.DetectedObjectInfo
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Highly accurate, on-device Object Detection using Google MediaPipe Tasks API.
 * 
 * Powered by EfficientDet-Lite0. Detects 80+ COCO classes (e.g., laptop, cup, cell phone)
 * with precise bounding boxes in a single pass (< 100ms).
 */
class MediaPipeAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "MediaPipeAnalyzer"
        private const val MODEL_NAME = "efficientdet_lite0.tflite"
    }

    private var objectDetector: ObjectDetector? = null

    init {
        setupObjectDetector()
    }

    private fun setupObjectDetector() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_NAME)
                .build()

            val options = ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(10)
                .setScoreThreshold(0.4f)
                .build()

            objectDetector = ObjectDetector.createFromOptions(context, options)
            Log.i(TAG, "MediaPipe Object Detector initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe Object Detector", e)
        }
    }

    suspend fun detectObjects(bitmap: Bitmap): List<DetectedObjectInfo> = withContext(Dispatchers.Default) {
        val detector = objectDetector ?: return@withContext emptyList()
        
        try {
            // MediaPipe requires a specific MPImage object
            val mpImage = BitmapImageBuilder(bitmap).build()
            
            // Run inference (synchronous blocking call, so we run on Dispatchers.Default)
            val detectionResult = detector.detect(mpImage)
            
            val detectedObjects = detectionResult.detections().mapNotNull { detection ->
                val category = detection.categories().firstOrNull() ?: return@mapNotNull null
                val bbox = detection.boundingBox()
                
                DetectedObjectInfo(
                    label = category.categoryName(),
                    confidence = category.score(),
                    boundingBox = Rect(
                        bbox.left.toInt(),
                        bbox.top.toInt(),
                        bbox.right.toInt(),
                        bbox.bottom.toInt()
                    )
                )
            }
            
            Log.i(TAG, "MediaPipe found ${detectedObjects.size} objects: ${detectedObjects.joinToString { it.label }}")
            detectedObjects
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe detection failed", e)
            emptyList()
        }
    }

    fun close() {
        objectDetector?.close()
        objectDetector = null
    }
}
