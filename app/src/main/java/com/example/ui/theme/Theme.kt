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
    primary = Color(0xFF4F46E5),
    secondary = Color(0xFF4F46E5),
    tertiary = AccentGold,
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF1A1A1A),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    outline = Color(0xFF2E2E2E)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ForestGreen,
    secondary = SagePillBg,
    tertiary = AccentGold,
    background = IvoryBackground,
    surface = CardBackground,
    onPrimary = IvoryBackground,
    onSecondary = SagePillText,
    onBackground = CharcoalDark,
    onSurface = CharcoalDark,
    outline = OutlineMedium
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
