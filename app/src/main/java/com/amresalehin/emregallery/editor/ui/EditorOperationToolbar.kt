package com.amresalehin.emregallery.editor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Zone 03: Operation Toolbar
 * Controls: Cancel · Undo · Redo · Reset · Apply
 * Role: Immediate execution and non-destructive action handling.
 *
 * Sits directly between the Workspace and Contextual Editing Panel.
 * Follows Emre Design System with pure circular controls and 48dp touch targets.
 */
@Composable
fun EditorOperationToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Cancel
        EmreCircularButton(
            icon = Icons.Default.Close,
            contentDescription = "Cancel",
            onClick = onCancel,
            size = 44.dp,
            iconSize = 20.dp,
            colors = colors,
            testTag = "operation_cancel_button"
        )

        // 2. Undo
        EmreCircularButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            contentDescription = "Undo",
            onClick = onUndo,
            enabled = canUndo,
            size = 44.dp,
            iconSize = 20.dp,
            colors = colors,
            testTag = "operation_undo_button"
        )

        // 3. Redo
        EmreCircularButton(
            icon = Icons.AutoMirrored.Filled.Redo,
            contentDescription = "Redo",
            onClick = onRedo,
            enabled = canRedo,
            size = 44.dp,
            iconSize = 20.dp,
            colors = colors,
            testTag = "operation_redo_button"
        )

        // 4. Reset
        EmreCircularButton(
            icon = Icons.Default.RestartAlt,
            contentDescription = "Reset",
            onClick = onReset,
            size = 44.dp,
            iconSize = 20.dp,
            colors = colors,
            testTag = "operation_reset_button"
        )

        // 5. Apply
        EmreCircularButton(
            icon = Icons.Default.Check,
            contentDescription = "Apply",
            onClick = onApply,
            size = 44.dp,
            iconSize = 20.dp,
            colors = colors,
            testTag = "operation_apply_button"
        )
    }
}
