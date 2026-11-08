package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.amresalehin.emregallery.data.MediaInboxStack
import java.util.*


@Composable
fun EditBookmarkDialog(
    stack: MediaInboxStack,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String) -> Unit,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
) {
    val textColor = PocketThemeColors.primaryText(isDark)
    val secondaryText = PocketThemeColors.secondaryText(isDark)
    val dialogSurface = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    val borderCol = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0)

    var titleText by remember { mutableStateOf(stack.title) }
    var descText by remember { mutableStateOf(stack.description) }

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
                Text(
                    text = "Edit Bookmark",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("Notes / Description") },
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
                        onClick = { onSave(titleText, descText) },
                        enabled = titleText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFFEEEEEE) else Color(0xFF202020),
                            contentColor = if (isDark) Color(0xFF161616) else Color(0xFFFFFFFF),
                            disabledContainerColor = if (isDark) Color(0xFF282828) else Color(0xFFE8E8E8),
                            disabledContentColor = if (isDark) Color(0xFF666666) else Color(0xFFA0A0A0)
                        )
                    ) {
                        Text("Update")
                    }
                }
            }
        }
    }
}

/**
 * Pocket Filter & Sort Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketFilterBottomSheet(
    currentSort: String,
    currentFilter: String,
    onSelectSort: (String) -> Unit,
    onSelectFilter: (String) -> Unit,
    onDismiss: () -> Unit,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
) {
    val textColor = PocketThemeColors.primaryText(isDark)
    val secondaryText = PocketThemeColors.secondaryText(isDark)
    val sheetSurface = if (isDark) Color(0xFF1C1C1C) else Color(0xFFFFFFFF)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Sort & Filter Bookmarks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Sort By",
                style = MaterialTheme.typography.labelMedium,
                color = secondaryText
            )

            Spacer(Modifier.height(8.dp))

            val sortOptions = listOf("Newest", "Oldest", "Title", "Domain")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortOptions.forEach { sort ->
                    val selected = currentSort == sort
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectSort(sort) },
                        label = { Text(sort, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (isDark) Color(0xFFEEEEEE) else Color(0xFF202020),
                            selectedLabelColor = if (isDark) Color(0xFF161616) else Color(0xFFFFFFFF),
                            containerColor = if (isDark) Color(0xFF242424) else Color(0xFFF2F2F2),
                            labelColor = if (isDark) Color(0xFFCCCCCC) else Color(0xFF444444)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selected) Color.Transparent else (if (isDark) Color(0xFF383838) else Color(0xFFE0E0E0))
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Show",
                style = MaterialTheme.typography.labelMedium,
                color = secondaryText
            )

            Spacer(Modifier.height(8.dp))

            val filterOptions = listOf("All", "Web Links", "Notes")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterOptions.forEach { filter ->
                    val selected = currentFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectFilter(filter) },
                        label = { Text(filter, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (isDark) Color(0xFFEEEEEE) else Color(0xFF202020),
                            selectedLabelColor = if (isDark) Color(0xFF161616) else Color(0xFFFFFFFF),
                            containerColor = if (isDark) Color(0xFF242424) else Color(0xFFF2F2F2),
                            labelColor = if (isDark) Color(0xFFCCCCCC) else Color(0xFF444444)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selected) Color.Transparent else (if (isDark) Color(0xFF383838) else Color(0xFFE0E0E0))
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
