package com.example.memory

import android.app.Application
import com.example.memory.di.AppContainer

/**
 * Application class for MEMORY.
 * Holds the manual DI container.
 */
class MemoryApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
