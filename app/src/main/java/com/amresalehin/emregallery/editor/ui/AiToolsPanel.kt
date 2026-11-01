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
import com.amresalehin.emregallery.editor.AiEditSettings
import com.amresalehin.emregallery.editor.AiStyleTransfer
import com.amresalehin.emregallery.editor.AiTryOnStyle

enum class AiSubTool(val label: String) {
    AUTO_ENHANCE("Auto Enhance"),
    EXPAND("Generative Expand"),
    STYLE_TRANSFER("Style Transfer"),
    TRY_ON("Virtual Try-On")
}

/**
 * Dedicated Primary AI Tools Workspace exposing Auto Enhance, Generative Expand,
 * Style Transfer (5 styles), and Virtual Try-On (5 styles) with live previews.
 */
@Composable
fun AiToolsPanel(
    aiSettings: AiEditSettings,
    onToggleAutoEnhance: (Boolean) -> Unit,
    onAutoEnhanceIntensityChange: (Float) -> Unit,
    onExpandRatioChange: (Float) -> Unit,
    onSelectStyleTransfer: (AiStyleTransfer?) -> Unit,
    onSelectTryOn: (AiTryOnStyle?) -> Unit,
    onAutoTone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubTool by remember { mutableStateOf(AiSubTool.AUTO_ENHANCE) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // AI Sub-tool switcher pills
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AiSubTool.entries) { tool ->
                val isSelected = activeSubTool == tool
                val icon = when (tool) {
                    AiSubTool.AUTO_ENHANCE -> Icons.Default.AutoFixHigh
                    AiSubTool.EXPAND -> Icons.Default.ZoomOutMap
                    AiSubTool.STYLE_TRANSFER -> Icons.Default.Palette
                    AiSubTool.TRY_ON -> Icons.Default.Checkroom
                }
                Surface(
                    onClick = { activeSubTool = tool },
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
                            contentDescription = tool.label,
                            tint = if (isSelected) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = tool.label,
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
            AiSubTool.AUTO_ENHANCE -> {
                AutoEnhanceSubPanel(
                    isEnabled = aiSettings.autoEnhanceEnabled,
                    intensity = aiSettings.autoEnhanceIntensity,
                    onToggle = onToggleAutoEnhance,
                    onIntensityChange = onAutoEnhanceIntensityChange,
                    onAutoTone = onAutoTone
                )
            }
            AiSubTool.EXPAND -> {
                GenerativeExpandSubPanel(
                    expandRatio = aiSettings.expandOutpaintRatio,
                    onExpandRatioChange = onExpandRatioChange
                )
            }
            AiSubTool.STYLE_TRANSFER -> {
                AiStyleTransferSubPanel(
                    activeStyle = aiSettings.activeStyleTransfer,
                    onSelectStyle = onSelectStyleTransfer
                )
            }
            AiSubTool.TRY_ON -> {
                AiTryOnSubPanel(
                    activeTryOn = aiSettings.activeTryOn,
                    onSelectTryOn = onSelectTryOn
                )
            }
        }
    }
}

@Composable
fun AutoEnhanceSubPanel(
    isEnabled: Boolean,
    intensity: Float,
    onToggle: (Boolean) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onAutoTone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("AI Neural Auto-Enhance", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Dynamic range, tone curve & clarity equalization", color = Color(0xFFAAAAAA), fontSize = 11.sp)
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = {
                    onToggle(it)
                    if (it) onAutoTone()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF2979FF)
                )
            )
        }

        if (isEnabled) {
            Spacer(Modifier.height(10.dp))
            LrSliderRow(
                title = "Enhancement Intensity",
                value = intensity * 100f,
                displayValue = "${(intensity * 100).toInt()}%",
                valueRange = 20f..200f,
                minLabel = "20%",
                maxLabel = "200%",
                onDragStart = {},
                onValueChange = { onIntensityChange(it / 100f) },
                onValueChangeFinished = {}
            )
        }
    }
}

@Composable
fun GenerativeExpandSubPanel(
    expandRatio: Float,
    onExpandRatioChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Generative Outpainting / Expand", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (expandRatio > 0.05f) {
                TextButton(onClick = { onExpandRatioChange(0f) }) {
                    Text("Reset", color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Ratio Expansion Presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                "Fit" to 0f,
                "+10%" to 0.10f,
                "+20%" to 0.20f,
                "+35%" to 0.35f,
                "+50%" to 0.50f
            )
            presets.forEach { (label, ratio) ->
                val isSelected = kotlin.math.abs(expandRatio - ratio) < 0.03f
                Surface(
                    onClick = { onExpandRatioChange(ratio) },
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
            title = "Expand Boundary",
            value = expandRatio * 100f,
            displayValue = "${(expandRatio * 100).toInt()}%",
            valueRange = 0f..50f,
            minLabel = "0%",
            maxLabel = "50%",
            onDragStart = {},
            onValueChange = { onExpandRatioChange(it / 100f) },
            onValueChangeFinished = {}
        )

        Text(
            text = "Seamlessly outpaints beyond image borders using smooth perimeter extrapolation.",
            color = Color(0xFF888888),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun AiStyleTransferSubPanel(
    activeStyle: AiStyleTransfer?,
    onSelectStyle: (AiStyleTransfer?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI Style Transfer (5 Styles)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (activeStyle != null) {
                TextButton(onClick = { onSelectStyle(null) }) {
                    Text("Remove", color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(AiStyleTransfer.entries) { style ->
                val isSelected = activeStyle == style
                val icon = when (style) {
                    AiStyleTransfer.CYBERPUNK -> Icons.Default.ElectricBolt
                    AiStyleTransfer.ANIME_VIBRANT -> Icons.Default.Palette
                    AiStyleTransfer.CHARCOAL_SKETCH -> Icons.Default.Brush
                    AiStyleTransfer.OIL_PAINTING -> Icons.Default.ColorLens
                    AiStyleTransfer.VINTAGE_SEPIA -> Icons.Default.Camera
                }
                Surface(
                    onClick = { onSelectStyle(if (isSelected) null else style) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) Color.White else Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.width(104.dp).height(70.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = style.label,
                            tint = if (isSelected) Color.Black else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = style.label,
                            color = if (isSelected) Color.Black else Color(0xFFAAAAAA),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiTryOnSubPanel(
    activeTryOn: AiTryOnStyle?,
    onSelectTryOn: (AiTryOnStyle?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Virtual Attire Try-On (5 Outfits)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (activeTryOn != null) {
                TextButton(onClick = { onSelectTryOn(null) }) {
                    Text("Remove", color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(AiTryOnStyle.entries) { outfit ->
                val isSelected = activeTryOn == outfit
                Surface(
                    onClick = { onSelectTryOn(if (isSelected) null else outfit) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) Color.White else Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.width(120.dp).height(74.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Checkroom,
                                contentDescription = null,
                                tint = if (isSelected) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = outfit.label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = outfit.description,
                            color = if (isSelected) Color(0xFF333333) else Color(0xFF888888),
                            fontSize = 9.sp,
                            maxLines = 2,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }
    }
}
