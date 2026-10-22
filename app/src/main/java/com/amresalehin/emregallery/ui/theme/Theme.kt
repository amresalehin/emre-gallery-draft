package com.amresalehin.emregallery.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color(0xFF121212),
    secondary = Color(0xFF3E3E3E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAEAEA),
    onSecondaryContainer = Color(0xFF1E1E1E),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF121212),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF6E6E6E),
    surfaceContainer = Color(0xFFF0F0F2),
    surfaceContainerHigh = Color(0xFFE4E4E6),
    outline = Color(0xFFB8B8B8),
    outlineVariant = Color(0xFFDCDCE0)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF121212),
    primaryContainer = Color(0xFF262626),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFD8D8D8),
    onSecondary = Color(0xFF141414),
    secondaryContainer = Color(0xFF242424),
    onSecondaryContainer = Color(0xFFEAEAEA),
    background = Color(0xFF0E0E0E),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF222222),
    onSurfaceVariant = Color(0xFFA6A6A6),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF262626),
    outline = Color(0xFF555555),
    outlineVariant = Color(0xFF333333)
)

@Composable
fun EmreGalleryTheme(theme: String = "system", content: @Composable () -> Unit) {
    val darkTheme = when (theme) { "dark" -> true; "light" -> false; else -> androidx.compose.foundation.isSystemInDarkTheme() }
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

