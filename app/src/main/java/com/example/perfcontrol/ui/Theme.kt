package com.example.perfcontrol.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Good = Color(0xFF2ECC71)
val Warn = Color(0xFFF5A524)
val Bad = Color(0xFFFF5A5F)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B34D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1B0F4A),
    secondary = Color(0xFFE8650A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3CF),
    onSecondaryContainer = Color(0xFF3D1A00),
    background = Color(0xFFF7F5FC),
    onBackground = Color(0xFF1B1830),
    surface = Color(0xFFF7F5FC),
    onSurface = Color(0xFF1B1830),
    surfaceVariant = Color(0xFFE6E1F3),
    onSurfaceVariant = Color(0xFF4A4660),
    outline = Color(0xFFC9C3DD),
    error = Color(0xFFD93A3A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1EEFA),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFEBFA),
    surfaceContainerHighest = Color(0xFFE9E4F7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB59CFF),
    onPrimary = Color(0xFF2A0F82),
    primaryContainer = Color(0xFF3F23A8),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFFFA866),
    onSecondary = Color(0xFF4A2300),
    secondaryContainer = Color(0xFF6B3500),
    onSecondaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF0F0B1E),
    onBackground = Color(0xFFE9E4FA),
    surface = Color(0xFF0F0B1E),
    onSurface = Color(0xFFE9E4FA),
    surfaceVariant = Color(0xFF2A2442),
    onSurfaceVariant = Color(0xFFCBC4E0),
    outline = Color(0xFF8A83A6),
    error = Color(0xFFFF8A8A),
    surfaceContainerLowest = Color(0xFF0B0816),
    surfaceContainerLow = Color(0xFF150F26),
    surfaceContainer = Color(0xFF1A1530),
    surfaceContainerHigh = Color(0xFF221C3C),
    surfaceContainerHighest = Color(0xFF2A2442),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun PerfTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
