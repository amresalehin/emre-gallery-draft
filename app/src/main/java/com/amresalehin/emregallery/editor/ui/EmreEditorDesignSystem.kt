package com.amresalehin.emregallery.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Emre Design System - Photo Editor Color Tokens
 *
 * Light Mode:
 * - Base surface: White #FFFFFF
 * - Panel surface: Soft grey #F5F5F5
 * - Elevated surface: White #FFFFFF
 * - Primary text / icons: Near-black #1A1A1A
 * - Secondary text / icons: Muted grey #6B6B6B
 * - Borders / separators: Soft grey #E5E5E5
 * - Active controls: Charcoal fill #242424, white foreground #FFFFFF
 * - Inactive controls: Transparent or soft neutral surface, muted foreground
 * - Disabled controls: Muted foreground and reduced emphasis
 *
 * Dark Mode:
 * - Base surface: Deep neutral #121212
 * - Panel surface: Dark neutral #1B1B1B
 * - Elevated surface: Raised neutral #242424
 * - Primary text / icons: Off-white #F5F5F5
 * - Secondary text / icons: Muted light grey #A3A3A3
 * - Borders / separators: Dark grey #383838
 * - Active controls: Light neutral fill #EAEAEA, dark foreground #171717
 * - Inactive controls: Transparent or dark panel surface, muted foreground
 * - Disabled controls: Muted foreground and reduced emphasis
 */
@Immutable
data class EmreEditorColors(
    val baseSurface: Color,
    val panelSurface: Color,
    val elevatedSurface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val border: Color,
    val activeControlBg: Color,
    val activeControlFg: Color,
    val inactiveControlBg: Color,
    val inactiveControlFg: Color,
    val disabledFg: Color,
    val isDark: Boolean
)

val LocalEmreEditorColors = staticCompositionLocalOf {
    EmreEditorColors(
        baseSurface = Color(0xFF121212),
        panelSurface = Color(0xFF1B1B1B),
        elevatedSurface = Color(0xFF242424),
        primaryText = Color(0xFFF5F5F5),
        secondaryText = Color(0xFFA3A3A3),
        border = Color(0xFF383838),
        activeControlBg = Color(0xFFEAEAEA),
        activeControlFg = Color(0xFF171717),
        inactiveControlBg = Color(0xFF1B1B1B),
        inactiveControlFg = Color(0xFFA3A3A3),
        disabledFg = Color(0xFF555555),
        isDark = true
    )
}

@Composable
fun rememberEmreEditorColors(): EmreEditorColors {
    // Detect dark theme from MaterialTheme background or system setting
    val isSystemDark = isSystemInDarkTheme()
    val isThemeDark = MaterialTheme.colorScheme.background.red < 0.5f || isSystemDark

    return remember(isThemeDark) {
        if (isThemeDark) {
            EmreEditorColors(
                baseSurface = Color(0xFF121212),
                panelSurface = Color(0xFF1B1B1B),
                elevatedSurface = Color(0xFF242424),
                primaryText = Color(0xFFF5F5F5),
                secondaryText = Color(0xFFA3A3A3),
                border = Color(0xFF383838),
                activeControlBg = Color(0xFFEAEAEA),
                activeControlFg = Color(0xFF171717),
                inactiveControlBg = Color(0xFF1B1B1B),
                inactiveControlFg = Color(0xFFA3A3A3),
                disabledFg = Color(0xFF555555),
                isDark = true
            )
        } else {
            EmreEditorColors(
                baseSurface = Color(0xFFFFFFFF),
                panelSurface = Color(0xFFF5F5F5),
                elevatedSurface = Color(0xFFFFFFFF),
                primaryText = Color(0xFF1A1A1A),
                secondaryText = Color(0xFF6B6B6B),
                border = Color(0xFFE5E5E5),
                activeControlBg = Color(0xFF242424),
                activeControlFg = Color(0xFFFFFFFF),
                inactiveControlBg = Color(0xFFF5F5F5),
                inactiveControlFg = Color(0xFF6B6B6B),
                disabledFg = Color(0xFFB0B0B0),
                isDark = false
            )
        }
    }
}

/**
 * Drag handle indicator used across Emre sheet panels and bottom dock.
 */
@Composable
fun EmreDragHandle(
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    Box(
        modifier = modifier
            .padding(vertical = 8.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(CircleShape)
            .background(colors.border)
    )
}

/**
 * Circular action button for Operation Toolbar and Top Bar (Cancel, Undo, Redo, Reset, Apply, Back, Versions).
 * Adheres to 48dp minimum touch target, Emre border, and spring click feedback.
 */
@Composable
fun EmreCircularButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    colors: EmreEditorColors = LocalEmreEditorColors.current,
    testTag: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.90f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "circular_btn_scale"
    )

    val iconTint by animateColorAsState(
        targetValue = if (enabled) colors.primaryText else colors.disabledFg,
        label = "circular_btn_icon_tint"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = colors.elevatedSurface,
            border = BorderStroke(1.dp, colors.border),
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

/**
 * Rounded pill button used in Subtools and Bottom Navigation.
 */
@Composable
fun EmrePillButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    colors: EmreEditorColors = LocalEmreEditorColors.current,
    testTag: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pill_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) colors.activeControlBg else colors.inactiveControlBg,
        label = "pill_bg"
    )

    val fgColor by animateColorAsState(
        targetValue = if (isSelected) colors.activeControlFg else colors.inactiveControlFg,
        label = "pill_fg"
    )

    val border = if (isSelected) {
        null
    } else {
        BorderStroke(1.dp, colors.border)
    }

    Surface(
        shape = CircleShape,
        color = bgColor,
        border = border,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = fgColor,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 0.2.sp
            )
        }
    }
}
