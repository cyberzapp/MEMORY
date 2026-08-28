package com.example.memory.ui.timeline

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.example.memory.data.db.MemoryEntity
import com.example.memory.data.db.MemoryType
import com.example.memory.data.db.ProcessingStatus
import com.example.memory.data.repository.MemoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// === ViewModel ===

class TimelineViewModel(
    private val repository: MemoryRepository
) : ViewModel() {

    val groupedMemories: StateFlow<Map<String, List<MemoryEntity>>> =
        repository.allMemories
            .map { memories ->
                memories.groupBy { memory ->
                    formatDateHeader(memory.capturedAt)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            // Delete associated files
            memory.originalMediaPath?.let { File(it).delete() }
            memory.thumbnailPath?.let { File(it).delete() }
            repository.deleteMemory(memory)
        }
    }

    private fun formatDateHeader(epochMs: Long): String {
        val memoryDate = Calendar.getInstance().apply { timeInMillis = epochMs }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(memoryDate, today) -> "Today"
            isSameDay(memoryDate, yesterday) -> "Yesterday"
            else -> SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
                .format(Date(epochMs))
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
               a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}

// === Compose UI ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onMemoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedMemories by viewModel.groupedMemories.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timeline", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Outlined.Search, "Search")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (groupedMemories.isEmpty()) {
            // Empty state
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No memories yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Capture your first memory with the camera",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedMemories.forEach { (dateHeader, memories) ->
                    // Sticky date header
                    item(key = "header_$dateHeader") {
                        DateHeader(dateHeader)
                    }

                    // Memory cards
                    items(
                        items = memories,
                        key = { it.id }
                    ) { memory ->
                        MemoryCard(
                            memory = memory,
                            onClick = { onMemoryClick(memory.id) },
                            onDelete = { viewModel.deleteMemory(memory) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun MemoryCard(
    memory: MemoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Thumbnail or type icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (memory.thumbnailPath != null) {
                    AsyncImage(
                        model = File(memory.thumbnailPath),
                        contentDescription = "Memory thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        when (memory.type) {
                            MemoryType.PHOTO -> Icons.Filled.Photo
                            MemoryType.VOICE -> Icons.Filled.Mic
                            else -> Icons.Filled.Note
                        },
                        contentDescription = memory.type,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                // Summary or raw text
                Text(
                    text = memory.structuredSummary
                        ?: memory.rawOcrText?.take(80)
                        ?: memory.voiceTranscript?.take(80)
                        ?: "Processing...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                // Metadata row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (memory.locationName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                memory.locationName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            SimpleDateFormat("h:mm a", Locale.getDefault())
                                .format(Date(memory.capturedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Processing status indicator
                if (memory.processingStatus != ProcessingStatus.DONE) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (memory.processingStatus == ProcessingStatus.PROCESSING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when (memory.processingStatus) {
                                ProcessingStatus.PENDING -> "⏳ Waiting to process"
                                ProcessingStatus.PROCESSING -> "🧠 Understanding..."
                                ProcessingStatus.NEEDS_RETRY -> "⚠️ Retry needed"
                                ProcessingStatus.FAILED -> "❌ Processing failed"
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
