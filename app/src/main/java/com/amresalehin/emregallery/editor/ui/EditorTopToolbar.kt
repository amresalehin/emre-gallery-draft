package com.amresalehin.emregallery.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Zone 01: Top Toolbar (Persistent)
 * Controls: Back · Versions · Save Options
 * Role: Global actions and document-level state management.
 * Follows Emre Design System and exact layout from wireframe.
 */
@Composable
fun EditorTopToolbar(
    onBack: () -> Unit,
    onOpenVersions: () -> Unit,
    onQuickSave: () -> Unit,
    onSaveCopy: () -> Unit,
    onExportOptions: () -> Unit,
    onShare: () -> Unit,
    onRevert: () -> Unit,
    onCopySettings: () -> Unit,
    onPasteSettings: () -> Unit,
    canPasteSettings: Boolean,
    versionCount: Int = 0,
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    var showSaveMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back Button (Circular 44dp touch target)
        EmreCircularButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            size = 42.dp,
            iconSize = 20.dp,
            colors = colors,
            testTag = "editor_back_button"
        )

        // Right Group: Versions · Separator · Save Options
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Versions Button (Stacked layers icon with badge if edits exist)
            Box {
                EmreCircularButton(
                    icon = Icons.Default.Layers,
                    contentDescription = "Versions",
                    onClick = onOpenVersions,
                    size = 42.dp,
                    iconSize = 20.dp,
                    colors = colors,
                    testTag = "editor_versions_button"
                )
                if (versionCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = colors.activeControlBg,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp)
                            .size(10.dp)
                    ) {}
                }
            }

            // Vertical Separator Line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .clip(CircleShape)
                    .background(colors.border)
            )

            // Save Options Pill Button ("Save ▾")
            Box {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.93f else 1.0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "save_pill_scale"
                )

                Surface(
                    shape = CircleShape,
                    color = colors.activeControlBg,
                    modifier = Modifier
                        .height(38.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { showSaveMenu = true }
                        )
                        .testTag("editor_save_options_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Save",
                            color = colors.activeControlFg,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Save Options",
                            tint = colors.activeControlFg,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Dropdown Menu with Emre Design System Styling
                DropdownMenu(
                    expanded = showSaveMenu,
                    onDismissRequest = { showSaveMenu = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = colors.elevatedSurface,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Save",
                                color = colors.primaryText,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = colors.primaryText
                            )
                        },
                        onClick = {
                            showSaveMenu = false
                            onQuickSave()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text("Save as copy", color = colors.primaryText)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = colors.primaryText
                            )
                        },
                        onClick = {
                            showSaveMenu = false
                            onSaveCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text("Export options...", color = colors.primaryText)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = colors.primaryText
                            )
                        },
                        onClick = {
                            showSaveMenu = false
                            onExportOptions()
                        }
                    )
                    HorizontalDivider(color = colors.border)
                    DropdownMenuItem(
                        text = {
                            Text("Copy edit settings", color = colors.primaryText)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ContentPasteGo,
                                contentDescription = null,
                                tint = colors.primaryText
                            )
                        },
                        onClick = {
                            showSaveMenu = false
                            onCopySettings()
                        }
                    )
                    if (canPasteSettings) {
                        DropdownMenuItem(
                            text = {
                                Text("Paste edit settings", color = colors.primaryText)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    tint = colors.primaryText
                                )
                            },
                            onClick = {
                                showSaveMenu = false
                                onPasteSettings()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text("Share photo", color = colors.primaryText)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = colors.primaryText
                            )
                        },
                        onClick = {
                            showSaveMenu = false
                            onShare()
                        }
                    )
                    HorizontalDivider(color = colors.border)
                    DropdownMenuItem(
                        text = {
                            Text("Revert to original", color = colors.secondaryText)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = null,
                                tint = colors.secondaryText
                            )
                        },
                        onClick = {
                            showSaveMenu = false
                            onRevert()
                        }
                    )
                }
            }
        }
    }
}
