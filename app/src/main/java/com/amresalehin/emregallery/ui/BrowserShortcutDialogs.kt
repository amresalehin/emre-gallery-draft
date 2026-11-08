package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.amresalehin.emregallery.data.BrowserShortcut
import java.util.*


fun AddShortcutDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, url: String) -> Unit,
    isDark: Boolean = false
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = BrowserThemeColors.cardSurface(isDark),
            border = BorderStroke(1.dp, BrowserThemeColors.cardOutline(isDark)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Pin New Shortcut",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrowserThemeColors.textPrimary(isDark)
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name (e.g. Reddit)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BrowserThemeColors.textPrimary(isDark),
                        unfocusedTextColor = BrowserThemeColors.textPrimary(isDark),
                        focusedBorderColor = BrowserThemeColors.textPrimary(isDark),
                        unfocusedBorderColor = BrowserThemeColors.cardOutline(isDark),
                        focusedLabelColor = BrowserThemeColors.textPrimary(isDark),
                        unfocusedLabelColor = BrowserThemeColors.textSecondary(isDark)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL (e.g. reddit.com)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BrowserThemeColors.textPrimary(isDark),
                        unfocusedTextColor = BrowserThemeColors.textPrimary(isDark),
                        focusedBorderColor = BrowserThemeColors.textPrimary(isDark),
                        unfocusedBorderColor = BrowserThemeColors.cardOutline(isDark),
                        focusedLabelColor = BrowserThemeColors.textPrimary(isDark),
                        unfocusedLabelColor = BrowserThemeColors.textSecondary(isDark)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = BrowserThemeColors.textSecondary(isDark))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() && url.isNotBlank()) {
                                onAdd(title, url)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrowserThemeColors.textPrimary(isDark)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Pin", color = BrowserThemeColors.background(isDark))
                    }
                }
            }
        }
    }
}

/**
 * Dialog for editing or removing an existing shortcut.
 */
@Composable
fun EditShortcutDialog(
    shortcut: BrowserShortcut,
    onDismiss: () -> Unit,
    onUpdate: (title: String, url: String) -> Unit,
    onDelete: () -> Unit,
    isDark: Boolean = false
) {
    var title by remember { mutableStateOf(shortcut.title) }
    var url by remember { mutableStateOf(shortcut.url) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = BrowserThemeColors.cardSurface(isDark),
            border = BorderStroke(1.dp, BrowserThemeColors.cardOutline(isDark)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Edit Shortcut",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrowserThemeColors.textPrimary(isDark)
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BrowserThemeColors.textPrimary(isDark),
                        unfocusedTextColor = BrowserThemeColors.textPrimary(isDark),
                        focusedBorderColor = BrowserThemeColors.textPrimary(isDark),
                        unfocusedBorderColor = BrowserThemeColors.cardOutline(isDark),
                        focusedLabelColor = BrowserThemeColors.textPrimary(isDark),
                        unfocusedLabelColor = BrowserThemeColors.textSecondary(isDark)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BrowserThemeColors.textPrimary(isDark),
                        unfocusedTextColor = BrowserThemeColors.textPrimary(isDark),
                        focusedBorderColor = BrowserThemeColors.textPrimary(isDark),
                        unfocusedBorderColor = BrowserThemeColors.cardOutline(isDark),
                        focusedLabelColor = BrowserThemeColors.textPrimary(isDark),
                        unfocusedLabelColor = BrowserThemeColors.textSecondary(isDark)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        onDelete()
                        onDismiss()
                    }) {
                        Text("Unpin", color = Color(0xFFEF4444))
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = BrowserThemeColors.textSecondary(isDark))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (title.isNotBlank() && url.isNotBlank()) {
                                    onUpdate(title, url)
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrowserThemeColors.textPrimary(isDark)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Save", color = BrowserThemeColors.background(isDark))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet displaying full browsing history with instant search and clear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
