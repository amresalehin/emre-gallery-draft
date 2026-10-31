package com.amresalehin.emregallery.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PhotoEditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onQuickSave: () -> Unit,
    onContinueExport: () -> Unit,
    onEditWithAi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .background(Color.Black.copy(alpha = 0.18f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp).testTag("editor_close_button")) {
            Icon(Icons.Default.Close, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(40.dp).testTag("editor_undo_button")) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.32f), modifier = Modifier.size(21.dp))
            }
            IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(40.dp).testTag("editor_redo_button")) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.32f), modifier = Modifier.size(21.dp))
            }
            IconButton(onClick = onContinueExport, modifier = Modifier.size(40.dp).testTag("editor_share_button")) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(21.dp))
            }
            IconButton(onClick = onQuickSave, modifier = Modifier.size(40.dp).testTag("editor_save_button")) {
                Icon(Icons.Default.SaveAlt, contentDescription = "Save copy to device", tint = Color.White, modifier = Modifier.size(21.dp))
            }
            IconButton(onClick = onEditWithAi, modifier = Modifier.size(40.dp).testTag("editor_more_button")) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}
