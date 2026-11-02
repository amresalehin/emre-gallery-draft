package com.amresalehin.emregallery.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Editing Domains for Emre Gallery Photo Editor.
 */
enum class EditorDomain(val label: String, val icon: ImageVector) {
    LIGHT("Light", Icons.Default.WbSunny),
    COLOR("Color", Icons.Default.Palette),
    CURVES("Curves", Icons.Default.Timeline),
    EFFECTS("Effects", Icons.Default.AutoFixHigh),
    DETAIL("Detail", Icons.Default.Details),
    BLUR("Blur", Icons.Default.BlurOn),
    VIGNETTE("Vignette", Icons.Default.Vignette),
    CROP("Crop", Icons.Default.Crop),
    PRESETS("Presets", Icons.Default.FilterVintage),
    RETOUCH("Retouch", Icons.Default.Healing),
    MASK("Mask", Icons.Default.Brush),
    TEXT("Text", Icons.Default.TextFields),
    SHAPE("Shape", Icons.Default.Category),
    CALLOUT("Callout", Icons.Default.ChatBubbleOutline),
    FRAME("Frame", Icons.Default.CropPortrait),
    OVERLAYS("Overlays", Icons.Default.Layers),
    AI_TOOLS("AI Tools", Icons.Default.AutoAwesome)
}

/**
 * Zone 05: Bottom Navigation
 * Role: Docked primary access layer across core editing areas.
 *
 * Distinctive Emre styling:
 * - Rounded pill card docked at bottom
 * - Subtle top drag handle indicator
 * - Dynamic active pill indicators with spring physics
 * - Adheres strictly to Emre Design System Light/Dark tokens
 * - Properly insets navigation bars safe area
 */
@Composable
fun EditorBottomNavigation(
    selectedDomain: EditorDomain,
    onSelectDomain: (EditorDomain) -> Unit,
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colors.panelSurface,
        border = BorderStroke(1.dp, colors.border),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Subtle top indicator as seen in wireframe
            EmreDragHandle(colors = colors)

            // Scrollable Pill Row of Primary Editing Domains
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorDomain.values().forEach { domain ->
                    val isSelected = domain == selectedDomain
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.92f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "domain_scale"
                    )

                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) colors.activeControlBg else colors.elevatedSurface,
                        label = "domain_bg"
                    )

                    val fgColor by animateColorAsState(
                        targetValue = if (isSelected) colors.activeControlFg else colors.secondaryText,
                        label = "domain_fg"
                    )

                    val border = if (isSelected) null else BorderStroke(1.dp, colors.border)

                    Surface(
                        shape = CircleShape,
                        color = bgColor,
                        border = border,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelectDomain(domain) }
                            )
                            .testTag("domain_tab_${domain.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = domain.icon,
                                contentDescription = null,
                                tint = fgColor,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = domain.label,
                                color = fgColor,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                letterSpacing = 0.2.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
