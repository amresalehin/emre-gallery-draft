package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


/**
 * Ultra-aesthetic Theme card matching the Welcome Screen cards design in a single sleek row.
 * Features 26.dp rounded corners, frosted/theme-adaptive surface, circular viewfinder halo avatar,
 * and a compact 3-option capsule switch on the right (System | Light | Dark).
 */
@Composable
internal fun AestheticThemeCard(
    theme: String,
    isDark: Boolean,
    onTheme: (String) -> Unit
) {
    val cardBackground = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        Color(0x75F5F7FA)
    }

    val cardBorder = BorderStroke(
        1.dp,
        if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        else Color.White.copy(alpha = 0.65f)
    )

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = cardBackground,
        border = cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_theme_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Circular Halo Avatar + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Circular Halo Avatar with Signature Viewfinder Corners
                Surface(
                    shape = CircleShape,
                    color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    else Color.White.copy(alpha = 0.60f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ThemeSignatureIcon(tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            // Right: Compact 3-way Segmented Capsule (System | Light | Dark)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val isSystemSelected = theme != "light" && theme != "dark"
                    val isLightSelected = theme == "light"
                    val isDarkSelected = theme == "dark"

                    SingleRowThemeOption(
                        icon = Icons.Default.BrightnessAuto,
                        label = "System",
                        isSelected = isSystemSelected,
                        isDark = isDark,
                        onClick = { onTheme("system") },
                        testTag = "settings_theme_system"
                    )

                    SingleRowThemeOption(
                        icon = Icons.Default.LightMode,
                        label = "Light",
                        isSelected = isLightSelected,
                        isDark = isDark,
                        onClick = { onTheme("light") },
                        testTag = "settings_theme_light"
                    )

                    SingleRowThemeOption(
                        icon = Icons.Default.DarkMode,
                        label = "Dark",
                        isSelected = isDarkSelected,
                        isDark = isDark,
                        onClick = { onTheme("dark") },
                        testTag = "settings_theme_dark"
                    )
                }
            }
        }
    }
}

/**
 * Signature Theme Icon with Emre's iconic 4 viewfinder corners and central palette.
 */
@Composable
internal fun ThemeSignatureIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF181C22)
) {
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 1.6.dp.toPx()
            val cornerLength = 4.5.dp.toPx()
            val w = size.width
            val h = size.height

            // Top-left
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(cornerLength, 0f), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, cornerLength), strokeWidth)

            // Top-right
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w - cornerLength, 0f), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w, cornerLength), strokeWidth)

            // Bottom-left
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(cornerLength, h), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(0f, h - cornerLength), strokeWidth)

            // Bottom-right
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, h), androidx.compose.ui.geometry.Offset(w - cornerLength, h), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, h), androidx.compose.ui.geometry.Offset(w - cornerLength, h), strokeWidth)
        }
        Icon(
            imageVector = Icons.Default.Palette,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint
        )
    }
}

/**
 * Compact circular theme option button for the single-row Theme card.
 */
@Composable
internal fun SingleRowThemeOption(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) {
            if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF161A20)
        } else {
            Color.Transparent
        },
        modifier = Modifier
            .size(32.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) {
                    if (isDark) MaterialTheme.colorScheme.onPrimary else Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Aesthetic Settings Tile for About, etc. matching the 26.dp rounded card style.
 */
