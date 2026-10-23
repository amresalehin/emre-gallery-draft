package com.amresalehin.emregallery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// Emre Gallery is monochrome by default. Pink is reserved for deliberate accents;
// it is not used as the primary color for every button or container.
private val EmrePink = Color(0xFFE85D8F)

private val Light = lightColorScheme(
    primary = Color(0xFF242424),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8E8E8),
    onPrimaryContainer = Color(0xFF181818),
    secondary = Color(0xFF555555),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECECEC),
    onSecondaryContainer = Color(0xFF252525),
    tertiary = EmrePink,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBE5ED),
    onTertiaryContainer = Color(0xFF4B172A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF484848),
    surfaceTint = Color(0x00000000),
    inverseSurface = Color(0xFF292929),
    inverseOnSurface = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFFD0D0D0),
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFFD2D2D2),
    scrim = Color(0x99000000)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE8E8E8),
    onPrimary = Color(0xFF191919),
    primaryContainer = Color(0xFF353535),
    onPrimaryContainer = Color(0xFFF3F3F3),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF202020),
    secondaryContainer = Color(0xFF303030),
    onSecondaryContainer = Color(0xFFEAEAEA),
    tertiary = EmrePink,
    onTertiary = Color(0xFF2B0B18),
    tertiaryContainer = Color(0xFF512536),
    onTertiaryContainer = Color(0xFFFFD9E5),
    background = Color(0xFF101010),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF101010),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF292929),
    onSurfaceVariant = Color(0xFFC4C4C4),
    surfaceTint = Color(0x00000000),
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF242424),
    inversePrimary = Color(0xFF444444),
    outline = Color(0xFF858585),
    outlineVariant = Color(0xFF454545),
    scrim = Color(0x99000000)
)

@Composable
fun EmreGalleryTheme(
    theme: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (darkTheme) Dark else Light
    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content
        )
    }
}
