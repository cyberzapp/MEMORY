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

    fun clearAllMemories() {
        viewModelScope.launch {
            val allMemories = groupedMemories.value.values.flatten()
            for (memory in allMemories) {
                memory.originalMediaPath?.let { File(it).delete() }
                memory.thumbnailPath?.let { File(it).delete() }
                repository.deleteMemory(memory)
            }
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
    onNavigateToSettings: () -> Unit,
    onMemoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedMemories by viewModel.groupedMemories.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Clear all confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All Memories?") },
            text = { Text("This will permanently delete all memories, photos, and associated data. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllMemories()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Outlined.Search, "Search")
                    }
                    IconButton(onClick = { /* TODO Filter */ }) {
                        Icon(Icons.Filled.FilterList, "Filter")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToSettings()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Settings, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear All Memories") },
                                onClick = {
                                    showMenu = false
                                    showClearDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
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

                    // Display Daily Summary for Today
                    if (dateHeader == "Today" && memories.isNotEmpty()) {
                        item(key = "summary_$dateHeader") {
                            DailySummaryCard(memories = memories)
                        }
                    }

                    // Memory cards with swipe-to-delete
                    items(
                        items = memories,
                        key = { it.id }
                    ) { memory ->
                        SwipeToDismissMemoryCard(
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

/**
 * Swipe-to-dismiss wrapper for memory cards.
 * Swipe left reveals a red delete background and removes the memory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissMemoryCard(
    memory: MemoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showUndoSnackbar by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Red delete background shown on swipe
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        MemoryCard(
            memory = memory,
            onClick = onClick,
            onDelete = onDelete
        )
    }
}

@Composable
private fun DateHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 16.dp, end = 24.dp)
    )
}

@Composable
fun DailySummaryCard(memories: List<MemoryEntity>) {
    // Generate a quick summary text
    val summaryText = if (memories.size > 2) {
        val locations = memories.mapNotNull { it.locationName }.distinct()
        val locText = if (locations.isNotEmpty()) " You visited ${locations.joinToString(" and ")}." else ""
        "You captured ${memories.size} memories today.$locText"
    } else {
        "You captured ${memories.size} memories today."
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "MEMORY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Here is your daily summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                summaryText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Total ${memories.size} Memories Captured",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MemoryCard(
    memory: MemoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail (rounded square)
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                        else -> Icons.Filled.Description
                    },
                    contentDescription = memory.type,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            // Title (Status-aware)
            val displayText = when (memory.processingStatus) {
                ProcessingStatus.PENDING -> "Waiting..."
                ProcessingStatus.PROCESSING -> "Analyzing..."
                ProcessingStatus.NEEDS_RETRY -> "Retry needed"
                else -> {
                    val rawStr = memory.structuredSummary ?: memory.rawOcrText ?: memory.voiceTranscript ?: "Memory"
                    val lines = rawStr.split("\n", ".").filter { it.isNotBlank() }
                    lines.firstOrNull()?.trim() ?: "Photo memory"
                }
            }

            Text(
                text = displayText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            // Subtitle: Time • Location
            val timeString = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(memory.capturedAt))
            val locationString = memory.locationName?.let { " • $it" } ?: ""
            Text(
                text = "$timeString$locationString",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Processing status indicator
            if (memory.processingStatus == ProcessingStatus.PROCESSING) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Understanding...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
