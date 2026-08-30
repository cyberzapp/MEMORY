package com.example.memory.di

import android.content.Context
import androidx.room.Room
import com.example.memory.data.db.MemoryDatabase
import com.example.memory.data.repository.MemoryRepository
import com.example.memory.data.vector.VectorSearchEngine
import com.example.memory.ml.embedding.EmbeddingEngine
import com.example.memory.ml.embedding.WordPieceTokenizer
import com.example.memory.ml.llm.GemmaEngine
import com.example.memory.ml.vision.ImageAnalyzer
import com.example.memory.reminders.ReminderManager
import com.example.memory.ml.ModelLifecycleManager

/**
 * Manual Dependency Injection container.
 */
class AppContainer(private val context: Context) {
    
    val database: MemoryDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            MemoryDatabase::class.java,
            "memory_db"
        )
        // Note: Using destructive migration during prototyping
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }

    val reminderManager: ReminderManager by lazy {
        ReminderManager(context)
    }
    
    val memoryDao by lazy { database.memoryDao() }
    
    val vectorSearchEngine by lazy { VectorSearchEngine(memoryDao) }
    
    val modelLifecycleManager by lazy { ModelLifecycleManager(context) }
    
    val memoryRepository by lazy { 
        MemoryRepository(
            memoryDao,
            vectorSearchEngine,
            modelLifecycleManager,
            reminderManager
        )
    }
}
