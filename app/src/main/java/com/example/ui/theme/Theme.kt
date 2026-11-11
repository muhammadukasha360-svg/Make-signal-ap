package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SignalXDarkColorScheme = darkColorScheme(
    primary = AccentYellow,
    onPrimary = DarkBackground,
    primaryContainer = AccentYellowGlow,
    onPrimaryContainer = AccentYellow,
    secondary = AccentCyan,
    onSecondary = DarkBackground,
    secondaryContainer = DarkCardElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = BullishGreen,
    onTertiary = DarkBackground,
    tertiaryContainer = BullishGreenGlow,
    onTertiaryContainer = BullishGreen,
    error = BearishRed,
    onError = DarkBackground,
    errorContainer = BearishRedGlow,
    onErrorContainer = BearishRed,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkCardElevated
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // SignalX AI is a dedicated professional dark trading terminal
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SignalXDarkColorScheme,
        typography = Typography,
        content = content
    )
}
