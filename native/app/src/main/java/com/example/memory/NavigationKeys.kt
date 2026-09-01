package com.example.memory

import kotlinx.serialization.Serializable

import androidx.navigation3.runtime.NavKey

/**
 * Type-safe navigation keys for Navigation 3.
 */
@Serializable
data object CaptureRoute : NavKey

@Serializable
data object TimelineRoute : NavKey

@Serializable
data object SearchRoute : NavKey

@Serializable
data class MemoryDetailRoute(val memoryId: String) : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object SplashRoute : NavKey

@Serializable
data object OnboardingRoute : NavKey

@Serializable
data object HomeRoute : NavKey

@Serializable
data object RemindersRoute : NavKey
