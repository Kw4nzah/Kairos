package com.kairos.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

// Dark palette shared with Poreia: warm brown neutrals, wine fills, rose accent, old-gold highlights, sage.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFE0959D),
    onPrimary = Color(0xFF2A0E13),
    primaryContainer = Color(0xFF8E2A38),
    onPrimaryContainer = Color(0xFFFFF8F0),
    secondary = Color(0xFFD7B978),
    onSecondary = Color(0xFF2E2108),
    secondaryContainer = Color(0xFF3A2E14),
    onSecondaryContainer = Color(0xFFD7B978),
    tertiary = Color(0xFFA9B99A),
    onTertiary = Color(0xFF1C2614),
    tertiaryContainer = Color(0xFF2B3324),
    onTertiaryContainer = Color(0xFFA9B99A),
    background = Color(0xFF161210),
    onBackground = Color(0xFFF2EEE8),
    surface = Color(0xFF161210),
    onSurface = Color(0xFFF2EEE8),
    surfaceVariant = Color(0xFF3A2F29),
    onSurfaceVariant = Color(0xFFC4BDB5),
    surfaceContainerLowest = Color(0xFF110D0B),
    surfaceContainerLow = Color(0xFF1C1613),
    surfaceContainer = Color(0xFF211A17),
    surfaceContainerHigh = Color(0xFF2A211D),
    surfaceContainerHighest = Color(0xFF332924),
    outline = Color(0xFF7A6D65),
    outlineVariant = Color(0xFF3A2F29),
    error = Color(0xFFE59A9A),
    onError = Color(0xFF3A0D0D)
)

@Composable
fun KairosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme) {
        // MaterialTheme alone leaves the content colour black. Screens drawn on a plain Box need this default.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
