package com.example.bamachat.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

private val FuturisticDarkColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D1E6D),
    onPrimaryContainer = Color(0xFFE8D5FF),
    secondary = NeonCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF003545),
    onSecondaryContainer = Color(0xFFC2E8FF),
    tertiary = NeonPink,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF5C1130),
    onTertiaryContainer = Color(0xFFFFD9E2),
    error = Error,
    onError = Color.Black,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF44445A),
    outlineVariant = Color(0xFF333348),
    inverseSurface = Color(0xFFE8E8F0),
    inverseOnSurface = Color(0xFF1A1A2E),
    inversePrimary = Color(0xFF6B3FA0),
    surfaceTint = NeonPurple
)

private val FuturisticLightColorScheme = lightColorScheme(
    primary = Color(0xFF6B3FA0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8D5FF),
    onPrimaryContainer = Color(0xFF240046),
    secondary = Color(0xFF006B7D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2E8FF),
    onSecondaryContainer = Color(0xFF001F28),
    tertiary = Color(0xFF9C254D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF3E001A),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4D0)
)

@Composable
fun BamaChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean? = null,  // null = read from settings
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    val useDynamicColor = dynamicColor ?: prefs.getBoolean("material_you_enabled", true)

    val colorScheme = when {
        useDynamicColor -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FuturisticDarkColorScheme
        else -> FuturisticLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
