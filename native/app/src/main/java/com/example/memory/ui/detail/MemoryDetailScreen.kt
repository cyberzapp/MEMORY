package com.example.memory.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.memory.data.db.DetectedObjectJson
import com.example.memory.data.db.ImageLabelJson
import com.example.memory.data.db.MemoryEntity
import com.example.memory.data.db.MemoryType
import com.example.memory.data.db.ReminderEntity
import com.example.memory.ml.EvidenceBundle
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDetailScreen(
    viewModel: MemoryDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is MemoryDetailState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is MemoryDetailState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is MemoryDetailState.Success -> {
                    MemoryDetailContent(
                        memory = state.memory,
                        reminders = state.activeReminders,
                        onScheduleReminder = { mins, text -> viewModel.scheduleReminder(mins, text) },
                        onCancelReminder = { id -> viewModel.cancelReminder(id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryDetailContent(
    memory: MemoryEntity,
    reminders: List<ReminderEntity>,
    onScheduleReminder: (Int, String) -> Unit,
    onCancelReminder: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Media Area
        item {
            if (memory.type == MemoryType.PHOTO) {
                AsyncImage(
                    model = memory.originalMediaPath?.let { File(it) },
                    contentDescription = "Memory Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Voice Memory",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // 2. Metadata Area
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = SimpleDateFormat("EEEE, MMMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(memory.capturedAt)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                if (memory.locationName != null) {
                    Text(
                        text = "📍 ${memory.locationName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 3. Extracted Context
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI Summary", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    val fallbackSummary = memory.voiceTranscript ?: "Processing or skipped..."
                    Text(
                        text = memory.structuredSummary ?: fallbackSummary,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    if (!memory.rawOcrText.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Text Found", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(text = memory.rawOcrText, style = MaterialTheme.typography.bodyMedium)
                    }

                    // Parse and display detected objects
                    val parsedObjects = remember(memory.rawDetectedObjects) {
                        try {
                            if (!memory.rawDetectedObjects.isNullOrBlank()) {
                                Json.decodeFromString<List<DetectedObjectJson>>(memory.rawDetectedObjects)
                                    .filter { it.label != "Object" }
                            } else emptyList()
                        } catch (e: Exception) { emptyList() }
                    }
                    if (parsedObjects.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Objects Detected", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = parsedObjects.joinToString(", ") { "${it.label} (${(it.confidence * 100).toInt()}%)" },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Parse and display image labels
                    val parsedLabels = remember(memory.rawImageLabels) {
                        try {
                            if (!memory.rawImageLabels.isNullOrBlank()) {
                                Json.decodeFromString<List<ImageLabelJson>>(memory.rawImageLabels)
                                    .filter { EvidenceBundle.isUsefulLabel(it.text) }
                            } else emptyList()
                        } catch (e: Exception) { emptyList() }
                    }
                    if (parsedLabels.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Scene Labels", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = parsedLabels.joinToString(", ") { it.text },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // 4. Reminders Area
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Reminders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            
            // Quick Add Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onScheduleReminder(15, "Check this out in 15 mins") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ 15 min")
                }
                Button(
                    onClick = { onScheduleReminder(60, "Check this out in 1 hour") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ 1 hr")
                }
                Button(
                    onClick = { onScheduleReminder(24 * 60, "Check this out tomorrow") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ 1 day")
                }
            }
        }

        // Active Reminders List
        if (reminders.isNotEmpty()) {
            items(reminders) { reminder ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(reminder.triggerAt)),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = reminder.reminderText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        IconButton(onClick = { onCancelReminder(reminder.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "No active reminders",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        
        item { Spacer(Modifier.height(32.dp)) }
    }
}
