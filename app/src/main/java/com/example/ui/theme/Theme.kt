package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFF4F378B),
    tertiary = Color(0xFF381E72),
    background = Color(0xFF1A1C1E),
    surface = Color(0xFF2D2F33),
    onPrimary = Color(0xFF381E72),
    onSecondary = Color(0xFFEADDFF),
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6)
  )

private val LightColorScheme = DarkColorScheme // Force dark theme aesthetic always for Elegant Dark

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
