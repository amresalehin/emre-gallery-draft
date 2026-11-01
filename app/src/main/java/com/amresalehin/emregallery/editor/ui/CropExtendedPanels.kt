package com.amresalehin.emregallery.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*

/**
 * Extended Crop & Transform subcategory navigation and controls:
 * Aspect Ratio, Shape Crop, Perspective & Corner Pin, Liquify, Dispersion, Motion Blur, Resize.
 */
@Composable
fun CropExtendedPanel(
    transform: TransformParams,
    selectedCropPreset: AspectRatioPreset,
    onSelectCropPreset: (AspectRatioPreset) -> Unit,
    onRotate90: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onStraightenChange: (Float) -> Unit,
    onStraightenFinished: () -> Unit,
    onAutoLevel: () -> Unit,
    onShapeCropChange: (ShapeCropType?) -> Unit,
    onPerspectiveChange: (x: Float, y: Float) -> Unit,
    onCornerPinReset: () -> Unit,
    onCornerPinEnable: () -> Unit,
    onLiquifyModeChange: (LiquifyMode) -> Unit,
    currentLiquifyMode: LiquifyMode,
    liquifyRadius: Float,
    onLiquifyRadiusChange: (Float) -> Unit,
    liquifyStrength: Float,
    onLiquifyStrengthChange: (Float) -> Unit,
    onClearLiquify: () -> Unit,
    onDispersionChange: (Float) -> Unit,
    onMotionChange: (amount: Float, angle: Float) -> Unit,
    onScaleFactorChange: (Float) -> Unit,
    onResetCrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubTool by remember { mutableStateOf("Ratio") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Sub-tool switcher pills
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val subTools = listOf(
                "Ratio" to "Crop & Rotate",
                "Shape" to "Shape Crop",
                "Perspective" to "Perspective",
                "Warp" to "Liquify",
                "Motion" to "Dispersion & Motion",
                "Resize" to "Resize"
            )
            items(subTools) { (id, label) ->
                val isSelected = activeSubTool == id
                Surface(
                    onClick = { activeSubTool = id },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    modifier = Modifier.height(34.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when (activeSubTool) {
            "Ratio" -> {
                CropControlsPanel(
                    selectedPreset = selectedCropPreset,
                    straightenAngle = transform.straightenAngle,
                    onSelectPreset = onSelectCropPreset,
                    onRotate90 = onRotate90,
                    onFlipH = onFlipH,
                    onFlipV = onFlipV,
                    onStraightenChange = onStraightenChange,
                    onStraightenFinished = onStraightenFinished,
                    onAutoLevel = onAutoLevel,
                    onResetCrop = onResetCrop
                )
            }
            "Shape" -> {
                ShapeCropSubPanel(
                    currentShape = transform.shapeCrop,
                    onSelectShape = onShapeCropChange
                )
            }
            "Perspective" -> {
                PerspectiveSubPanel(
                    perspectiveX = transform.perspectiveX,
                    perspectiveY = transform.perspectiveY,
                    cornerPins = transform.cornerPins,
                    onPerspectiveChange = onPerspectiveChange,
                    onCornerPinReset = onCornerPinReset,
                    onCornerPinEnable = onCornerPinEnable
                )
            }
            "Warp" -> {
                LiquifySubPanel(
                    currentMode = currentLiquifyMode,
                    onModeChange = onLiquifyModeChange,
                    radius = liquifyRadius,
                    onRadiusChange = onLiquifyRadiusChange,
                    strength = liquifyStrength,
                    onStrengthChange = onLiquifyStrengthChange,
                    pointCount = transform.liquifyPoints.size,
                    onClear = onClearLiquify
                )
            }
            "Motion" -> {
                MotionDispersionSubPanel(
                    dispersionAmount = transform.dispersionAmount,
                    onDispersionChange = onDispersionChange,
                    motionAmount = transform.motionAmount,
                    motionAngle = transform.motionAngle,
                    onMotionChange = onMotionChange
                )
            }
            "Resize" -> {
                ResizeSubPanel(
                    scaleFactor = transform.scaleFactor,
                    onScaleFactorChange = onScaleFactorChange
                )
            }
        }
    }
}

@Composable
fun ShapeCropSubPanel(
    currentShape: ShapeCropType?,
    onSelectShape: (ShapeCropType?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Geometric Shape Crop", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (currentShape != null) {
                TextButton(onClick = { onSelectShape(null) }) {
                    Text("Clear Shape", color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ShapeCropType.entries) { shape ->
                val isSelected = currentShape == shape
                val icon = when (shape) {
                    ShapeCropType.RECTANGLE -> Icons.Default.CropSquare
                    ShapeCropType.CIRCLE -> Icons.Default.Circle
                    ShapeCropType.OVAL -> Icons.Default.PanoramaFishEye
                    ShapeCropType.TRIANGLE -> Icons.Default.ChangeHistory
                    ShapeCropType.HEART -> Icons.Default.Favorite
                    ShapeCropType.STAR -> Icons.Default.Star
                    ShapeCropType.HEXAGON -> Icons.Default.Hexagon
                    ShapeCropType.DIAMOND -> Icons.Default.Diamond
                    ShapeCropType.SHIELD -> Icons.Default.Shield
                }
                Surface(
                    onClick = { onSelectShape(if (isSelected) null else shape) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color.White else Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.size(68.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = shape.label,
                            tint = if (isSelected) Color.Black else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = shape.label,
                            color = if (isSelected) Color.Black else Color(0xFFAAAAAA),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PerspectiveSubPanel(
    perspectiveX: Float,
    perspectiveY: Float,
    cornerPins: List<CornerPinPoint>?,
    onPerspectiveChange: (x: Float, y: Float) -> Unit,
    onCornerPinReset: () -> Unit,
    onCornerPinEnable: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Perspective Skew & Pinning", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        LrSliderRow(
            title = "Horizontal Skew",
            value = perspectiveX,
            displayValue = "${perspectiveX.toInt()}°",
            valueRange = -30f..30f,
            minLabel = "−30°",
            maxLabel = "+30°",
            onDragStart = {},
            onValueChange = { onPerspectiveChange(it, perspectiveY) },
            onValueChangeFinished = {}
        )

        LrSliderRow(
            title = "Vertical Skew",
            value = perspectiveY,
            displayValue = "${perspectiveY.toInt()}°",
            valueRange = -30f..30f,
            minLabel = "−30°",
            maxLabel = "+30°",
            onDragStart = {},
            onValueChange = { onPerspectiveChange(perspectiveX, it) },
            onValueChangeFinished = {}
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isCornerPinActive = !cornerPins.isNullOrEmpty()
            Button(
                onClick = {
                    if (isCornerPinActive) onCornerPinReset() else onCornerPinEnable()
                },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCornerPinActive) Color(0xFF2979FF) else Color(0xFF262626),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.PinDrop, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isCornerPinActive) "Disable 4-Pin" else "Enable 4-Corner Pin", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    onPerspectiveChange(0f, 0f)
                    onCornerPinReset()
                },
                modifier = Modifier.height(42.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Text("Reset", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun LiquifySubPanel(
    currentMode: LiquifyMode,
    onModeChange: (LiquifyMode) -> Unit,
    radius: Float,
    onRadiusChange: (Float) -> Unit,
    strength: Float,
    onStrengthChange: (Float) -> Unit,
    pointCount: Int,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Liquify & Warp ($pointCount strokes)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (pointCount > 0) {
                TextButton(onClick = onClear) {
                    Text("Clear All", color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Liquify Mode Pills: Push, Pull, Expand, Shrink
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiquifyMode.entries.forEach { mode ->
                val isSelected = currentMode == mode
                Surface(
                    onClick = { onModeChange(mode) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = mode.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LrSliderRow(
            title = "Brush Radius",
            value = radius,
            displayValue = "${radius.toInt()} px",
            valueRange = 10f..150f,
            minLabel = "10 px",
            maxLabel = "150 px",
            onDragStart = {},
            onValueChange = onRadiusChange,
            onValueChangeFinished = {}
        )

        LrSliderRow(
            title = "Deformation Strength",
            value = strength * 100f,
            displayValue = "${(strength * 100).toInt()}%",
            valueRange = 10f..100f,
            minLabel = "10%",
            maxLabel = "100%",
            onDragStart = {},
            onValueChange = { onStrengthChange(it / 100f) },
            onValueChangeFinished = {}
        )

        Text(
            text = "Tap or drag across photo canvas to distort and sculpt pixels.",
            color = Color(0xFF888888),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun MotionDispersionSubPanel(
    dispersionAmount: Float,
    onDispersionChange: (Float) -> Unit,
    motionAmount: Float,
    motionAngle: Float,
    onMotionChange: (amount: Float, angle: Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Dispersion & Directional Motion Blur", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        LrSliderRow(
            title = "Dispersion Amount",
            value = dispersionAmount,
            displayValue = "${dispersionAmount.toInt()}",
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "100",
            onDragStart = {},
            onValueChange = onDispersionChange,
            onValueChangeFinished = {}
        )

        LrSliderRow(
            title = "Motion Blur Amount",
            value = motionAmount,
            displayValue = "${motionAmount.toInt()}",
            valueRange = 0f..100f,
            minLabel = "0",
            maxLabel = "100",
            onDragStart = {},
            onValueChange = { onMotionChange(it, motionAngle) },
            onValueChangeFinished = {}
        )

        LrSliderRow(
            title = "Motion Direction Angle",
            value = motionAngle,
            displayValue = "${motionAngle.toInt()}°",
            valueRange = 0f..360f,
            minLabel = "0°",
            maxLabel = "360°",
            onDragStart = {},
            onValueChange = { onMotionChange(motionAmount, it) },
            onValueChangeFinished = {}
        )
    }
}

@Composable
fun ResizeSubPanel(
    scaleFactor: Float,
    onScaleFactorChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Image Scaling & Resize", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        // Scale Factor quick presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                "25%" to 0.25f,
                "50%" to 0.50f,
                "75%" to 0.75f,
                "100%" to 1.0f,
                "150%" to 1.5f,
                "200%" to 2.0f
            )
            presets.forEach { (label, factor) ->
                val isSelected = kotlin.math.abs(scaleFactor - factor) < 0.05f
                Surface(
                    onClick = { onScaleFactorChange(factor) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LrSliderRow(
            title = "Continuous Scale",
            value = scaleFactor * 100f,
            displayValue = "${(scaleFactor * 100).toInt()}%",
            valueRange = 25f..250f,
            minLabel = "25%",
            maxLabel = "250%",
            onDragStart = {},
            onValueChange = { onScaleFactorChange(it / 100f) },
            onValueChangeFinished = {}
        )
    }
}
