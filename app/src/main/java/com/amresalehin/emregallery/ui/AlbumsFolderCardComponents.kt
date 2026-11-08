package com.amresalehin.emregallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import com.amresalehin.emregallery.data.MediaItem


data class CardPalette(
    val bgColor: Color,
    val textColor: Color,
    val badgeBg: Color
)

internal val FolderPalettes = listOf(
    CardPalette(bgColor = Color(0xFFFFD572), textColor = Color(0xFF231B05), badgeBg = Color(0xFF383120)),
    CardPalette(bgColor = Color(0xFFA5D1FE), textColor = Color(0xFF0F1E2E), badgeBg = Color(0xFF22364A)),
    CardPalette(bgColor = Color(0xFFD6B4FC), textColor = Color(0xFF251336), badgeBg = Color(0xFF3B274B)),
    CardPalette(bgColor = Color(0xFFA6E5A8), textColor = Color(0xFF132B14), badgeBg = Color(0xFF243E26)),
    CardPalette(bgColor = Color(0xFFFFB5B5), textColor = Color(0xFF331414), badgeBg = Color(0xFF4A2626)),
    CardPalette(bgColor = Color(0xFF98E8DE), textColor = Color(0xFF0E2B27), badgeBg = Color(0xFF1E3F3C)),
    CardPalette(bgColor = Color(0xFFFFC39A), textColor = Color(0xFF331B0A), badgeBg = Color(0xFF4D301C))
)

internal val AlbumPalettes = listOf(
    CardPalette(bgColor = Color(0xFFE8DEF8), textColor = Color(0xFF25163D), badgeBg = Color(0xFF3C2754)),
    CardPalette(bgColor = Color(0xFFFFD8E4), textColor = Color(0xFF3B1020), badgeBg = Color(0xFF522234)),
    CardPalette(bgColor = Color(0xFFD0E4FF), textColor = Color(0xFF0A223B), badgeBg = Color(0xFF1E3A57)),
    CardPalette(bgColor = Color(0xFFBCEBD9), textColor = Color(0xFF0D2D23), badgeBg = Color(0xFF234539)),
    CardPalette(bgColor = Color(0xFFFFF0B3), textColor = Color(0xFF332505), badgeBg = Color(0xFF4A3815)),
    CardPalette(bgColor = Color(0xFFFFD9D0), textColor = Color(0xFF3B120B), badgeBg = Color(0xFF54251D))
)

class FolderTabShape(
    private val tabWidthRatio: Float = 0.44f,
    private val tabHeightDp: Dp = 12.dp,
    private val cornerRadiusDp: Dp = 18.dp
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val r = with(density) { cornerRadiusDp.toPx() }
        val th = with(density) { tabHeightDp.toPx() }
        val tw = (size.width * tabWidthRatio).coerceAtMost(size.width - r * 2)
        val w = size.width
        val h = size.height

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, r)
            quadraticTo(0f, 0f, r, 0f)
            lineTo(tw - r * 0.5f, 0f)
            cubicTo(
                tw, 0f,
                tw + 3f, th,
                tw + r * 0.7f, th
            )
            lineTo(w - r, th)
            quadraticTo(w, th, w, th + r)
            lineTo(w, h - r)
            quadraticTo(w, h, w - r, h)
            lineTo(r, h)
            quadraticTo(0f, h, 0f, h - r)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

@Composable
internal fun FolderCardItem(
    folderKey: String,
    items: List<MediaItem>,
    palette: CardPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folderTitle = folderKey.substringAfterLast('/').ifBlank { "Internal storage" }
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = FolderTabShape(),
        color = palette.bgColor,
        modifier = modifier
            .width(160.dp)
            .height(220.dp)
            .clickable(onClick = onClick)
            .testTag("folder_card_${folderTitle.replace(' ', '_')}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Count badge at top-right in recessed area beside folder tab
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.badgeBg.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 8.dp)
            ) {
                Text(
                    text = items.size.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 10.dp, top = 26.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 2x2 Collage of up to 4 images
                Collage2x2(
                    items = items.take(4),
                    palette = palette,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                )

                // Bottom title row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = folderTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Folder options",
                                tint = palette.textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open folder") },
                                onClick = {
                                    menuOpen = false
                                    onClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
