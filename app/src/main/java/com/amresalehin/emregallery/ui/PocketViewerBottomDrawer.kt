package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaInboxStack
import java.util.*


@Composable
internal fun ColumnScope.PocketViewerBottomDrawerSurface(
    stack: MediaInboxStack,
    dateFormatted: String,
    faviconUrl: String,
    derivedTags: List<String>,
    textColor: Color,
    secondaryText: Color,
    dividerCol: Color,
    isDark: Boolean,
    onOpenBrowser: (String) -> Unit,
    onEditNote: () -> Unit
) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = PocketThemeColors.drawerSurface(isDark),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, if (isDark) dividerCol.copy(alpha = 0.7f) else dividerCol),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Drag Handle Pill (with swipe down to dismiss drawer gesture)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 2.dp)
                            .size(width = 80.dp, height = 20.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount > 15f) {
                                        onBack()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(PocketThemeColors.drawerHandle(isDark))
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // 1. Time Row
                    PocketDetailCardRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Time",
                        value = dateFormatted,
                        textColor = textColor,
                        secondaryText = secondaryText,
                        barSurface = barSurface,
                        dividerCol = dividerCol
                    )

                    // 2. Source Row (Clickable to open link)
                    PocketDetailCardRow(
                        icon = Icons.Default.Link,
                        label = "Source",
                        value = stack.sourceUrl.ifBlank { stack.sourceDomain.ifBlank { "No source URL" } },
                        trailingIcon = if (stack.sourceUrl.isNotBlank()) Icons.AutoMirrored.Filled.OpenInNew else null,
                        onClick = if (stack.sourceUrl.isNotBlank()) { { onOpenBrowser(stack.sourceUrl) } } else null,
                        textColor = textColor,
                        secondaryText = secondaryText,
                        barSurface = barSurface,
                        dividerCol = dividerCol
                    )

                    // 3. Note Row (Clickable to edit note)
                    PocketDetailCardRow(
                        icon = Icons.Default.Description,
                        label = "Note",
                        value = stack.description.ifBlank { "Tap to add note" },
                        trailingIcon = Icons.Default.Edit,
                        onClick = onEditNote,
                        textColor = textColor,
                        secondaryText = secondaryText,
                        barSurface = barSurface,
                        dividerCol = dividerCol
                    )

                    // 4. Tags Row
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = barSurface,
                        border = BorderStroke(1.dp, if (isDark) dividerCol.copy(alpha = 0.5f) else Color(0xFFE6E6E6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sell,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Tags",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = textColor
                            )
                            Spacer(Modifier.width(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                derivedTags.forEach { tag ->
                                    Surface(
                                        shape = CircleShape,
                                        color = PocketThemeColors.tagChipBg(isDark),
                                        border = BorderStroke(0.5.dp, if (isDark) dividerCol else Color(0xFFDDDDDD)),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        ) {
                                            Text(
                                                text = tag,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                color = textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Title Row
                    PocketDetailCardRow(
                        icon = Icons.Default.ShortText,
                        label = "Title",
                        value = stack.title.ifBlank { "Untitled Bookmark" },
                        textColor = textColor,
                        secondaryText = secondaryText,
                        barSurface = barSurface,
                        dividerCol = dividerCol
                    )

                    // 6. Description Row
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = barSurface,
                        border = BorderStroke(1.dp, if (isDark) dividerCol.copy(alpha = 0.5f) else Color(0xFFE6E6E6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Subject,
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Description",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = textColor
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (stack.description.isNotBlank()) stack.description else "No additional description provided.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (stack.description.isNotBlank()) textColor else secondaryText
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }
}

@Composable
fun PocketDetailCardRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null,
    textColor: Color,
    secondaryText: Color,
    barSurface: Color,
    dividerCol: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = barSurface,
        border = BorderStroke(1.dp, if (textColor.luminance() > 0.5f) dividerCol.copy(alpha = 0.5f) else Color(0xFFE6E6E6)),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = textColor
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = secondaryText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Empty state for Pocket
 * Full empty as requested: remove everything on screen when no bookmark is there.
 */
