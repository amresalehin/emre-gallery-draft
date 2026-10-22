package com.amresalehin.emregallery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF24364B), onPrimary = Color.White,
    secondary = Color(0xFF536780), onSecondary = Color.White,
    background = Color(0xFFF7F8FB), onBackground = Color(0xFF131A25),
    surface = Color(0xFFF7F8FB), onSurface = Color(0xFF131A25),
    surfaceVariant = Color(0xFFE8ECF2), onSurfaceVariant = Color(0xFF596579),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFB7CBE4), onPrimary = Color(0xFF193047),
    secondary = Color(0xFFBBC9DB), onSecondary = Color(0xFF26384B),
    background = Color(0xFF10151C), onBackground = Color(0xFFE5EAF1),
    surface = Color(0xFF10151C), onSurface = Color(0xFFE5EAF1),
    surfaceVariant = Color(0xFF242C37), onSurfaceVariant = Color(0xFFB5C0CF),
)

@Composable
fun EmreGalleryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
