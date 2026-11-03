package com.amresalehin.emregallery.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.*

/**
 * Dedicated Vignette Panel for the separate Vignette Domain.
 * Features an interactive visual preview canvas, precision sliders
 * (Amount, Midpoint, Feather, Roundness), and quick artistic presets.
 */
@Composable
fun VignettePanel(
    vignette: Float,
    vignetteMidpoint: Float,
    vignetteFeather: Float,
    vignetteRoundness: Float,
    onVignetteChange: (Float) -> Unit,
    onMidpointChange: (Float) -> Unit,
    onFeatherChange: (Float) -> Unit,
    onRoundnessChange: (Float) -> Unit,
    onApplyPreset: (amount: Float, midpoint: Float, feather: Float, roundness: Float) -> Unit = { _, _, _, _ -> },
    onReset: () -> Unit,
    onSliderDragStart: (name: String, formattedVal: String) -> Unit,
    onSliderDragFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        // Header with reset button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Vignette Framing",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            IconButton(
                onClick = onReset,
                modifier = Modifier.size(36.dp).testTag("vignette_reset_button")
            ) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = "Reset Vignette",
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Amount Slider (-100..+100)
        LrBipolarSliderRow(
            title = "Amount",
            value = vignette,
            onSliderDragStart = onSliderDragStart,
            onSliderChange = { _, v -> onVignetteChange(v) },
            onSliderChangeFinished = onSliderDragFinished,
            minLabel = "Dark",
            maxLabel = "White",
            dragLabel = "Vignette Amount"
        )

        // Midpoint Slider (0..100)
        LrPercentageSliderRow(
            title = "Midpoint",
            propName = "Midpoint",
            value = vignetteMidpoint,
            onSliderDragStart = onSliderDragStart,
            onSliderChange = { _, v -> onMidpointChange(v) },
            onSliderChangeFinished = onSliderDragFinished,
            minLabel = "Small",
            maxLabel = "Large",
            dragLabel = "Vignette Midpoint"
        )

        // Feather Slider (0..100)
        LrPercentageSliderRow(
            title = "Feather",
            propName = "Feather",
            value = vignetteFeather,
            onSliderDragStart = onSliderDragStart,
            onSliderChange = { _, v -> onFeatherChange(v) },
            onSliderChangeFinished = onSliderDragFinished,
            minLabel = "Hard",
            maxLabel = "Soft",
            dragLabel = "Vignette Feather"
        )

        // Roundness Slider (-100..+100)
        LrBipolarSliderRow(
            title = "Roundness",
            value = vignetteRoundness,
            onSliderDragStart = onSliderDragStart,
            onSliderChange = { _, v -> onRoundnessChange(v) },
            onSliderChangeFinished = onSliderDragFinished,
            minLabel = "Rect",
            maxLabel = "Circle",
            dragLabel = "Vignette Roundness"
        )
    }
}

/**
 * Dedicated Curves Domain Panel for interactive RGB and per-channel tone curve adjustments.
 */
@Composable
fun CurvesDomainPanel(
    curves: RgbCurves,
    activeChannel: CurveChannel,
    onSelectChannel: (CurveChannel) -> Unit,
    onOpenFullscreenCurves: () -> Unit = {},
    onApplyPreset: (String) -> Unit,
    onResetChannel: (CurveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        // Channel Selector Dots & Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel switcher dots: RGB (White), Red, Green, Blue
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val channels = listOf(
                    Triple(CurveChannel.RGB, Color.White, "RGB"),
                    Triple(CurveChannel.RED, Color(0xFFFF5252), "Red"),
                    Triple(CurveChannel.GREEN, Color(0xFF69F0AE), "Green"),
                    Triple(CurveChannel.BLUE, Color(0xFF448AFF), "Blue")
                )

                channels.forEach { (channel, dotColor, label) ->
                    val isSel = activeChannel == channel
                    Surface(
                        onClick = { onSelectChannel(channel) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSel) Color(0xFF2C2C2C) else Color(0xFF181818),
                        border = BorderStroke(1.dp, if (isSel) dotColor else Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = label,
                                color = if (isSel) Color.White else Color(0xFFAAAAAA),
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Reset Channel Button
            IconButton(
                onClick = { onResetChannel(activeChannel) },
                modifier = Modifier.size(34.dp).testTag("curves_reset_channel_button")
            ) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = "Reset Channel",
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Quick Curve Presets
        Text(
            text = "Curve Tone Presets",
            color = Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val presets = listOf(
                "Linear" to "Linear",
                "Medium Contrast" to "Medium Contrast",
                "High Contrast" to "High Contrast",
                "Matte Fade" to "Matte Fade",
                "Cross Process" to "Cross Process"
            )
            items(presets) { (label, key) ->
                Surface(
                    onClick = { onApplyPreset(key) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.height(30.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Blur domain panel: slider + presets + reset.
 */
@Composable
fun BlurPanel(
    blur: Float,
    onBlurChange: (Float) -> Unit,
    onReset: () -> Unit,
    onSliderDragStart: (name: String, formattedVal: String) -> Unit,
    onSliderDragFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEmreEditorColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.panelSurface)
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BlurOn,
                    contentDescription = null,
                    tint = colors.primaryText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Blur",
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            if (blur > 0.1f) {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(36.dp).testTag("blur_reset_button")
                ) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = "Reset Blur",
                        tint = colors.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LrPercentageSliderRow(
            title = "Blur Amount",
            propName = "Blur",
            value = blur,
            onSliderDragStart = onSliderDragStart,
            onSliderChange = { _, v -> onBlurChange(v) },
            onSliderChangeFinished = onSliderDragFinished,
            dragLabel = "Blur"
        )
    }
}
