package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView


internal fun androidx.compose.foundation.lazy.LazyListScope.albumsMapCardItem(
    onFilter: (String) -> Unit
) {
        // 3. Maps Card (strictly black-grey-white)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 2.dp,
                shadowElevation = 5.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFilter("Map") }
                    .testTag("albums_map_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    var mapPreviewReady by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(160)
                        mapPreviewReady = true
                    }
                    if (mapPreviewReady) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                Configuration.getInstance().userAgentValue = ctx.packageName
                                MapView(ctx).apply {
                                    setTileSource(TileSourceFactory.MAPNIK)
                                    setMultiTouchControls(false)
                                    setBuiltInZoomControls(false)
                                    controller.setZoom(4.5)
                                    controller.setCenter(GeoPoint(22.5, 79.0))
                                    isClickable = false
                                    isFocusable = false
                                }
                            },
                            update = { preview ->
                                preview.setOnTouchListener { _, _ -> false }
                                preview.invalidate()
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                )
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable { onFilter("Map") }
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.08f),
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
                                    )
                                )
                            )
                    )

                    // Content overlay
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = "Map and Places",
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Map",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
}
