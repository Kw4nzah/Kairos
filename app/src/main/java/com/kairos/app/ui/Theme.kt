package com.kairos.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette: deep teal + warm amber on paper / ink neutrals. Dynamic color is intentionally off.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6B66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6EDE8),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDB0),
    onSecondaryContainer = Color(0xFF2B1800),
    tertiary = Color(0xFFB4442F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD3),
    onTertiaryContainer = Color(0xFF3F0900),
    background = Color(0xFFF8F6F1),
    onBackground = Color(0xFF181C1B),
    surface = Color(0xFFF8F6F1),
    onSurface = Color(0xFF181C1B),
    surfaceVariant = Color(0xFFE1E8E5),
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F0EB),
    surfaceContainer = Color(0xFFECEAE5),
    surfaceContainerHigh = Color(0xFFE6E4DF),
    surfaceContainerHighest = Color(0xFFE0DED9),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD8CF),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C),
    onPrimaryContainer = Color(0xFFC6EDE8),
    secondary = Color(0xFFF2BC6B),
    onSecondary = Color(0xFF462B00),
    secondaryContainer = Color(0xFF63400A),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFFFFB4A4),
    onTertiary = Color(0xFF690000),
    tertiaryContainer = Color(0xFF8F2C19),
    onTertiaryContainer = Color(0xFFFFDAD3),
    background = Color(0xFF0E1413),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1413),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF161D1C),
    surfaceContainer = Color(0xFF1A2120),
    surfaceContainerHigh = Color(0xFF242B2A),
    surfaceContainerHighest = Color(0xFF2F3635),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4947),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun KairosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
