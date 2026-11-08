package com.amresalehin.emregallery.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.amresalehin.emregallery.data.MediaClassifier
import java.util.*


@Composable
fun AddBookmarkDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, url: String, description: String) -> Unit,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
) {
    val context = LocalContext.current
    val textColor = PocketThemeColors.primaryText(isDark)
    val secondaryText = PocketThemeColors.secondaryText(isDark)
    val dialogSurface = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    val borderCol = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0)

    var urlText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var descText by remember { mutableStateOf("") }

    val clipboard = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val clipboardUrl = remember {
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
            if (MediaClassifier.isValidWebUrl(text) || text.startsWith("http://") || text.startsWith("https://")) text else null
        } else null
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = textColor,
        unfocusedBorderColor = if (isDark) Color(0xFF444444) else Color(0xFFD0D0D0),
        focusedLabelColor = textColor,
        unfocusedLabelColor = secondaryText,
        cursorColor = textColor,
        focusedTextColor = textColor,
        unfocusedTextColor = textColor
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = dialogSurface,
            tonalElevation = if (isDark) 4.dp else 2.dp,
            shadowElevation = if (isDark) 6.dp else 3.dp,
            border = BorderStroke(1.dp, borderCol),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Add Bookmark to Pocket",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Paste from clipboard suggestion
                if (clipboardUrl != null && urlText.isBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF282828) else Color(0xFFF2F2F2),
                        border = BorderStroke(0.5.dp, if (isDark) Color(0xFF404040) else Color(0xFFE0E0E0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                urlText = clipboardUrl
                                if (titleText.isBlank()) {
                                    titleText = MediaClassifier.cleanDomainFromUrl(clipboardUrl)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Paste: $clipboardUrl",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        if (titleText.isBlank() && it.isNotBlank()) {
                            val domain = MediaClassifier.cleanDomainFromUrl(it)
                            if (domain.isNotBlank()) titleText = domain
                        }
                    },
                    label = { Text("Web URL / Link *") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    colors = textFieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Title (optional)") },
                    placeholder = { Text("Article or page title") },
                    singleLine = true,
                    colors = textFieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("Notes / Description (optional)") },
                    placeholder = { Text("Add notes or details") },
                    maxLines = 3,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = secondaryText)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (urlText.isNotBlank()) {
                                onSave(titleText, urlText, descText)
                            }
                        },
                        enabled = urlText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFFEEEEEE) else Color(0xFF202020),
                            contentColor = if (isDark) Color(0xFF161616) else Color(0xFFFFFFFF),
                            disabledContainerColor = if (isDark) Color(0xFF282828) else Color(0xFFE8E8E8),
                            disabledContentColor = if (isDark) Color(0xFF666666) else Color(0xFFA0A0A0)
                        )
                    ) {
                        Text("Save Bookmark")
                    }
                }
            }
        }
    }
}

/**
 * Edit Bookmark Dialog
 */
