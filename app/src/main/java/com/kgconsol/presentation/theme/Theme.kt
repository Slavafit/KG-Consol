package com.kgconsol.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette: warehouse / industrial feel — deep blue + amber accent
private val KGBlue = Color(0xFF1A3A5C)
private val KGBlueDark = Color(0xFF0D2137)
private val KGAmber = Color(0xFFFFA726)
private val KGGreen = Color(0xFF2E7D32)
private val KGGreenLight = Color(0xFF66BB6A)

private val LightColorScheme = lightColorScheme(
    primary = KGBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B41),
    secondary = Color(0xFF2E5B8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E4FF),
    tertiary = KGGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8F0BB),
    onTertiaryContainer = Color(0xFF002108),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF5F7FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFDDE3EA),
    outline = Color(0xFF72787E)
)

@Composable
fun KGConsolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}
