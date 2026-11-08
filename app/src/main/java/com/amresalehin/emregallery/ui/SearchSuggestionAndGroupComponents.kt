package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.*
import java.util.*


fun SearchSuggestionRow(
    suggestion: SearchSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    val textColor = searchPrimaryTextColor(isDark)
    val secondaryText = searchSecondaryTextColor(isDark)
    val barSurface = searchBarSurfaceColor(isDark)
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("suggestion_${suggestion.id}"),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Thumbnail or Category Icon
            if (suggestion.thumbnailUri != null) {
                AsyncImage(
                    model = remember(suggestion.thumbnailUri, context) {
                        ThumbnailUtils.buildThumbnailRequest(context, suggestion.thumbnailUri)
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(if (suggestion.isPersonPortrait) CircleShape else RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = if (suggestion.isPersonPortrait) CircleShape else RoundedCornerShape(8.dp),
                    color = barSurface
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = when (suggestion.type) {
                            SuggestionType.RECENT -> Icons.Default.History
                            SuggestionType.PERSON -> Icons.Default.Person
                            SuggestionType.PLACE -> Icons.Default.Place
                            SuggestionType.ALBUM -> Icons.Default.PhotoAlbum
                            SuggestionType.FILE -> Icons.Default.Image
                            SuggestionType.TAG -> Icons.Default.Tag
                            SuggestionType.DOCUMENT -> Icons.Default.Description
                        }
                        Icon(icon, contentDescription = null, tint = secondaryText, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Text column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!suggestion.subtitle.isNullOrBlank()) {
                    Text(
                        text = suggestion.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Trailing northwest arrow affordance
            Icon(
                imageVector = Icons.Default.NorthWest,
                contentDescription = "Fill query",
                tint = secondaryText.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
            )
        }
    }
}

/**
 * Advanced Filter Bottom Sheet opened by the search bar funnel icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable


@Composable
fun SearchDateGroupHeader(
    label: String,
    count: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    val textColor = searchPrimaryTextColor(isDark)
    val secondaryText = searchSecondaryTextColor(isDark)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            color = textColor
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count items",
            style = MaterialTheme.typography.bodySmall,
            color = secondaryText,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse group" else "Expand group",
            tint = secondaryText,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Clean neutral empty state when no results match.
 */
@Composable
fun SearchEmptyState(
    query: String,
    hasActiveFilters: Boolean,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    val textColor = searchPrimaryTextColor(isDark)
    val secondaryText = searchSecondaryTextColor(isDark)
    val barSurface = searchBarSurfaceColor(isDark)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = barSurface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = secondaryText
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (query.isNotBlank()) "No matches for “$query”" else "No matching items",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = textColor
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (hasActiveFilters) {
                "Try adjusting or clearing your filters to see more results."
            } else {
                "Check the spelling or try searching by person name, place, album, or file tag."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (hasActiveFilters) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onResetFilters,
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
            ) {
                Text("Clear active filters")
            }
        }
    }
}
