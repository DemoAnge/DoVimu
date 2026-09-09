package com.dovimu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object DoVimuColors {
    val Orange = Color(0xFFFF6A00)
    val OrangeSoft = Color(0xFFFF8A3D)
    val Bg = Color(0xFF0B0B0E)
    val Surface = Color(0xFF15151B)
    val Surface2 = Color(0xFF1D1D26)
    val Text = Color(0xFFF5F5F7)
    val Mute = Color(0xFF9A9AA8)
    val Line = Color(0xFF2C2C36)
    val Success = Color(0xFF3DDC97)
    val Danger = Color(0xFFFF5C7A)
    val Audio = Color(0xFF7B8CFF)
}

private val scheme = darkColorScheme(
    primary = DoVimuColors.Orange,
    onPrimary = Color.White,
    secondary = DoVimuColors.Audio,
    onSecondary = Color.White,
    background = DoVimuColors.Bg,
    onBackground = DoVimuColors.Text,
    surface = DoVimuColors.Surface,
    onSurface = DoVimuColors.Text,
    surfaceVariant = DoVimuColors.Surface2,
    onSurfaceVariant = DoVimuColors.Mute,
    outline = DoVimuColors.Line,
    error = DoVimuColors.Danger,
    onError = Color.White,
)

@Composable
fun DoVimuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
