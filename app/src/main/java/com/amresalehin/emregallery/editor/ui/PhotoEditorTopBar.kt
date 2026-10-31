package com.amresalehin.emregallery.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Minimal, monochrome top action bar for the photo editor.
 * Eliminates unnecessary headers, keeping total focus on the image canvas.
 */
@Composable
fun PhotoEditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onQuickSave: () -> Unit,
    onShare: () -> Unit,
    onRevert: () -> Unit,
    onCopySettings: () -> Unit,
    onPasteSettings: () -> Unit,
    canPasteSettings: Boolean,
    onContinueExport: () -> Unit,
    onEditWithAi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.75f),
                        Color.Black.copy(alpha = 0.35f),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Close / Back button (Minimal round pill touch target)
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("editor_close_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Right-side actions: Undo, Redo, Share, Save, Overflow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Undo
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("editor_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.28f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Redo
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("editor_redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.28f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Share
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("editor_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Quick Save (Checkmark in subtle monochrome capsule)
                Surface(
                    onClick = onQuickSave,
                    shape = CircleShape,
                    color = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("editor_save_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save photo",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Overflow Menu [⋮]
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("editor_overflow_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(0xFF1E1E1E),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save copy to gallery", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Check, null, tint = Color.White) },
                            onClick = {
                                showMenu = false
                                onQuickSave()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy edit settings", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = Color.White) },
                            onClick = {
                                showMenu = false
                                onCopySettings()
                            }
                        )
                        if (canPasteSettings) {
                            DropdownMenuItem(
                                text = { Text("Paste edit settings", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.ContentPaste, null, tint = Color.White) },
                                onClick = {
                                    showMenu = false
                                    onPasteSettings()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Revert to original", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.RestartAlt, null, tint = Color.White) },
                            onClick = {
                                showMenu = false
                                onRevert()
                            }
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        DropdownMenuItem(
                            text = { Text("Export options...", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Share, null, tint = Color.White) },
                            onClick = {
                                showMenu = false
                                onContinueExport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("AI neural enhance", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = Color.White) },
                            onClick = {
                                showMenu = false
                                onEditWithAi()
                            }
                        )
                    }
                }
            }
        }
    }
}
