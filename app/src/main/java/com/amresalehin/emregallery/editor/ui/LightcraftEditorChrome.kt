package com.amresalehin.emregallery.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.editor.EditorTool

private val LcRail = Color(0xFF202020)
private val LcPanel = Color(0xFF292929)
private val LcText = Color(0xFFE7E7E7)
private val LcMuted = Color(0xFF969696)

private data class LcTool(val tool: EditorTool, val label: String, val icon: ImageVector)
private val lcTools = listOf(
    LcTool(EditorTool.TOOLS, "Actions", Icons.Default.AutoFixHigh),
    LcTool(EditorTool.EFFECTS, "Presets", Icons.Default.FilterVintage),
    LcTool(EditorTool.CROP, "Crop", Icons.Default.Crop),
    LcTool(EditorTool.ADJUST, "Edit", Icons.Default.Tune),
    LcTool(EditorTool.BRUSHES, "Masking", Icons.Default.Layers),
    LcTool(EditorTool.RETOUCH, "Remove", Icons.Default.Healing)
)

@Composable
fun LightcraftToolNavigation(
    activeTool: EditorTool,
    toolSelected: Boolean,
    onSelectTool: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    if (vertical) {
        Column(
            modifier.width(54.dp).fillMaxHeight().background(LcRail).padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            lcTools.forEach { spec ->
                LcToolButton(spec, activeTool == spec.tool && toolSelected, !toolSelected, true) { onSelectTool(spec.tool) }
            }
        }
    } else {
        Row(
            modifier.fillMaxWidth().height(if (toolSelected) 54.dp else 68.dp)
                .navigationBarsPadding().background(LcRail).padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = if (toolSelected) Arrangement.Center else Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            lcTools.forEach { spec ->
                LcToolButton(
                    spec, activeTool == spec.tool && toolSelected, !toolSelected, false,
                    Modifier.weight(1f)
                ) { onSelectTool(spec.tool) }
            }
        }
    }
}

@Composable
private fun LcToolButton(
    spec: LcTool,
    selected: Boolean,
    showLabel: Boolean,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(if (selected) Color.White else LcMuted, tween(120), label = "lightcraft-tool")
    Column(
        modifier.then(if (vertical) Modifier.width(54.dp).height(if (showLabel) 68.dp else 54.dp) else Modifier.height(if (showLabel) 68.dp else 54.dp))
            .clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(spec.icon, contentDescription = spec.label, tint = tint, modifier = Modifier.size(21.dp))
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(spec.label, color = tint, fontSize = 10.sp, maxLines = 1)
        }
        if (selected) {
            Spacer(Modifier.height(3.dp))
            Spacer(Modifier.width(20.dp).height(2.dp).background(Color.White, RoundedCornerShape(2.dp)))
        }
    }
}

private data class LcSection(val name: String, val property: String, val icon: ImageVector)
private val lcSections = listOf(
    LcSection("Light", "Exposure", Icons.Default.WbSunny),
    LcSection("Color", "Temperature", Icons.Default.Palette),
    LcSection("Effects", "Blur", Icons.Default.AutoFixHigh),
    LcSection("Detail", "Sharpness", Icons.Default.Camera)
)

@Composable
fun LightcraftSectionNavigation(
    activeSection: String,
    onSelectSection: (String) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    if (vertical) {
        Column(modifier.width(56.dp).fillMaxHeight().background(LcRail).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            lcSections.forEach { item -> LcSectionButton(item, activeSection == item.name, true) { onSelectSection(item.name) } }
        }
    } else {
        Row(
            modifier.fillMaxWidth().height(68.dp).background(LcRail).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            lcSections.forEach { item -> LcSectionButton(item, activeSection == item.name, false) { onSelectSection(item.name) } }
        }
    }
}

@Composable
private fun LcSectionButton(item: LcSection, selected: Boolean, vertical: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.then(if (vertical) Modifier.width(56.dp).height(68.dp) else Modifier.width(62.dp).height(64.dp))
            .clickable(onClick = onClick).padding(vertical = 5.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(item.icon, contentDescription = item.name, tint = if (selected) Color.White else LcMuted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(5.dp))
        Text(item.name, color = if (selected) LcText else LcMuted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, textAlign = TextAlign.Center)
        if (selected) {
            Spacer(Modifier.height(3.dp))
            Spacer(Modifier.width(18.dp).height(2.dp).background(Color.White, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
fun LightcraftPanelSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = LcPanel.copy(alpha = 0.95f), contentColor = LcText, shape = RoundedCornerShape(0.dp), shadowElevation = 0.dp) { content() }
}

@Composable
fun LightcraftModeBar(title: String, onCancel: () -> Unit, onApply: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(61.dp).background(LcRail).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onCancel, color = Color.Transparent) { Text("CANCEL", color = LcMuted, fontSize = 11.sp, modifier = Modifier.padding(10.dp)) }
        Text(title, color = LcText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Surface(onClick = onApply, color = Color.Transparent) { Text("APPLY", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp)) }
    }
}

@Composable
fun LightcraftModeRail(onCancel: () -> Unit, onApply: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(49.dp).fillMaxHeight().background(LcRail), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        Surface(onClick = onCancel, color = Color.Transparent) { Text("×", color = LcText, fontSize = 26.sp, modifier = Modifier.padding(9.dp)) }
        Surface(onClick = onApply, color = Color.Transparent) { Text("✓", color = Color.White, fontSize = 23.sp, modifier = Modifier.padding(9.dp)) }
    }
}

fun lightcraftDefaultAdjustment(section: String): String = when (section) {
    "Color" -> "Temperature"
    "Effects" -> "Blur"
    "Detail" -> "Sharpness"
    else -> "Exposure"
}
