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
import com.amresalehin.emregallery.editor.RetouchStroke
import com.amresalehin.emregallery.editor.RetouchType

enum class HealingCategory(val label: String) {
    REPAIR("Repair"),
    SKIN_BEAUTY("Skin & Beauty"),
    EYES_HAIR("Eyes & Hair"),
    RESHAPE_SCULPT("Reshape & Sculpt")
}

/**
 * Healing & Retouch Panel exposing all 20 retouch algorithms grouped into 4 categories:
 * Repair, Skin & Beauty, Eyes & Hair, and Reshape & Sculpt.
 */
@Composable
fun HealingExtendedPanel(
    activeType: RetouchType,
    onSelectType: (RetouchType) -> Unit,
    brushRadius: Float,
    onRadiusChange: (Float) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    tintColor: Color,
    onTintColorChange: (Color) -> Unit,
    strokeCount: Int,
    onClearStrokes: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(HealingCategory.REPAIR) }

    // Map each retouch type to its category
    val repairTypes = listOf(
        RetouchType.BLEMISH,
        RetouchType.OBJECT_REMOVAL,
        RetouchType.WRINKLE_REMOVAL,
        RetouchType.RED_EYE,
        RetouchType.PATCH_HEALING,
        RetouchType.CLONE_STAMP
    )
    val skinBeautyTypes = listOf(
        RetouchType.SKIN_SMOOTH,
        RetouchType.SKIN_TONE,
        RetouchType.TEETH_WHITENING,
        RetouchType.MAKEUP_BRUSH
    )
    val eyesHairTypes = listOf(
        RetouchType.EYE_ENHANCEMENT,
        RetouchType.EYE_COLOUR,
        RetouchType.HAIR_RECOLOUR
    )
    val reshapeSculptTypes = listOf(
        RetouchType.FACE_RESHAPE,
        RetouchType.NOSE_RESHAPE,
        RetouchType.LIP_RESHAPE,
        RetouchType.SMUDGE_BRUSH,
        RetouchType.DODGE_BRUSH,
        RetouchType.BURN_BRUSH,
        RetouchType.SELECTIVE_BRUSH
    )

    // Sync selectedCategory when activeType changes externally
    LaunchedEffect(activeType) {
        when {
            activeType in repairTypes -> selectedCategory = HealingCategory.REPAIR
            activeType in skinBeautyTypes -> selectedCategory = HealingCategory.SKIN_BEAUTY
            activeType in eyesHairTypes -> selectedCategory = HealingCategory.EYES_HAIR
            activeType in reshapeSculptTypes -> selectedCategory = HealingCategory.RESHAPE_SCULPT
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // -------------------------------------------------------------
        // TOP: Sliders, Contextual Controls & Tool Parameters
        // -------------------------------------------------------------
        Column(modifier = Modifier.fillMaxWidth()) {
            // Group Header & Clear button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${activeType.label} ($strokeCount strokes)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                if (strokeCount > 0) {
                    TextButton(onClick = onClearStrokes) {
                        Text("Clear Retouch", color = Color(0xFFFF8A80), fontSize = 12.sp)
                    }
                }
            }

            // Brush Radius Slider
            LrSliderRow(
                title = "Brush Radius",
                value = brushRadius,
                displayValue = "${brushRadius.toInt()} px",
                valueRange = 4f..120f,
                minLabel = "4 px",
                maxLabel = "120 px",
                onDragStart = {},
                onValueChange = onRadiusChange,
                onValueChangeFinished = {}
            )

            // Tool-specific parameters: Strength / Intensity
            LrSliderRow(
                title = "Strength",
                value = intensity * 100f,
                displayValue = "${(intensity * 100).toInt()}%",
                valueRange = 10f..100f,
                minLabel = "10%",
                maxLabel = "100%",
                onDragStart = {},
                onValueChange = { onIntensityChange(it / 100f) },
                onValueChangeFinished = {}
            )

            // Tint Color row for color-based tools
            val requiresColor = activeType in listOf(
                RetouchType.EYE_COLOUR,
                RetouchType.HAIR_RECOLOUR,
                RetouchType.MAKEUP_BRUSH,
                RetouchType.SKIN_TONE
            )

            if (requiresColor) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Tint:", color = Color(0xFFAAAAAA), fontSize = 11.sp)
                    val tintColors = when (activeType) {
                        RetouchType.EYE_COLOUR -> listOf(
                            Color(0xFF2979FF), Color(0xFF00E676), Color(0xFF8D6E63),
                            Color(0xFF795548), Color(0xFFB0BEC5), Color(0xFFAB47BC)
                        )
                        RetouchType.HAIR_RECOLOUR -> listOf(
                            Color(0xFFD7CCC8), Color(0xFF8D6E63), Color(0xFF3E2723),
                            Color(0xFFFF8A65), Color(0xFFBA68C8), Color(0xFF4FC3F7), Color(0xFFFF4081)
                        )
                        RetouchType.MAKEUP_BRUSH -> listOf(
                            Color(0xFFE91E63), Color(0xFFFF4081), Color(0xFFFF5252),
                            Color(0xFFFF8A80), Color(0xFFC2185B), Color(0xFFFF7043)
                        )
                        else -> listOf(
                            Color(0xFFFFE0B2), Color(0xFFFFCC80), Color(0xFFD7CCC8),
                            Color(0xFFBCAAA4), Color(0xFF8D6E63), Color(0xFF5D4037)
                        )
                    }

                    tintColors.forEach { c ->
                        val isSel = tintColor == c
                        Surface(
                            onClick = { onTintColorChange(c) },
                            shape = CircleShape,
                            color = c,
                            border = BorderStroke(if (isSel) 2.dp else 1.dp, Color.White),
                            modifier = Modifier.size(24.dp)
                        ) {
                            if (isSel) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (c.red + c.green + c.blue > 2f) Color.Black else Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // -------------------------------------------------------------
        // TIER 3 (Above Tier 2): Tool Selector Pills for the Active Category
        // -------------------------------------------------------------
        val currentTypes = when (selectedCategory) {
            HealingCategory.REPAIR -> repairTypes
            HealingCategory.SKIN_BEAUTY -> skinBeautyTypes
            HealingCategory.EYES_HAIR -> eyesHairTypes
            HealingCategory.RESHAPE_SCULPT -> reshapeSculptTypes
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(currentTypes) { type ->
                val isSelected = activeType == type
                val icon = when (type) {
                    RetouchType.BLEMISH -> Icons.Default.Adjust
                    RetouchType.OBJECT_REMOVAL -> Icons.Default.AutoFixNormal
                    RetouchType.WRINKLE_REMOVAL -> Icons.Default.Face
                    RetouchType.RED_EYE -> Icons.Default.Visibility
                    RetouchType.PATCH_HEALING -> Icons.Default.Healing
                    RetouchType.CLONE_STAMP -> Icons.Default.ContentCopy
                    RetouchType.SKIN_SMOOTH -> Icons.Default.Face
                    RetouchType.SKIN_TONE -> Icons.Default.Palette
                    RetouchType.TEETH_WHITENING -> Icons.Default.Mood
                    RetouchType.MAKEUP_BRUSH -> Icons.Default.Brush
                    RetouchType.EYE_ENHANCEMENT -> Icons.Default.RemoveRedEye
                    RetouchType.EYE_COLOUR -> Icons.Default.ColorLens
                    RetouchType.HAIR_RECOLOUR -> Icons.Default.FormatColorFill
                    RetouchType.FACE_RESHAPE -> Icons.Default.Transform
                    RetouchType.NOSE_RESHAPE -> Icons.Default.CenterFocusStrong
                    RetouchType.LIP_RESHAPE -> Icons.Default.SentimentSatisfiedAlt
                    RetouchType.SMUDGE_BRUSH -> Icons.Default.TouchApp
                    RetouchType.DODGE_BRUSH -> Icons.Default.WbSunny
                    RetouchType.BURN_BRUSH -> Icons.Default.Brightness3
                    RetouchType.SELECTIVE_BRUSH -> Icons.Default.FilterVintage
                }

                Surface(
                    onClick = { onSelectType(type) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = type.label,
                            tint = if (isSelected) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = type.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // -------------------------------------------------------------
        // TIER 2 (Just above bottom bar): 4 Category Tabs
        // Repair, Skin & Beauty, Eyes & Hair, Reshape & Sculpt
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HealingCategory.entries.forEach { cat ->
                val isSelected = selectedCategory == cat
                Surface(
                    onClick = {
                        selectedCategory = cat
                        val targetList = when (cat) {
                            HealingCategory.REPAIR -> repairTypes
                            HealingCategory.SKIN_BEAUTY -> skinBeautyTypes
                            HealingCategory.EYES_HAIR -> eyesHairTypes
                            HealingCategory.RESHAPE_SCULPT -> reshapeSculptTypes
                        }
                        if (activeType !in targetList) {
                            onSelectType(targetList.first())
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) Color.White else Color(0xFF222222),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = cat.label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
