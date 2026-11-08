package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.*
import java.util.*


@Composable
internal fun CustomDateRangeDialog(
    onDismiss: () -> Unit,
    onDateRangeSelected: (startMillis: Long, endMillis: Long) -> Unit,
    isDark: Boolean = false
) {
    val textColor = searchPrimaryTextColor(isDark)
    val secondaryText = searchSecondaryTextColor(isDark)
    val cardSurface = searchCardSurfaceColor(isDark)

    var startDaysAgo by remember { mutableIntStateOf(30) }
    var endDaysAgo by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardSurface,
        title = {
            Text(
                text = "Select Date Range",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = textColor
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Filter items taken between:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryText
                )

                listOf(
                    Pair("Past week (7 days)", 7 to 0),
                    Pair("Past month (30 days)", 30 to 0),
                    Pair("Past 6 months (180 days)", 180 to 0),
                    Pair("Past year (365 days)", 365 to 0),
                    Pair("Older than 1 year", 3650 to 365)
                ).forEach { (label, range) ->
                    val isSelected = startDaysAgo == range.first && endDaysAgo == range.second
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                startDaysAgo = range.first
                                endDaysAgo = range.second
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                startDaysAgo = range.first
                                endDaysAgo = range.second
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = label, color = textColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    val startMillis = now - (startDaysAgo.toLong() * 24 * 60 * 60 * 1000L)
                    val endMillis = now - (endDaysAgo.toLong() * 24 * 60 * 60 * 1000L)
                    onDateRangeSelected(startMillis, endMillis)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = textColor,
                    contentColor = if (isDark) SearchColors.DarkBg else Color.White
                )
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = secondaryText)
            }
        }
    )
}
