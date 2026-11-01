package com.amresalehin.emregallery.editor.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*

/**
 * Zone 02: Main Workspace
 * Controls: Image / Video Canvas
 * Role: Live rendering, preview, spatial transformation, and direct canvas selection.
 *
 * Enclosed in a rounded soft card container with subtle borders matching the Emre wireframe.
 */
@Composable
fun EditorWorkspace(
    previewBitmap: Bitmap?,
    originalBitmap: Bitmap?,
    activeTool: EditorTool,
    selectedBrushType: BrushType,
    selectedBrushColor: Color,
    brushWidth: Float,
    brushOpacity: Float,
    brushHardness: Float,
    activeRetouchType: RetouchType,
    healingIntensity: Float,
    healingTintColor: Color,
    cropAspectRatio: Float?,
    isCurveMode: Boolean,
    activeCurveChannel: CurveChannel,
    sessionState: EditSessionState,
    viewModel: PhotoEditorViewModel,
    activeDraggingSliderName: String?,
    activeDraggingSliderValue: String,
    isReplaying: Boolean,
    replayProgress: Float,
    isEyedropperActive: Boolean,
    onColorPicked: (Color) -> Unit,
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.panelSurface,
        border = BorderStroke(1.dp, colors.border),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .testTag("editor_workspace_container")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                PhotoEditorCanvas(
                    bitmap = previewBitmap,
                    originalBitmap = originalBitmap,
                    activeTool = activeTool,
                    brushType = selectedBrushType,
                    brushColor = selectedBrushColor,
                    brushWidth = brushWidth,
                    activeRetouchType = activeRetouchType,
                    retouchIntensity = healingIntensity,
                    retouchTintColor = healingTintColor,
                    cropAspectRatio = cropAspectRatio,
                    isCurveMode = isCurveMode,
                    activeCurveChannel = activeCurveChannel,
                    curvePoints = when (activeCurveChannel) {
                        CurveChannel.RGB -> sessionState.adjustments.curves.master
                        CurveChannel.RED -> sessionState.adjustments.curves.red
                        CurveChannel.GREEN -> sessionState.adjustments.curves.green
                        CurveChannel.BLUE -> sessionState.adjustments.curves.blue
                    },
                    onCurvePointChanged = { channel, idx, inVal, outVal ->
                        viewModel.updateCurvePoint(channel, idx, inVal, outVal)
                    },
                    onCurvePointAdded = { channel, inVal, outVal ->
                        viewModel.addCurvePoint(channel, inVal, outVal)
                    },
                    onDrawingStrokeFinished = { stroke ->
                        if (activeTool == EditorTool.BRUSHES) {
                            viewModel.addDrawingStroke(stroke)
                        } else {
                            viewModel.addStrokeToActiveMask(stroke)
                        }
                    },
                    onRetouchStrokeFinished = { viewModel.addRetouchStroke(it) },
                    onCropRectPreview = { viewModel.updateCropPreview(it) },
                    onCropRectChanged = { viewModel.commitCrop(it) },
                    maskOverlaySettings = if (activeDraggingSliderName != null) {
                        sessionState.maskOverlaySettings.copy(showOverlay = false)
                    } else {
                        sessionState.maskOverlaySettings
                    },
                    activeMaskLayer = sessionState.maskLayers.firstOrNull { it.id == sessionState.activeMaskId },
                    brushOpacity = brushOpacity,
                    brushHardness = brushHardness,
                    symmetryMode = sessionState.drawSymmetryMode,
                    isEyedropperActive = isEyedropperActive,
                    onColorPicked = onColorPicked,
                    activeDrawLayerId = sessionState.activeDrawLayerId
                )
            } else {
                // Placeholder State (Matching the wireframe's rounded image card with icon)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "No image preview",
                        tint = colors.secondaryText.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Loading workspace canvas...",
                        color = colors.secondaryText,
                        fontSize = 13.sp
                    )
                }
            }

            // Floating HUD Slider Banner at Top of Canvas
            AnimatedVisibility(
                visible = activeDraggingSliderName != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                if (activeDraggingSliderName != null) {
                    SliderHudBanner(
                        sliderName = activeDraggingSliderName,
                        sliderValue = activeDraggingSliderValue
                    )
                }
            }

            // Replay progress indicator
            if (isReplaying) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    shape = CircleShape,
                    color = colors.elevatedSurface,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            progress = { replayProgress },
                            modifier = Modifier.size(16.dp),
                            color = colors.primaryText,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Replaying edit history...",
                            color = colors.primaryText,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
