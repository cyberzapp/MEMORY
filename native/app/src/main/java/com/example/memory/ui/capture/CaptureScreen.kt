package com.example.memory.ui.capture

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.location.Geocoder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.memory.data.db.MemoryEntity
import com.example.memory.data.db.MemoryType
import com.example.memory.data.db.ProcessingStatus
import com.example.memory.data.repository.MemoryRepository
import com.example.memory.ml.MemoryProcessingWorker
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

// === ViewModel ===

class CaptureViewModel(
    private val repository: MemoryRepository,
    private val appContext: Context
) : ViewModel() {

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Ready)
    val captureState: StateFlow<CaptureState> = _captureState

    private val _lastCapturedId = MutableStateFlow<String?>(null)
    val lastCapturedId: StateFlow<String?> = _lastCapturedId

    /**
     * Save a captured photo bitmap and enqueue background processing.
     */
    fun capturePhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            _captureState.value = CaptureState.Saving

            try {
                val memoryId = UUID.randomUUID().toString()

                // Save original photo to internal storage
                val photoFile = savePhotoToInternal(bitmap, memoryId)

                // Get current location
                val location = getCurrentLocation()

                // Create memory entity (PENDING status — worker will process)
                val memory = MemoryEntity(
                    id = memoryId,
                    type = MemoryType.PHOTO,
                    capturedAt = System.currentTimeMillis(),
                    latitude = location?.first,
                    longitude = location?.second,
                    locationName = location?.third,
                    originalMediaPath = photoFile.absolutePath,
                    processingStatus = ProcessingStatus.PENDING
                )

                repository.insertMemory(memory)

                // Enqueue background ML processing
                enqueueProcessing(memoryId)

                _lastCapturedId.value = memoryId
                _captureState.value = CaptureState.Captured(memoryId)

                // Auto-reset after 2 seconds
                kotlinx.coroutines.delay(2000)
                _captureState.value = CaptureState.Ready

            } catch (e: Exception) {
                Log.e("CaptureVM", "Capture failed", e)
                _captureState.value = CaptureState.Error(e.message ?: "Capture failed")
            }
        }
    }

    /**
     * Save a voice transcript as a memory.
     */
    fun captureVoice(transcript: String) {
        viewModelScope.launch {
            _captureState.value = CaptureState.Saving

            try {
                val memoryId = UUID.randomUUID().toString()
                val location = getCurrentLocation()

                val memory = MemoryEntity(
                    id = memoryId,
                    type = MemoryType.VOICE,
                    capturedAt = System.currentTimeMillis(),
                    latitude = location?.first,
                    longitude = location?.second,
                    locationName = location?.third,
                    voiceTranscript = transcript,
                    processingStatus = ProcessingStatus.PENDING
                )

                repository.insertMemory(memory)
                enqueueProcessing(memoryId)

                _lastCapturedId.value = memoryId
                _captureState.value = CaptureState.Captured(memoryId)

                kotlinx.coroutines.delay(2000)
                _captureState.value = CaptureState.Ready

            } catch (e: Exception) {
                _captureState.value = CaptureState.Error(e.message ?: "Voice capture failed")
            }
        }
    }

    private fun savePhotoToInternal(bitmap: Bitmap, memoryId: String): File {
        val photosDir = File(appContext.filesDir, "photos")
        photosDir.mkdirs()
        val file = File(photosDir, "${memoryId}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        return file
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Triple<Double, Double, String?>? {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
            val location = fusedClient.lastLocation.await() ?: return null

            // Reverse geocode
            val geocoder = Geocoder(appContext, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            val locationName = addresses?.firstOrNull()?.let { addr ->
                listOfNotNull(addr.featureName, addr.locality).joinToString(", ")
            }

            Triple(location.latitude, location.longitude, locationName)
        } catch (e: Exception) {
            Log.w("CaptureVM", "Location unavailable", e)
            null
        }
    }

    private fun enqueueProcessing(memoryId: String) {
        val request = OneTimeWorkRequestBuilder<MemoryProcessingWorker>()
            .setInputData(workDataOf(MemoryProcessingWorker.KEY_MEMORY_ID to memoryId))
            .build()
        WorkManager.getInstance(appContext).enqueue(request)
    }
}

sealed class CaptureState {
    data object Ready : CaptureState()
    data object Saving : CaptureState()
    data class Captured(val memoryId: String) : CaptureState()
    data class Error(val message: String) : CaptureState()
}

// === Compose UI ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onNavigateToTimeline: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateBack: () -> Unit = {},
    triggerCaptureFlow: kotlinx.coroutines.flow.SharedFlow<Unit> = kotlinx.coroutines.flow.MutableSharedFlow(),
    modifier: Modifier = Modifier
) {
    val captureState by viewModel.captureState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    
    LaunchedEffect(triggerCaptureFlow, imageCapture) {
        triggerCaptureFlow.collect {
            if (imageCapture != null && captureState == CaptureState.Ready) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                
                imageCapture?.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                val bitmap = image.toBitmap()
                                viewModel.capturePhoto(bitmap)
                            } finally {
                                image.close()
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("CaptureScreen", "Photo capture failed", exception)
                        }
                    }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.Close, "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToTimeline) {
                        Icon(Icons.Outlined.Timeline, "Timeline")
                    }
                    IconButton(onClick = { /* TODO flash */ }) {
                        Icon(Icons.Filled.FlashOn, "Flash")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Camera Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                CameraPreview(
                    onImageCaptureReady = { imageCapture = it },
                    modifier = Modifier.fillMaxSize()
                )

                // Capture state overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = captureState is CaptureState.Captured,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Saved",
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Memory captured",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            val speechLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val data = result.data
                    val results = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                    val spokenText = results?.get(0)
                    if (!spokenText.isNullOrBlank()) {
                        viewModel.captureVoice(spokenText)
                    }
                }
            }

                Text(
                    text = "Tap to remember",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Voice Button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            try {
                                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                }
                                speechLauncher.launch(intent)
                            } catch (e: Exception) {
                                viewModel.captureVoice("Voice capture not supported on this device.")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Mic,
                        contentDescription = "Record Voice",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(Modifier.width(24.dp))
                
                // Photo Button
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(4.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            imageCapture?.let { ic ->
                                ic.takePicture(
                                    java.util.concurrent.Executors.newSingleThreadExecutor(),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val bitmap = image.toBitmap()
                                            viewModel.capturePhoto(bitmap)
                                            image.close()
                                        }
                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e("CaptureScreen", "Photo capture failed", exception)
                                        }
                                    }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = "Capture",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun CameraPreview(
    onImageCaptureReady: (ImageCapture) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                onImageCaptureReady(imageCapture)

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Camera bind failed", e)
                }
            }, ctx.mainExecutor)

            previewView
        },
        modifier = modifier
    )
}
