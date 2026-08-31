package com.example.memory.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.memory.service.VoiceCaptureService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("memory_prefs", Context.MODE_PRIVATE) }
    
    var wakeWordEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("wake_word_enabled", false))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Features",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Column {
                        Text("Voice Activation (Wake Word)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Say \"Memory Capture\" to auto-capture",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = wakeWordEnabled,
                    onCheckedChange = { isChecked ->
                        wakeWordEnabled = isChecked
                        sharedPrefs.edit().putBoolean("wake_word_enabled", isChecked).apply()
                        
                        if (isChecked) {
                            val intent = Intent(context, VoiceCaptureService::class.java)
                            context.startService(intent)
                        } else {
                            val intent = Intent(context, VoiceCaptureService::class.java)
                            context.stopService(intent)
                        }
                    }
                )
            }
            
            if (wakeWordEnabled) {
                Text(
                    text = "Note: Voice Activation runs a continuous background service which may impact battery life. It requires microphone permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
