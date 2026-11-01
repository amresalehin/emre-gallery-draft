package com.amresalehin.emregallery.editor.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*

enum class OverlaySubWorkspace(val label: String) {
    FRAMES_BORDERS("Frames & Borders"),
    TEXT("Text"),
    STICKERS("Stickers"),
    SHAPES("Shapes"),
    CALLOUTS("Callouts"),
    LIGHTING("Lighting"),
    BACKGROUND("Background")
}

/**
 * Overlays Primary Category Workspace with 7 sub-workspaces:
 * Frames & Borders, Text, Stickers, Shapes, Callouts, Lighting, and Background Cutout.
 */
@Composable
fun OverlaysWorkspacePanel(
    sessionState: EditSessionState,
    onFrameChange: (FrameStyle) -> Unit,
    onBorderChange: (BorderSettings) -> Unit,
    onAddText: (String, Color, Color) -> Unit,
    onUpdateText: (TextOverlayItem) -> Unit,
    onDeleteText: (String) -> Unit,
    onDuplicateText: (String) -> Unit,
    onAddSticker: (String) -> Unit,
    onUpdateSticker: (StickerItem) -> Unit,
    onDeleteSticker: (String) -> Unit,
    onDuplicateSticker: (String) -> Unit,
    onAddShape: (ShapeType) -> Unit,
    onUpdateShape: (ShapeItem) -> Unit,
    onDeleteShape: (String) -> Unit,
    onDuplicateShape: (String) -> Unit,
    onAddCallout: (String, CalloutStyle) -> Unit,
    onUpdateCallout: (CalloutItem) -> Unit,
    onDeleteCallout: (String) -> Unit,
    onAddLensFlare: (LensFlareType) -> Unit,
    onUpdateLensFlare: (LensFlareItem) -> Unit,
    onDeleteLensFlare: (String) -> Unit,
    onBackgroundChange: (BackgroundSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubWorkspace by remember { mutableStateOf(OverlaySubWorkspace.FRAMES_BORDERS) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // -------------------------------------------------------------
        // TOP / CONTROLS & TIER 3 (Above Tier 2): Subpanel content
        // -------------------------------------------------------------
        when (activeSubWorkspace) {
            OverlaySubWorkspace.FRAMES_BORDERS -> {
                FramesBordersSubPanel(
                    currentFrame = sessionState.frame,
                    borderSettings = sessionState.border,
                    onFrameChange = onFrameChange,
                    onBorderChange = onBorderChange
                )
            }
            OverlaySubWorkspace.TEXT -> {
                TextOverlaySubPanel(
                    textOverlays = sessionState.textOverlays,
                    onAddText = onAddText,
                    onUpdateText = onUpdateText,
                    onDeleteText = onDeleteText,
                    onDuplicateText = onDuplicateText
                )
            }
            OverlaySubWorkspace.STICKERS -> {
                StickersSubPanel(
                    stickers = sessionState.stickers,
                    onAddSticker = onAddSticker,
                    onUpdateSticker = onUpdateSticker,
                    onDeleteSticker = onDeleteSticker,
                    onDuplicateSticker = onDuplicateSticker
                )
            }
            OverlaySubWorkspace.SHAPES -> {
                ShapesSubPanel(
                    shapes = sessionState.shapes,
                    onAddShape = onAddShape,
                    onUpdateShape = onUpdateShape,
                    onDeleteShape = onDeleteShape,
                    onDuplicateShape = onDuplicateShape
                )
            }
            OverlaySubWorkspace.CALLOUTS -> {
                CalloutsSubPanel(
                    callouts = sessionState.callouts,
                    onAddCallout = onAddCallout,
                    onUpdateCallout = onUpdateCallout,
                    onDeleteCallout = onDeleteCallout
                )
            }
            OverlaySubWorkspace.LIGHTING -> {
                LightingSubPanel(
                    flares = sessionState.lensFlares,
                    onAddLensFlare = onAddLensFlare,
                    onUpdateLensFlare = onUpdateLensFlare,
                    onDeleteLensFlare = onDeleteLensFlare
                )
            }
            OverlaySubWorkspace.BACKGROUND -> {
                BackgroundSubPanel(
                    settings = sessionState.background,
                    onSettingsChange = onBackgroundChange
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // -------------------------------------------------------------
        // TIER 2 (Just above bottom bar): Subcategory Pills Navigation
        // Frames & Borders, Text, Stickers, Shapes, Callouts, Lighting, Background
        // -------------------------------------------------------------
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(OverlaySubWorkspace.entries) { sub ->
                val isSelected = activeSubWorkspace == sub
                val icon = when (sub) {
                    OverlaySubWorkspace.FRAMES_BORDERS -> Icons.Default.CropPortrait
                    OverlaySubWorkspace.TEXT -> Icons.Default.TextFields
                    OverlaySubWorkspace.STICKERS -> Icons.Default.EmojiEmotions
                    OverlaySubWorkspace.SHAPES -> Icons.Default.Category
                    OverlaySubWorkspace.CALLOUTS -> Icons.Default.ChatBubbleOutline
                    OverlaySubWorkspace.LIGHTING -> Icons.Default.Flare
                    OverlaySubWorkspace.BACKGROUND -> Icons.Default.Wallpaper
                }

                Surface(
                    onClick = { activeSubWorkspace = sub },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = sub.label,
                            tint = if (isSelected) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = sub.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 1. Frames & Borders
// -------------------------------------------------------------
@Composable
fun FramesBordersSubPanel(
    currentFrame: FrameStyle,
    borderSettings: BorderSettings,
    onFrameChange: (FrameStyle) -> Unit,
    onBorderChange: (BorderSettings) -> Unit
) {
    var subTab by remember { mutableStateOf("Frames") }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (subTab == "Frames") {
            Text("Artistic Frames (${FrameStyle.entries.size} styles)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(FrameStyle.entries) { frame ->
                    val isSelected = currentFrame == frame
                    Surface(
                        onClick = { onFrameChange(frame) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color.White else Color(0xFF1E1E1E),
                        border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.width(96.dp).height(64.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (frame == FrameStyle.NONE) Icons.Default.Close else Icons.Default.CropPortrait,
                                contentDescription = frame.label,
                                tint = if (isSelected) Color.Black else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = frame.label,
                                color = if (isSelected) Color.Black else Color(0xFFAAAAAA),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        } else {
            Text("Custom Border Inset", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))

            LrSliderRow(
                title = "Border Width",
                value = borderSettings.widthRatio * 100f,
                displayValue = "${(borderSettings.widthRatio * 100).toInt()}%",
                valueRange = 0f..15f,
                minLabel = "0%",
                maxLabel = "15%",
                onDragStart = {},
                onValueChange = { onBorderChange(borderSettings.copy(widthRatio = it / 100f)) },
                onValueChangeFinished = {}
            )

            LrSliderRow(
                title = "Corner Radius",
                value = borderSettings.cornerRadiusRatio * 100f,
                displayValue = "${(borderSettings.cornerRadiusRatio * 100).toInt()}%",
                valueRange = 0f..50f,
                minLabel = "0%",
                maxLabel = "50%",
                onDragStart = {},
                onValueChange = { onBorderChange(borderSettings.copy(cornerRadiusRatio = it / 100f)) },
                onValueChangeFinished = {}
            )

            LrSliderRow(
                title = "Padding Inset",
                value = borderSettings.paddingRatio * 100f,
                displayValue = "${(borderSettings.paddingRatio * 100).toInt()}%",
                valueRange = 0f..20f,
                minLabel = "0%",
                maxLabel = "20%",
                onDragStart = {},
                onValueChange = { onBorderChange(borderSettings.copy(paddingRatio = it / 100f)) },
                onValueChangeFinished = {}
            )

            Spacer(Modifier.height(8.dp))

            // Border Color Swatches
            Text("Border Color", color = Color(0xFFAAAAAA), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val borderColors = listOf(Color.White, Color.Black, Color(0xFFFFD54F), Color(0xFFFF5252), Color(0xFF2979FF), Color(0xFF00E676))
                items(borderColors) { c ->
                    val isSel = borderSettings.color == c
                    Surface(
                        onClick = { onBorderChange(borderSettings.copy(color = c)) },
                        shape = CircleShape,
                        color = c,
                        border = BorderStroke(if (isSel) 2.5.dp else 1.dp, Color.White),
                        modifier = Modifier.size(32.dp)
                    ) {}
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Tier 3: Sub-tab switcher pills (Frames vs Border Stroke) docked above Tier 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Frames", "Border Stroke").forEach { tab ->
                val isSel = subTab == tab
                Surface(
                    onClick = { subTab = tab },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSel) Color.White else Color(0xFF222222),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tab,
                            color = if (isSel) Color.Black else Color.White,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. Text Overlays
// -------------------------------------------------------------
@Composable
fun TextOverlaySubPanel(
    textOverlays: List<TextOverlayItem>,
    onAddText: (String, Color, Color) -> Unit,
    onUpdateText: (TextOverlayItem) -> Unit,
    onDeleteText: (String) -> Unit,
    onDuplicateText: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTextId by remember { mutableStateOf(textOverlays.firstOrNull()?.id) }
    val activeItem = textOverlays.firstOrNull { it.id == selectedTextId } ?: textOverlays.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Text Layers (${textOverlays.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Button(
                onClick = { showAddDialog = true },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Text", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (textOverlays.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            // Text Layer Selector Tabs
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(textOverlays) { item ->
                    val isSel = item.id == activeItem?.id
                    Surface(
                        onClick = { selectedTextId = item.id },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSel) Color.White else Color(0xFF222222),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = item.text.take(12),
                                color = if (isSel) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            if (activeItem != null) {
                Spacer(Modifier.height(10.dp))
                // Active Text Inspector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onDuplicateText(activeItem.id) },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).height(34.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Duplicate", color = Color.White, fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { onDeleteText(activeItem.id) },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).height(34.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = Color(0xFFFF5252), fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Font Family Row
                Text("Font Style", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TextFontFamily.entries) { font ->
                        val isSel = activeItem.fontFamily == font
                        Surface(
                            onClick = { onUpdateText(activeItem.copy(fontFamily = font)) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) Color.White else Color(0xFF222222),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                Text(font.label, color = if (isSel) Color.Black else Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Font Size Slider
                LrSliderRow(
                    title = "Font Size",
                    value = activeItem.fontSizeSp,
                    displayValue = "${activeItem.fontSizeSp.toInt()} sp",
                    valueRange = 12f..72f,
                    minLabel = "12",
                    maxLabel = "72",
                    onDragStart = {},
                    onValueChange = { onUpdateText(activeItem.copy(fontSizeSp = it)) },
                    onValueChangeFinished = {}
                )

                // Rotation Slider
                LrSliderRow(
                    title = "Rotation",
                    value = activeItem.rotation,
                    displayValue = "${activeItem.rotation.toInt()}°",
                    valueRange = -180f..180f,
                    minLabel = "−180°",
                    maxLabel = "+180°",
                    onDragStart = {},
                    onValueChange = { onUpdateText(activeItem.copy(rotation = it)) },
                    onValueChangeFinished = {}
                )

                // Curved Arc Slider
                LrSliderRow(
                    title = "Curved Arc",
                    value = activeItem.curvedArcDegrees,
                    displayValue = "${activeItem.curvedArcDegrees.toInt()}°",
                    valueRange = -180f..180f,
                    minLabel = "−180°",
                    maxLabel = "+180°",
                    onDragStart = {},
                    onValueChange = { onUpdateText(activeItem.copy(curvedArcDegrees = it)) },
                    onValueChangeFinished = {}
                )
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Text("No text layers added yet. Tap '+ Add Text' above.", color = Color(0xFF888888), fontSize = 12.sp)
        }
    }

    if (showAddDialog) {
        var inputStr by remember { mutableStateOf("Emre Gallery") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Text Layer", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = inputStr,
                    onValueChange = { inputStr = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (inputStr.isNotBlank()) {
                            onAddText(inputStr, Color.White, Color.Black.copy(alpha = 0.5f))
                        }
                        showAddDialog = false
                    }
                ) {
                    Text("Add", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Color(0xFFAAAAAA))
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// -------------------------------------------------------------
// 3. Stickers
// -------------------------------------------------------------
@Composable
fun StickersSubPanel(
    stickers: List<StickerItem>,
    onAddSticker: (String) -> Unit,
    onUpdateSticker: (StickerItem) -> Unit,
    onDeleteSticker: (String) -> Unit,
    onDuplicateSticker: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Smileys") }
    var selectedStickerId by remember { mutableStateOf(stickers.firstOrNull()?.id) }
    val activeSticker = stickers.firstOrNull { it.id == selectedStickerId } ?: stickers.firstOrNull()

    val emojiCategories = mapOf(
        "Smileys" to listOf("😊", "😎", "😍", "🥳", "🔥", "✨", "❤️", "💖", "🎉", "💯", "👑", "⭐"),
        "Animals" to listOf("🐱", "🐶", "🦊", "🐼", "🦁", "🐯", "🐨", "🦋", "🦄", "🐧", "🦉", "🐰"),
        "Food" to listOf("🍕", "🍔", "🍦", "☕", "🍩", "🍣", "🌮", "🍿", "🍓", "🍉", "🍰", "🍹"),
        "Travel" to listOf("✈️", "🚗", "🏖️", "🚀", "⛺", "⛰️", "🗼", "🎡", "🚲", "⛵", "🌅", "🗽"),
        "Objects" to listOf("💡", "📸", "💎", "🎸", "🎧", "🕶️", "⌚", "🎁", "🎨", "🏆", "🎮", "🔑")
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Add Sticker / Emoji", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        // Category Tabs
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(emojiCategories.keys.toList()) { cat ->
                val isSel = selectedCategory == cat
                Surface(
                    onClick = { selectedCategory = cat },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSel) Color.White else Color(0xFF222222),
                    modifier = Modifier.height(30.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = cat,
                            color = if (isSel) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Emoji Grid / Strip
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val emojis = emojiCategories[selectedCategory] ?: emptyList()
            items(emojis) { emoji ->
                Surface(
                    onClick = { onAddSticker(emoji) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 22.sp)
                    }
                }
            }
        }

        if (activeSticker != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Selected: ${activeSticker.emojiOrRes}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { onDuplicateSticker(activeSticker.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Duplicate", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { onDeleteSticker(activeSticker.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                    }
                }
            }

            LrSliderRow(
                title = "Size",
                value = activeSticker.sizeDp,
                displayValue = "${activeSticker.sizeDp.toInt()} dp",
                valueRange = 32f..200f,
                minLabel = "32",
                maxLabel = "200",
                onDragStart = {},
                onValueChange = { onUpdateSticker(activeSticker.copy(sizeDp = it)) },
                onValueChangeFinished = {}
            )

            LrSliderRow(
                title = "Rotation",
                value = activeSticker.rotation,
                displayValue = "${activeSticker.rotation.toInt()}°",
                valueRange = -180f..180f,
                minLabel = "−180°",
                maxLabel = "+180°",
                onDragStart = {},
                onValueChange = { onUpdateSticker(activeSticker.copy(rotation = it)) },
                onValueChangeFinished = {}
            )
        }
    }
}

// -------------------------------------------------------------
// 4. Shapes
// -------------------------------------------------------------
@Composable
fun ShapesSubPanel(
    shapes: List<ShapeItem>,
    onAddShape: (ShapeType) -> Unit,
    onUpdateShape: (ShapeItem) -> Unit,
    onDeleteShape: (String) -> Unit,
    onDuplicateShape: (String) -> Unit
) {
    var selectedShapeId by remember { mutableStateOf(shapes.firstOrNull()?.id) }
    val activeShape = shapes.firstOrNull { it.id == selectedShapeId } ?: shapes.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Add Vector Shape", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        // Shape Icons Strip
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ShapeType.entries) { shape ->
                val icon = when (shape) {
                    ShapeType.RECTANGLE -> Icons.Default.CropSquare
                    ShapeType.ROUNDED_RECTANGLE -> Icons.Default.Square
                    ShapeType.CIRCLE -> Icons.Default.Circle
                    ShapeType.STAR -> Icons.Default.Star
                    ShapeType.HEART -> Icons.Default.Favorite
                    ShapeType.HEXAGON -> Icons.Default.Hexagon
                    ShapeType.ARROW -> Icons.Default.ArrowForward
                    ShapeType.TRIANGLE -> Icons.Default.ChangeHistory
                    ShapeType.POLYGON -> Icons.Default.Polyline
                    ShapeType.LINE -> Icons.Default.HorizontalRule
                    ShapeType.CURVED_LINE -> Icons.Default.Gesture
                    ShapeType.FREEFORM -> Icons.Default.Draw
                }

                Surface(
                    onClick = { onAddShape(shape) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(64.dp).height(50.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(icon, shape.label, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(shape.label, color = Color(0xFFAAAAAA), fontSize = 8.sp, maxLines = 1)
                    }
                }
            }
        }

        if (activeShape != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Selected: ${activeShape.shapeType.label}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { onDuplicateShape(activeShape.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Duplicate", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { onDeleteShape(activeShape.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                    }
                }
            }

            LrSliderRow(
                title = "Stroke Width",
                value = activeShape.strokeWidth,
                displayValue = "${activeShape.strokeWidth.toInt()} px",
                valueRange = 1f..20f,
                minLabel = "1",
                maxLabel = "20",
                onDragStart = {},
                onValueChange = { onUpdateShape(activeShape.copy(strokeWidth = it)) },
                onValueChangeFinished = {}
            )

            LrSliderRow(
                title = "Rotation",
                value = activeShape.rotation,
                displayValue = "${activeShape.rotation.toInt()}°",
                valueRange = -180f..180f,
                minLabel = "−180°",
                maxLabel = "+180°",
                onDragStart = {},
                onValueChange = { onUpdateShape(activeShape.copy(rotation = it)) },
                onValueChangeFinished = {}
            )
        }
    }
}

// -------------------------------------------------------------
// 5. Callouts
// -------------------------------------------------------------
@Composable
fun CalloutsSubPanel(
    callouts: List<CalloutItem>,
    onAddCallout: (String, CalloutStyle) -> Unit,
    onUpdateCallout: (CalloutItem) -> Unit,
    onDeleteCallout: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedStyle by remember { mutableStateOf(CalloutStyle.SPEECH_BUBBLE) }
    var selectedCalloutId by remember { mutableStateOf(callouts.firstOrNull()?.id) }
    val activeCallout = callouts.firstOrNull { it.id == selectedCalloutId } ?: callouts.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Callout Balloons (${callouts.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Button(
                onClick = { showAddDialog = true },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Callout", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CalloutStyle.entries) { style ->
                val isSel = selectedStyle == style
                Surface(
                    onClick = { selectedStyle = style },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSel) Color.White else Color(0xFF222222),
                    modifier = Modifier.height(30.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = style.label,
                            color = if (isSel) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        if (activeCallout != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Selected: \"${activeCallout.text}\"", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                IconButton(onClick = { onDeleteCallout(activeCallout.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        var calloutText by remember { mutableStateOf("Say something...") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add ${selectedStyle.label}", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = calloutText,
                    onValueChange = { calloutText = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (calloutText.isNotBlank()) {
                            onAddCallout(calloutText, selectedStyle)
                        }
                        showAddDialog = false
                    }
                ) {
                    Text("Add", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Color(0xFFAAAAAA))
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// -------------------------------------------------------------
// 6. Lighting (Lens Flares)
// -------------------------------------------------------------
@Composable
fun LightingSubPanel(
    flares: List<LensFlareItem>,
    onAddLensFlare: (LensFlareType) -> Unit,
    onUpdateLensFlare: (LensFlareItem) -> Unit,
    onDeleteLensFlare: (String) -> Unit
) {
    var selectedFlareId by remember { mutableStateOf(flares.firstOrNull()?.id) }
    val activeFlare = flares.firstOrNull { it.id == selectedFlareId } ?: flares.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Optical Lens Flares (${LensFlareType.entries.size} types)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LensFlareType.entries) { type ->
                Surface(
                    onClick = { onAddLensFlare(type) },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Flare, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(type.label, color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        if (activeFlare != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Selected: ${activeFlare.flareType.label}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                IconButton(onClick = { onDeleteLensFlare(activeFlare.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                }
            }

            LrSliderRow(
                title = "Intensity",
                value = activeFlare.intensity * 100f,
                displayValue = "${(activeFlare.intensity * 100).toInt()}%",
                valueRange = 10f..150f,
                minLabel = "10%",
                maxLabel = "150%",
                onDragStart = {},
                onValueChange = { onUpdateLensFlare(activeFlare.copy(intensity = it / 100f)) },
                onValueChangeFinished = {}
            )
        }
    }
}

// -------------------------------------------------------------
// 7. Background Cutout & Replacement
// -------------------------------------------------------------
@Composable
fun BackgroundSubPanel(
    settings: BackgroundSettings,
    onSettingsChange: (BackgroundSettings) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Subject Isolation & Background", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        // Background Mode Pills
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BackgroundMode.entries) { mode ->
                val isSel = settings.mode == mode
                Surface(
                    onClick = { onSettingsChange(settings.copy(mode = mode, isCutoutActive = mode != BackgroundMode.ORIGINAL)) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSel) Color.White else Color(0xFF222222),
                    modifier = Modifier.height(34.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = mode.label,
                            color = if (isSel) Color.Black else Color.White,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (settings.mode) {
            BackgroundMode.BLUR -> {
                LrSliderRow(
                    title = "Portrait Blur Radius",
                    value = settings.blurRadius,
                    displayValue = "${settings.blurRadius.toInt()} px",
                    valueRange = 5f..60f,
                    minLabel = "5 px",
                    maxLabel = "60 px",
                    onDragStart = {},
                    onValueChange = { onSettingsChange(settings.copy(blurRadius = it)) },
                    onValueChangeFinished = {}
                )
            }
            BackgroundMode.SOLID -> {
                Text("Studio Solid Color", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val solidColors = listOf(Color.White, Color.Black, Color(0xFF212121), Color(0xFFEEEEEE), Color(0xFF2979FF), Color(0xFFFF5252), Color(0xFFFFD54F))
                    items(solidColors) { c ->
                        Surface(
                            onClick = { onSettingsChange(settings.copy(solidColor = c)) },
                            shape = CircleShape,
                            color = c,
                            border = BorderStroke(if (settings.solidColor == c) 2.5.dp else 1.dp, Color.White),
                            modifier = Modifier.size(32.dp)
                        ) {}
                    }
                }
            }
            BackgroundMode.GRADIENT -> {
                Text("Studio Gradient Presets", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val gradients = listOf(
                        listOf(Color(0xFFFF758C), Color(0xFFFF7EB3)),
                        listOf(Color(0xFF2193B0), Color(0xFF6DD5ED)),
                        listOf(Color(0xFFCC2B5E), Color(0xFF753A88)),
                        listOf(Color(0xFF11998E), Color(0xFF38EF7D))
                    )
                    items(gradients) { gradColors ->
                        Surface(
                            onClick = { onSettingsChange(settings.copy(gradientColors = gradColors)) },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f)),
                            modifier = Modifier.width(60.dp).height(36.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(gradColors)))
                        }
                    }
                }
            }
            else -> {}
        }

        Spacer(Modifier.height(8.dp))

        // AI Refinement & Shadow Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Hair Refinement & Invert", color = Color.White, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.hairRefinement,
                    onClick = { onSettingsChange(settings.copy(hairRefinement = !settings.hairRefinement)) },
                    label = { Text("Hair Refine", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = settings.invertMask,
                    onClick = { onSettingsChange(settings.copy(invertMask = !settings.invertMask)) },
                    label = { Text("Invert", fontSize = 10.sp) }
                )
            }
        }
    }
}
