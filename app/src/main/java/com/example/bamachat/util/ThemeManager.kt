package com.example.bamachat.util

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object ThemeManager {
    
    enum class ThemeMode {
        LIGHT, DARK, SYSTEM
    }
    
    fun getDarkColorScheme(): ColorScheme = darkColorScheme(
        primary = Color(0xFF4F8CFF),
        secondary = Color(0xFF43C6AC),
        tertiary = Color(0xFFFFB157),
        background = Color(0xFF0F1424),
        surface = Color(0xFF1A1A2D),
        surfaceVariant = Color(0xFF2A2D32),
        error = Color(0xFFFF6B6B)
    )
    
    fun getLightColorScheme(): ColorScheme = lightColorScheme(
        primary = Color(0xFF3D5A80),
        secondary = Color(0xFF2B8A76),
        tertiary = Color(0xFFB87D2B),
        background = Color(0xFFF5F5F7),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEBEBF0),
        error = Color(0xFFD32F2F)
    )
    
    fun getColorScheme(mode: ThemeMode): ColorScheme {
        return when (mode) {
            ThemeMode.LIGHT -> getLightColorScheme()
            ThemeMode.DARK -> getDarkColorScheme()
            ThemeMode.SYSTEM -> getDarkColorScheme() // Default to dark, will be overridden by system
        }
    }
}
