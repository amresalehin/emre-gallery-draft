package com.amresalehin.emregallery.editor.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*

enum class TextSubtool(val label: String) {
    ALL("All Controls"),
    FONTS("Fonts & Style"),
    COLOR("Colors & Bg"),
    EFFECTS("Arc & Effects")
}

enum class ShapeSubtool(val label: String) {
    SHAPES("Shapes"),
    STYLE("Stroke & Fill"),
    EFFECTS("Radius & Shadow")
}

enum class CalloutSubtool(val label: String) {
    STYLES("Styles"),
    TEXT("Callout Text"),
    COLOR("Colors")
}

enum class FrameSubtool(val label: String) {
    FRAMES("Photo Frames"),
    BORDER("Custom Border")
}

private val DefaultAccentColor = Color(0xFF2979FF)

/**
 * Dedicated Text Domain Panel
 */
@Composable
fun TextPanel(
    sessionState: EditSessionState,
    activeSubtool: TextSubtool,
    viewModel: PhotoEditorViewModel,
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    var newTextValue by remember { mutableStateOf("Double tap to edit") }
    var selectedColor by remember { mutableStateOf(Color.White) }
    var selectedBgColor by remember { mutableStateOf(Color.Black.copy(alpha = 0.5f)) }

    val activeItem = sessionState.textOverlays.lastOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Quick Text Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.elevatedSurface)
                    .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = newTextValue,
                    onValueChange = { newTextValue = it },
                    textStyle = TextStyle(
                        color = colors.primaryText,
                        fontSize = 14.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(DefaultAccentColor),
                    modifier = Modifier.fillMaxWidth().testTag("text_input_field")
                )
            }

            Button(
                onClick = {
                    if (newTextValue.isNotBlank()) {
                        viewModel.addTextOverlay(newTextValue, selectedColor, selectedBgColor)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(42.dp).testTag("add_text_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Active text overlays chips (if any)
        if (sessionState.textOverlays.isNotEmpty()) {
            Text(
                "Active Overlays (${sessionState.textOverlays.size}):",
                color = colors.secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(sessionState.textOverlays) { item ->
                    val isSelected = item.id == activeItem?.id
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) DefaultAccentColor.copy(alpha = 0.2f) else colors.elevatedSurface,
                        border = BorderStroke(1.dp, if (isSelected) DefaultAccentColor else colors.border),
                        modifier = Modifier.clickable {
                            viewModel.updateTextOverlay(item)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.text.take(12),
                                color = colors.primaryText,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                            IconButton(
                                onClick = { viewModel.duplicateTextOverlay(item.id) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = colors.secondaryText, modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { viewModel.deleteTextOverlay(item.id) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        when (activeSubtool) {
            TextSubtool.ALL, TextSubtool.FONTS -> {
                // Font Family selector
                Text("Font Family", color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TextFontFamily.values()) { font ->
                        val isCurr = activeItem?.fontFamily == font
                        EmrePillButton(
                            label = font.label,
                            isSelected = isCurr,
                            onClick = {
                                activeItem?.let {
                                    viewModel.updateTextOverlay(it.copy(fontFamily = font))
                                }
                            },
                            colors = colors,
                            testTag = "font_${font.name.lowercase()}"
                        )
                    }
                }

                // Size slider
                LrSliderRow(
                    title = "Font Size",
                    value = activeItem?.fontSizeSp ?: 24f,
                    displayValue = "${(activeItem?.fontSizeSp ?: 24f).toInt()}sp",
                    valueRange = 12f..72f,
                    minLabel = "12sp",
                    maxLabel = "72sp",
                    onDragStart = {},
                    onValueChange = { size ->
                        activeItem?.let { viewModel.updateTextOverlay(it.copy(fontSizeSp = size)) }
                    },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )

                // Alignment & Bold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Alignment", color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextAlignment.values().forEach { align ->
                            val icon = when (align) {
                                TextAlignment.LEFT -> Icons.Default.FormatAlignLeft
                                TextAlignment.CENTER -> Icons.Default.FormatAlignCenter
                                TextAlignment.RIGHT -> Icons.Default.FormatAlignRight
                            }
                            IconButton(
                                onClick = {
                                    activeItem?.let { viewModel.updateTextOverlay(it.copy(alignment = align)) }
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (activeItem?.alignment == align) DefaultAccentColor.copy(alpha = 0.25f) else Color.Transparent)
                            ) {
                                Icon(icon, contentDescription = align.label, tint = colors.primaryText, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            TextSubtool.COLOR -> {
                // Text Color Palette
                Text("Text Color", color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val palette = listOf(
                    Color.White, Color.Black, Color(0xFFFF5252), Color(0xFFFF9800),
                    Color(0xFFFFEB3B), Color(0xFF4CAF50), Color(0xFF00E676), Color(0xFF00E5FF),
                    Color(0xFF2979FF), Color(0xFF651FFF), Color(0xFFE040FB), Color(0xFFFF4081)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(palette) { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    BorderStroke(
                                        2.dp,
                                        if (activeItem?.color == color) DefaultAccentColor else Color.White.copy(alpha = 0.2f)
                                    ),
                                    CircleShape
                                )
                                .clickable {
                                    selectedColor = color
                                    activeItem?.let { viewModel.updateTextOverlay(it.copy(color = color)) }
                                }
                        )
                    }
                }

                // Background Color
                Spacer(Modifier.height(4.dp))
                Text("Background Style", color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val bgOptions = listOf(
                    Color.Transparent to "None",
                    Color.Black.copy(alpha = 0.5f) to "Dark Tint",
                    Color.Black to "Black Solid",
                    Color.White.copy(alpha = 0.6f) to "Light Tint",
                    Color.White to "White Solid",
                    Color(0xFF1E88E5).copy(alpha = 0.6f) to "Blue Tint"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(bgOptions) { (bg, label) ->
                        EmrePillButton(
                            label = label,
                            isSelected = activeItem?.backgroundColor == bg,
                            onClick = {
                                selectedBgColor = bg
                                activeItem?.let { viewModel.updateTextOverlay(it.copy(backgroundColor = bg)) }
                            },
                            colors = colors,
                            testTag = "bg_${label.lowercase()}"
                        )
                    }
                }
            }

            TextSubtool.EFFECTS -> {
                // Curve arc slider
                LrSliderRow(
                    title = "Curved Text Arc",
                    value = activeItem?.curvedArcDegrees ?: 0f,
                    displayValue = "${(activeItem?.curvedArcDegrees ?: 0f).toInt()}°",
                    valueRange = -180f..180f,
                    minLabel = "-180°",
                    maxLabel = "+180°",
                    onDragStart = {},
                    onValueChange = { deg ->
                        activeItem?.let { viewModel.updateTextOverlay(it.copy(curvedArcDegrees = deg)) }
                    },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )

                // Warp styles
                Text("Text Warp Preset", color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TextWarpStyle.values()) { warp ->
                        EmrePillButton(
                            label = warp.label,
                            isSelected = activeItem?.warpStyle == warp,
                            onClick = {
                                activeItem?.let { viewModel.updateTextOverlay(it.copy(warpStyle = warp)) }
                            },
                            colors = colors,
                            testTag = "warp_${warp.name.lowercase()}"
                        )
                    }
                }

                // Stroke width
                LrSliderRow(
                    title = "Stroke Outline",
                    value = activeItem?.strokeWidth ?: 0f,
                    displayValue = "${(activeItem?.strokeWidth ?: 0f).toInt()}px",
                    valueRange = 0f..12f,
                    minLabel = "0",
                    maxLabel = "12",
                    onDragStart = {},
                    onValueChange = { sw ->
                        activeItem?.let { viewModel.updateTextOverlay(it.copy(strokeWidth = sw)) }
                    },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )
            }
        }
    }
}

/**
 * Dedicated Shape Domain Panel
 */
@Composable
fun ShapePanel(
    sessionState: EditSessionState,
    activeSubtool: ShapeSubtool,
    viewModel: PhotoEditorViewModel,
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    val activeShape = sessionState.shapes.lastOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Shapes Quick Grid
        Text(
            "Add Vector Shape:",
            color = colors.secondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ShapeType.values()) { shapeType ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.elevatedSurface,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.clickable {
                        viewModel.addShape(shapeType, Color.White, Color.Transparent)
                    }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        val icon = when (shapeType) {
                            ShapeType.RECTANGLE -> Icons.Default.CropSquare
                            ShapeType.ROUNDED_RECTANGLE -> Icons.Default.RoundedCorner
                            ShapeType.CIRCLE -> Icons.Default.Circle
                            ShapeType.STAR -> Icons.Default.Star
                            ShapeType.HEART -> Icons.Default.Favorite
                            ShapeType.HEXAGON -> Icons.Default.Pentagon
                            ShapeType.ARROW -> Icons.Default.ArrowForward
                            ShapeType.TRIANGLE -> Icons.Default.ChangeHistory
                            ShapeType.POLYGON -> Icons.Default.Polyline
                            ShapeType.LINE -> Icons.Default.HorizontalRule
                            ShapeType.CURVED_LINE -> Icons.Default.Gesture
                            ShapeType.FREEFORM -> Icons.Default.Draw
                        }
                        Icon(icon, contentDescription = shapeType.label, tint = DefaultAccentColor, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(shapeType.label, color = colors.primaryText, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        // Active shapes manager
        if (sessionState.shapes.isNotEmpty()) {
            Text(
                "Active Shapes (${sessionState.shapes.size}):",
                color = colors.secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessionState.shapes) { shape ->
                    val isSelected = shape.id == activeShape?.id
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) DefaultAccentColor.copy(alpha = 0.2f) else colors.elevatedSurface,
                        border = BorderStroke(1.dp, if (isSelected) DefaultAccentColor else colors.border),
                        modifier = Modifier.clickable { viewModel.updateShape(shape) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(shape.shapeType.label, color = colors.primaryText, fontSize = 12.sp)
                            IconButton(
                                onClick = { viewModel.duplicateShape(shape.id) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = colors.secondaryText, modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { viewModel.deleteShape(shape.id) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        when (activeSubtool) {
            ShapeSubtool.SHAPES, ShapeSubtool.STYLE -> {
                // Stroke width slider
                LrSliderRow(
                    title = "Stroke Width",
                    value = activeShape?.strokeWidth ?: 4f,
                    displayValue = "${(activeShape?.strokeWidth ?: 4f).toInt()}px",
                    valueRange = 1f..24f,
                    minLabel = "1px",
                    maxLabel = "24px",
                    onDragStart = {},
                    onValueChange = { w ->
                        activeShape?.let { viewModel.updateShape(it.copy(strokeWidth = w)) }
                    },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )

                // Stroke color row
                Text("Stroke Color", color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val strokeColors = listOf(
                    Color.White, Color.Black, Color(0xFFFF5252), Color(0xFFFFD600),
                    Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFFE040FB)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(strokeColors) { col ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    BorderStroke(
                                        2.dp,
                                        if (activeShape?.strokeColor == col) DefaultAccentColor else Color.White.copy(alpha = 0.2f)
                                    ),
                                    CircleShape
                                )
                                .clickable {
                                    activeShape?.let { viewModel.updateShape(it.copy(strokeColor = col)) }
                                }
                        )
                    }
                }

                // Fill color row
                Spacer(Modifier.height(4.dp))
                Text("Fill Color", color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val fillOptions = listOf(
                    Color.Transparent, Color.White.copy(alpha = 0.3f), Color.White,
                    Color.Black.copy(alpha = 0.4f), Color(0xFFFF5252).copy(alpha = 0.4f),
                    Color(0xFF2979FF).copy(alpha = 0.4f), Color(0xFF00E676).copy(alpha = 0.4f)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(fillOptions) { fill ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (fill == Color.Transparent) colors.elevatedSurface else fill)
                                .border(
                                    BorderStroke(
                                        1.5.dp,
                                        if (activeShape?.fillColor == fill) DefaultAccentColor else colors.border
                                    ),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    activeShape?.let { viewModel.updateShape(it.copy(fillColor = fill)) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (fill == Color.Transparent) {
                                Text("∅", color = colors.secondaryText, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            ShapeSubtool.EFFECTS -> {
                // Corner radius
                LrSliderRow(
                    title = "Corner Radius",
                    value = activeShape?.cornerRadius ?: 16f,
                    displayValue = "${(activeShape?.cornerRadius ?: 16f).toInt()}px",
                    valueRange = 0f..60f,
                    minLabel = "0",
                    maxLabel = "60",
                    onDragStart = {},
                    onValueChange = { rad ->
                        activeShape?.let { viewModel.updateShape(it.copy(cornerRadius = rad)) }
                    },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )

                // Rotation
                LrSliderRow(
                    title = "Rotation Angle",
                    value = activeShape?.rotation ?: 0f,
                    displayValue = "${(activeShape?.rotation ?: 0f).toInt()}°",
                    valueRange = 0f..360f,
                    minLabel = "0°",
                    maxLabel = "360°",
                    onDragStart = {},
                    onValueChange = { rot ->
                        activeShape?.let { viewModel.updateShape(it.copy(rotation = rot)) }
                    },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )
            }
        }
    }
}

/**
 * Dedicated Callout Domain Panel
 */
@Composable
fun CalloutPanel(
    sessionState: EditSessionState,
    activeSubtool: CalloutSubtool,
    viewModel: PhotoEditorViewModel,
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    var calloutText by remember { mutableStateOf("Hey there!") }
    var selectedStyle by remember { mutableStateOf(CalloutStyle.SPEECH_BUBBLE) }
    val activeCallout = sessionState.callouts.lastOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Text Input & Add Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.elevatedSurface)
                    .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = calloutText,
                    onValueChange = { calloutText = it },
                    textStyle = TextStyle(
                        color = colors.primaryText,
                        fontSize = 14.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(DefaultAccentColor),
                    modifier = Modifier.fillMaxWidth().testTag("callout_input_field")
                )
            }

            Button(
                onClick = {
                    if (calloutText.isNotBlank()) {
                        viewModel.addCalloutWithStyle(calloutText, selectedStyle)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DefaultAccentColor),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(42.dp).testTag("add_callout_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Active callouts
        if (sessionState.callouts.isNotEmpty()) {
            Text(
                "Active Callouts (${sessionState.callouts.size}):",
                color = colors.secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessionState.callouts) { item ->
                    val isSelected = item.id == activeCallout?.id
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) DefaultAccentColor.copy(alpha = 0.2f) else colors.elevatedSurface,
                        border = BorderStroke(1.dp, if (isSelected) DefaultAccentColor else colors.border),
                        modifier = Modifier.clickable { viewModel.updateCallout(item) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(item.text.take(12), color = colors.primaryText, fontSize = 12.sp)
                            IconButton(
                                onClick = { viewModel.deleteCallout(item.id) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        when (activeSubtool) {
            CalloutSubtool.STYLES -> {
                Text("Callout Bubble Style", color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CalloutStyle.values()) { style ->
                        val isCurr = selectedStyle == style || activeCallout?.style == style
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurr) DefaultAccentColor.copy(alpha = 0.25f) else colors.elevatedSurface,
                            border = BorderStroke(1.dp, if (isCurr) DefaultAccentColor else colors.border),
                            modifier = Modifier.clickable {
                                selectedStyle = style
                                activeCallout?.let {
                                    viewModel.updateCallout(it.copy(style = style))
                                }
                            }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    contentDescription = style.label,
                                    tint = if (isCurr) DefaultAccentColor else colors.secondaryText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(style.label, color = colors.primaryText, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            CalloutSubtool.TEXT -> {
                Text("Edit Active Callout Text", color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.elevatedSurface)
                        .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = activeCallout?.text ?: calloutText,
                        onValueChange = { newTxt ->
                            activeCallout?.let { viewModel.updateCallout(it.copy(text = newTxt)) }
                        },
                        textStyle = TextStyle(color = colors.primaryText, fontSize = 14.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(DefaultAccentColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Numbered Callout badge counter
                if (activeCallout?.style == CalloutStyle.NUMBERED_CALLOUT) {
                    Spacer(Modifier.height(6.dp))
                    Text("Badge Number: ${activeCallout.badgeNumber}", color = colors.secondaryText, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..8).forEach { num ->
                            EmrePillButton(
                                label = "$num",
                                isSelected = activeCallout.badgeNumber == num,
                                onClick = {
                                    viewModel.updateCallout(activeCallout.copy(badgeNumber = num))
                                },
                                colors = colors,
                                testTag = "badge_num_$num"
                            )
                        }
                    }
                }
            }

            CalloutSubtool.COLOR -> {
                Text("Bubble Fill Color", color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val bubbleBgColors = listOf(
                    Color.White, Color.Black, Color(0xFFFFF9C4), Color(0xFFE1F5FE),
                    Color(0xFFE8F5E9), Color(0xFFFCE4EC), Color(0xFFFFF3E0), Color(0xFFEDE7F6)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bubbleBgColors) { bg ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .border(
                                    BorderStroke(
                                        2.dp,
                                        if (activeCallout?.backgroundColor == bg) DefaultAccentColor else Color.White.copy(alpha = 0.2f)
                                    ),
                                    CircleShape
                                )
                                .clickable {
                                    activeCallout?.let { viewModel.updateCallout(it.copy(backgroundColor = bg)) }
                                }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Text Color", color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val textColors = listOf(Color.Black, Color.White, Color(0xFFD32F2F), Color(0xFF1976D2))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    textColors.forEach { col ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    BorderStroke(
                                        2.dp,
                                        if (activeCallout?.textColor == col) DefaultAccentColor else colors.border
                                    ),
                                    CircleShape
                                )
                                .clickable {
                                    activeCallout?.let { viewModel.updateCallout(it.copy(textColor = col)) }
                                }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Frame Domain Panel
 */
@Composable
fun FramePanel(
    sessionState: EditSessionState,
    activeSubtool: FrameSubtool,
    viewModel: PhotoEditorViewModel,
    onFrameChange: (FrameStyle) -> Unit,
    onBorderChange: (BorderSettings) -> Unit,
    modifier: Modifier = Modifier,
    colors: EmreEditorColors = LocalEmreEditorColors.current
) {
    val currentFrame = sessionState.frame
    val currentBorder = sessionState.border

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (activeSubtool) {
            FrameSubtool.FRAMES -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Photo Frame Styles",
                        color = colors.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (currentFrame != FrameStyle.NONE) {
                        TextButton(
                            onClick = { onFrameChange(FrameStyle.NONE) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Clear Frame", color = Color(0xFFFF5252), fontSize = 12.sp)
                        }
                    }
                }

                // Grid of all Frame Styles
                val styleRows = FrameStyle.values().toList().chunked(3)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    styleRows.forEach { rowStyles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowStyles.forEach { style ->
                                val isSelected = currentFrame == style
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) DefaultAccentColor.copy(alpha = 0.2f) else colors.elevatedSurface,
                                    border = BorderStroke(1.dp, if (isSelected) DefaultAccentColor else colors.border),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onFrameChange(style) }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (style) {
                                                FrameStyle.NONE -> Icons.Default.Block
                                                FrameStyle.POLAROID, FrameStyle.INSTAX_MINI, FrameStyle.SX70_VINTAGE -> Icons.Default.CropPortrait
                                                FrameStyle.FILM_STRIP -> Icons.Default.Movie
                                                FrameStyle.CAMERA_VIEWFINDER -> Icons.Default.CameraAlt
                                                FrameStyle.GRADIENT_BORDER -> Icons.Default.Gradient
                                                FrameStyle.SHADOW_FLOAT -> Icons.Default.Layers
                                                else -> Icons.Default.CropFree
                                            },
                                            contentDescription = style.label,
                                            tint = if (isSelected) DefaultAccentColor else colors.secondaryText,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = style.label,
                                            color = colors.primaryText,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                            // Fill remaining spaces in chunked row
                            val remaining = 3 - rowStyles.size
                            if (remaining > 0) {
                                repeat(remaining) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            FrameSubtool.BORDER -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Custom Border Parameters",
                        color = colors.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (currentBorder.widthRatio > 0f) {
                        TextButton(
                            onClick = { onBorderChange(BorderSettings()) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Reset Border", color = Color(0xFFFF5252), fontSize = 12.sp)
                        }
                    }
                }

                // Border Width
                LrSliderRow(
                    title = "Border Width",
                    value = currentBorder.widthRatio * 100f,
                    displayValue = "${(currentBorder.widthRatio * 100f).toInt()}%",
                    valueRange = 0f..15f,
                    minLabel = "0%",
                    maxLabel = "15%",
                    onDragStart = {},
                    onValueChange = { pct ->
                        onBorderChange(currentBorder.copy(widthRatio = pct / 100f))
                    },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )

                // Corner Radius
                LrSliderRow(
                    title = "Corner Radius",
                    value = currentBorder.cornerRadiusRatio * 100f,
                    displayValue = "${(currentBorder.cornerRadiusRatio * 100f).toInt()}%",
                    valueRange = 0f..50f,
                    minLabel = "0%",
                    maxLabel = "50%",
                    onDragStart = {},
                    onValueChange = { pct ->
                        onBorderChange(currentBorder.copy(cornerRadiusRatio = pct / 100f))
                    },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )

                // Padding
                LrSliderRow(
                    title = "Outer Padding",
                    value = currentBorder.paddingRatio * 100f,
                    displayValue = "${(currentBorder.paddingRatio * 100f).toInt()}%",
                    valueRange = 0f..10f,
                    minLabel = "0%",
                    maxLabel = "10%",
                    onDragStart = {},
                    onValueChange = { pct ->
                        onBorderChange(currentBorder.copy(paddingRatio = pct / 100f))
                    },
                    onValueChangeFinished = { viewModel.commitSliderChange() }
                )

                // Inner Shadow switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Inner Shadow", color = colors.secondaryText, fontSize = 13.sp)
                    Switch(
                        checked = currentBorder.innerShadow,
                        onCheckedChange = { onBorderChange(currentBorder.copy(innerShadow = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = DefaultAccentColor)
                    )
                }

                // Border Color
                Text("Border Color", color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val borderColors = listOf(
                    Color.White, Color(0xFFF5F5DC), Color.Black, Color(0xFFFFD700),
                    Color(0xFFC0C0C0), Color(0xFF212121), Color(0xFFE0E0E0), Color(0xFFFFCDD2)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(borderColors) { col ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    BorderStroke(
                                        2.dp,
                                        if (currentBorder.color == col) DefaultAccentColor else Color.White.copy(alpha = 0.2f)
                                    ),
                                    CircleShape
                                )
                                .clickable {
                                    onBorderChange(currentBorder.copy(color = col))
                                }
                        )
                    }
                }
            }
        }
    }
}

