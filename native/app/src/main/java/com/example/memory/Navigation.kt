package com.example.memory

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.memory.ui.capture.CaptureScreen
import com.example.memory.ui.capture.CaptureViewModel
import com.example.memory.ui.search.SearchScreen
import com.example.memory.ui.search.SearchViewModel
import com.example.memory.ui.timeline.TimelineScreen
import com.example.memory.ui.timeline.TimelineViewModel
import com.example.memory.ui.detail.MemoryDetailScreen
import com.example.memory.ui.detail.MemoryDetailViewModel
import com.example.memory.ui.settings.SettingsScreen

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow

@Composable
fun MainNavigation(triggerCaptureFlow: SharedFlow<Unit> = MutableSharedFlow()) {
    val backStack = rememberNavBackStack(CaptureRoute)
    val context = LocalContext.current
    val appContainer = (context.applicationContext as MemoryApplication).container

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<CaptureRoute> {
                val viewModel: CaptureViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return CaptureViewModel(appContainer.memoryRepository, context.applicationContext) as T
                        }
                    }
                )
                CaptureScreen(
                    viewModel = viewModel,
                    triggerCaptureFlow = triggerCaptureFlow,
                    onNavigateToTimeline = { backStack.add(TimelineRoute) },
                    onNavigateToSearch = { backStack.add(SearchRoute) },
                    modifier = Modifier.safeDrawingPadding()
                )
            }

            entry<TimelineRoute> {
                val viewModel: TimelineViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return TimelineViewModel(appContainer.memoryRepository) as T
                        }
                    }
                )
                TimelineScreen(
                    viewModel = viewModel,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onNavigateToSearch = { backStack.add(SearchRoute) },
                    onNavigateToSettings = { backStack.add(SettingsRoute) },
                    onMemoryClick = { memoryId ->
                        backStack.add(MemoryDetailRoute(memoryId))
                    },
                    modifier = Modifier.safeDrawingPadding()
                )
            }

            entry<SearchRoute> {
                val viewModel: SearchViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return SearchViewModel(appContainer.memoryRepository) as T
                        }
                    }
                )
                SearchScreen(
                    viewModel = viewModel,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    onMemoryClick = { memoryId ->
                        backStack.add(MemoryDetailRoute(memoryId))
                    },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            
            entry<MemoryDetailRoute> { route ->
                val viewModel: MemoryDetailViewModel = viewModel(
                    key = "detail_${route.memoryId}",
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return MemoryDetailViewModel(route.memoryId, appContainer.memoryRepository) as T
                        }
                    }
                )
                MemoryDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            
            entry<SettingsRoute> {
                SettingsScreen(
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
