package com.amresalehin.emregallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaInboxStack
import java.util.*

@Composable
internal fun PocketViewerTopBar(
    stack: MediaInboxStack,
    barSurface: Color,
    textColor: Color,
    dividerCol: Color,
    isDark: Boolean,
    context: android.content.Context,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onDelete: () -> Unit
) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = barSurface,
                    shadowElevation = if (isDark) 2.dp else 1.dp,
                    border = BorderStroke(1.dp, if (isDark) dividerCol.copy(alpha = 0.7f) else dividerCol),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onBack)
                        .testTag("bookmark_viewer_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (stack.sourceUrl.isNotBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = barSurface,
                            shadowElevation = if (isDark) 2.dp else 1.dp,
                            border = BorderStroke(1.dp, if (isDark) dividerCol.copy(alpha = 0.7f) else dividerCol),
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { onOpenBrowser(stack.sourceUrl) }
                                .testTag("bookmark_viewer_reader_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = "Open in Reader",
                                    tint = textColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = barSurface,
                        shadowElevation = if (isDark) 2.dp else 1.dp,
                        border = BorderStroke(1.dp, if (isDark) dividerCol.copy(alpha = 0.7f) else dividerCol),
                        modifier = Modifier
                            .size(44.dp)
                            .clickable {
                                val textToCopy = stack.sourceUrl.ifBlank { stack.title }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("URL", textToCopy)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            .testTag("bookmark_viewer_copy_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy link",
                                tint = textColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = barSurface,
                        shadowElevation = if (isDark) 2.dp else 1.dp,
                        border = BorderStroke(1.dp, if (isDark) dividerCol.copy(alpha = 0.7f) else dividerCol),
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(onClick = onDelete)
                            .testTag("bookmark_viewer_delete_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete bookmark",
                                tint = textColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

}
