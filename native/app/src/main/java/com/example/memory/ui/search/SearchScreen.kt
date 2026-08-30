package com.example.memory.ui.search

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.example.memory.data.db.DetectedObjectJson
import com.example.memory.data.db.MemoryEntity
import com.example.memory.data.db.MemoryType
import com.example.memory.data.repository.MemoryRepository
import com.example.memory.data.repository.MemorySession
import com.example.memory.data.repository.RankedMemory
import com.example.memory.data.repository.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// === ViewModel ===

class SearchViewModel(
    private val repository: MemoryRepository
) : ViewModel() {

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
    }

    /**
     * Execute semantic search with temporal context:
     * query → embed → cosine KNN → expand temporal window → session grouping → narrative answer
     */
    fun search() {
        val currentQuery = _query.value.trim()
        if (currentQuery.isEmpty()) return

        viewModelScope.launch {
            _searchState.value = SearchState.Searching(currentQuery)

            try {
                val result = repository.searchMemories(currentQuery)
                _searchState.value = if (result.memories.isEmpty()) {
                    SearchState.NoResults(currentQuery)
                } else {
                    SearchState.Results(currentQuery, result)
                }
            } catch (e: Exception) {
                _searchState.value = SearchState.Error(e.message ?: "Search failed")
            }
        }
    }
}

sealed class SearchState {
    data object Idle : SearchState()
    data class Searching(val query: String) : SearchState()
    data class Results(val query: String, val result: SearchResult) : SearchState()
    data class NoResults(val query: String) : SearchState()
    data class Error(val message: String) : SearchState()
}

// === Compose UI ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    onMemoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchState by viewModel.searchState.collectAsState()
    val query by viewModel.query.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.updateQuery(it) },
                placeholder = { Text("Where did I put my charger?") },
                leadingIcon = { Icon(Icons.Outlined.Search, "Search") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateQuery("") }) {
                            Icon(Icons.Filled.Clear, "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.search()
                        keyboardController?.hide()
                    }
                )
            )

            Spacer(Modifier.height(16.dp))

            // Search results
            when (val state = searchState) {
                is SearchState.Idle -> {
                    SearchHints()
                }

                is SearchState.Searching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "🧠 Understanding your question...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is SearchState.Results -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // AI Answer bubble
                        item(key = "answer") {
                            AnswerBubble(state.result.answer)
                        }

                        // Session timeline (if sessions exist)
                        val sessions = state.result.sessions
                        if (sessions.isNotEmpty()) {
                            item(key = "session_header") {
                                Text(
                                    "Memory Timeline:",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            sessions.forEachIndexed { sessionIdx, session ->
                                item(key = "session_$sessionIdx") {
                                    SessionTimeline(
                                        session = session,
                                        onMemoryClick = onMemoryClick
                                    )
                                }
                            }
                        }

                        // Evidence cards for matches not in sessions
                        val sessionMemoryIds = sessions.flatMap { it.memories.map { m -> m.id } }.toSet()
                        val nonSessionMatches = state.result.memories.filter { it.entity.id !in sessionMemoryIds }

                        if (nonSessionMatches.isNotEmpty()) {
                            item(key = "results_header") {
                                Text(
                                    "Other matches (${nonSessionMatches.size}):",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            items(
                                items = nonSessionMatches,
                                key = { it.entity.id }
                            ) { rankedMemory ->
                                EvidenceCard(
                                    rankedMemory = rankedMemory,
                                    onClick = { onMemoryClick(rankedMemory.entity.id) }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }

                is SearchState.NoResults -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No memories found for\n\"${state.query}\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                is SearchState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerBubble(answer: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = "AI Answer",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Session Timeline — vertical timeline showing connected memories.
 *
 *   ┃
 *   ┣━ 10:30 📸 Whiteboard — Calculus equation
 *   ┃
 *   ┣━ 10:32 🎤 "Professor said this will be in the exam"
 *   ┃
 *   ┗━ 10:35 📸 Notebook — Integration notes
 */
@Composable
private fun SessionTimeline(
    session: MemorySession,
    onMemoryClick: (String) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Session header
            Text(
                text = "${dateFormat.format(Date(session.startTime))}, ${timeFormat.format(Date(session.startTime))} — ${timeFormat.format(Date(session.endTime))}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            // Timeline entries
            session.memories.forEachIndexed { index, memory ->
                val isLast = index == session.memories.lastIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMemoryClick(memory.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Timeline dot + line
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(24.dp)
                    ) {
                        // Dot
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        // Vertical line (except for last item)
                        if (!isLast) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(40.dp)
                                    .background(lineColor)
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Time
                    Text(
                        text = timeFormat.format(Date(memory.capturedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(64.dp)
                    )

                    // Type icon
                    val typeIcon = when (memory.type) {
                        MemoryType.PHOTO -> "📸"
                        MemoryType.VOICE -> "🎤"
                        else -> "📝"
                    }
                    Text(typeIcon, modifier = Modifier.width(24.dp))

                    Spacer(Modifier.width(4.dp))

                    // Summary
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = memory.structuredSummary
                                ?: memory.voiceTranscript?.take(60)
                                ?: memory.rawOcrText?.take(60)
                                ?: "Memory",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Thumbnail (if photo)
                    if (memory.thumbnailPath != null) {
                        Spacer(Modifier.width(8.dp))
                        AsyncImage(
                            model = File(memory.thumbnailPath),
                            contentDescription = "Memory thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EvidenceCard(
    rankedMemory: RankedMemory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val memory = rankedMemory.entity
    val matchPercent = (rankedMemory.similarityScore * 100).toInt()

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (memory.thumbnailPath != null) {
                        AsyncImage(
                            model = File(memory.thumbnailPath),
                            contentDescription = "Evidence",
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
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Summary
                    Text(
                        text = memory.structuredSummary
                            ?: memory.rawOcrText?.take(100)
                            ?: memory.voiceTranscript?.take(100)
                            ?: "Memory",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(4.dp))

                    // Parsed detected objects (instead of raw JSON)
                    val parsedObjects = remember(memory.rawDetectedObjects) {
                        try {
                            if (!memory.rawDetectedObjects.isNullOrBlank() && memory.rawDetectedObjects != "[]") {
                                Json.decodeFromString<List<DetectedObjectJson>>(memory.rawDetectedObjects)
                                    .filter { it.label != "Object" }
                            } else emptyList()
                        } catch (e: Exception) { emptyList() }
                    }
                    if (parsedObjects.isNotEmpty()) {
                        Text(
                            text = "🏷️ ${parsedObjects.joinToString(", ") { it.label }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // OCR text
                    if (!memory.rawOcrText.isNullOrBlank()) {
                        Text(
                            text = "📝 \"${memory.rawOcrText.take(50)}\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Voice transcript
                    if (!memory.voiceTranscript.isNullOrBlank()) {
                        Text(
                            text = "🎤 \"${memory.voiceTranscript.take(50)}\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Location + time
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (memory.locationName != null) {
                            Text(
                                "📍 ${memory.locationName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "⏱ ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(memory.capturedAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Match score badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        matchPercent >= 80 -> MaterialTheme.colorScheme.primary
                        matchPercent >= 50 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Text(
                        text = "${matchPercent}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            matchPercent >= 50 -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHints() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.Psychology,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Ask me anything about your memories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))

        val hints = listOf(
            "Where did I put my charger?",
            "What did sir say about the exam?",
            "What happened around 9 AM?",
            "Where is my passport?"
        )
        hints.forEach { hint ->
            SuggestionChip(
                onClick = { },
                label = { Text(hint, style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}
