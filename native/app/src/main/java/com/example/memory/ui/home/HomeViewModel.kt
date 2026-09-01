package com.example.memory.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.memory.MemoryApplication
import com.example.memory.data.db.MemoryEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val userName: String = "",
    val recentMemories: List<MemoryEntity> = emptyList(),
    val totalMemoryCount: Int = 0
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MemoryApplication
    private val memoryRepository = app.container.memoryRepository
    private val userPrefs = app.container.userPreferencesRepository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPrefs.userName.collect { name ->
                _uiState.update { it.copy(userName = name) }
            }
        }

        viewModelScope.launch {
            memoryRepository.processedMemories.collect { memories ->
                _uiState.update {
                    it.copy(
                        recentMemories = memories,
                        totalMemoryCount = memories.size
                    )
                }
            }
        }
    }
}
