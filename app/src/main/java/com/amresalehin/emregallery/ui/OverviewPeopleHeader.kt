package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.EnteFaceConstants
import com.amresalehin.emregallery.face.FaceProcessingCoordinator

@Composable
internal fun OverviewPeopleHeader(
    selectedEngineId: String,
    onSelectEngine: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "People",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        val context = LocalContext.current
        val isProcessingActive by FaceProcessingCoordinator.isProcessingActive.collectAsStateWithLifecycle()
        val isBusy = isProcessingActive || FaceProcessingCoordinator.isBusy(context)
        var modelMenuExpanded by remember { mutableStateOf(false) }

        val currentEngineName = if (selectedEngineId == BuffaloLFaceEngine.ENGINE_ID) {
            "Buffalo-L"
        } else {
            "YOLO5Face + MobileFaceNet"
        }

        Box {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .clickable(enabled = !isBusy) { modelMenuExpanded = true }
                    .alpha(if (!isBusy) 1.0f else 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = currentEngineName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Switch face model",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = modelMenuExpanded && !isBusy,
                onDismissRequest = { modelMenuExpanded = false }
            ) {
                FaceEngineOptionItem(
                    label = "Buffalo-L",
                    selected = selectedEngineId == BuffaloLFaceEngine.ENGINE_ID,
                    enabled = !isBusy,
                    onClick = {
                        modelMenuExpanded = false
                        if (!FaceProcessingCoordinator.isBusy(context)) {
                            onSelectEngine(BuffaloLFaceEngine.ENGINE_ID)
                        }
                    }
                )
                FaceEngineOptionItem(
                    label = "YOLO5Face + MobileFaceNet",
                    selected = selectedEngineId == EnteFaceConstants.ENGINE_ID,
                    enabled = !isBusy,
                    onClick = {
                        modelMenuExpanded = false
                        if (!FaceProcessingCoordinator.isBusy(context)) {
                            onSelectEngine(EnteFaceConstants.ENGINE_ID)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FaceEngineOptionItem(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        enabled = enabled,
        trailingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        onClick = onClick
    )
}
