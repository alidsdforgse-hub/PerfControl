package com.example.perfcontrol.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
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

val LocalGlass = compositionLocalOf { false }

private fun glassScheme(base: androidx.compose.material3.ColorScheme, dark: Boolean): androidx.compose.material3.ColorScheme {
    val fill = if (dark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.58f)
    val fillLow = if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.45f)
    return base.copy(
        surfaceContainerLowest = fillLow,
        surfaceContainerLow = fillLow,
        surfaceContainer = fill,
        surfaceContainerHighest = fill,
    )
}

@Composable
fun PerfTheme(dark: Boolean, glass: Boolean = false, content: @Composable () -> Unit) {
    val base = if (dark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = if (glass) glassScheme(base, dark) else base,
        shapes = AppShapes,
    ) {
        CompositionLocalProvider(LocalGlass provides glass, content = content)
    }
}

/** خلفية ملوّنة متحركة ببطء تظهر من خلف البطاقات الشفافة فتعطي إحساس الزجاج. */
@Composable
fun GlassBackground(dark: Boolean, glass: Boolean, content: @Composable () -> Unit) {
    val bg = if (dark) Color(0xFF0F0B1E) else Color(0xFFF1EEFA)
    Box(Modifier.fillMaxSize().background(bg)) {
        if (glass) {
            val t = rememberInfiniteTransition(label = "blobs")
            val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
            val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
            val strength = if (dark) 0.55f else 0.42f
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val r = w * 0.85f
                fun blob(color: Color, cx: Float, cy: Float) {
                    drawCircle(
                        Brush.radialGradient(listOf(color.copy(alpha = strength), Color.Transparent), center = Offset(cx, cy), radius = r),
                        radius = r,
                        center = Offset(cx, cy),
                    )
                }
                blob(Color(0xFF7C4DFF), w * (0.15f + 0.5f * a), h * (0.12f + 0.15f * b))
                blob(Color(0xFFFF6FB5), w * (0.95f - 0.5f * b), h * (0.5f + 0.1f * a))
                blob(Color(0xFF26C6DA), w * (0.2f + 0.4f * b), h * (0.92f - 0.2f * a))
            }
        }
        content()
    }
}
