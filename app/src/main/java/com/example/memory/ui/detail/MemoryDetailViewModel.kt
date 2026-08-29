package com.example.memory.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memory.data.db.MemoryEntity
import com.example.memory.data.db.ReminderEntity
import com.example.memory.data.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

sealed class MemoryDetailState {
    data object Loading : MemoryDetailState()
    data class Success(
        val memory: MemoryEntity,
        val activeReminders: List<ReminderEntity>
    ) : MemoryDetailState()
    data class Error(val message: String) : MemoryDetailState()
}

class MemoryDetailViewModel(
    private val memoryId: String,
    private val repository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MemoryDetailState>(MemoryDetailState.Loading)
    val uiState: StateFlow<MemoryDetailState> = _uiState

    init {
        loadMemoryDetails()
    }

    private fun loadMemoryDetails() {
        viewModelScope.launch {
            try {
                val memory = repository.getMemoryById(memoryId)
                if (memory == null) {
                    _uiState.value = MemoryDetailState.Error("Memory not found")
                    return@launch
                }

                // Fetch active reminders for this memory
                val reminders = repository.getRemindersForMemory(memoryId)
                val active = reminders.filter { !it.isCompleted }
                _uiState.value = MemoryDetailState.Success(memory, active)
            } catch (e: Exception) {
                _uiState.value = MemoryDetailState.Error(e.message ?: "Failed to load memory")
            }
        }
    }

    /**
     * Schedule a reminder at a relative offset.
     * @param delayMinutes The number of minutes from now to trigger the reminder.
     * @param text The reminder note.
     */
    fun scheduleReminder(delayMinutes: Int, text: String) {
        viewModelScope.launch {
            val triggerAtMs = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes.toLong())
            repository.scheduleReminder(memoryId, text, triggerAtMs)
            loadMemoryDetails() // Reload to show new reminder
        }
    }

    /**
     * Cancel an active reminder.
     */
    fun cancelReminder(reminderId: String) {
        viewModelScope.launch {
            repository.cancelReminder(reminderId)
            loadMemoryDetails() // Reload to remove reminder
        }
    }
}
