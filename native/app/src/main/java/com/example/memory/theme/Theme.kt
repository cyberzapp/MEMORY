package com.example.memory.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    secondary = Gray400,
    onSecondary = Black,
    background = Gray900,
    onBackground = White,
    surface = Gray900,
    onSurface = White,
    surfaceVariant = Gray800,
    onSurfaceVariant = Gray400,
    error = Red500
)

private val LightColorScheme = lightColorScheme(
    primary = Black,
    onPrimary = White,
    secondary = Gray800,
    onSecondary = White,
    background = White,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray800,
    error = Red500
)

@Composable
fun MemoryTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color to enforce the clean Black & White brand look
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
