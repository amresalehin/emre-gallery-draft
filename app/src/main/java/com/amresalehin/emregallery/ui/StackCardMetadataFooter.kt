package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.MediaInboxStack

/**
 * Footer displaying the source site favicon (or fallback initial), stack title,
 * timestamp, and saved item count.
 */
@Composable
internal fun StackCardMetadataFooter(
    stack: MediaInboxStack,
    dateStr: String
) {
    val context = LocalContext.current
    val cleanDomain = remember(stack.sourceDomain, stack.sourceUrl) {
        StackCardPresentation.resolveCleanDomain(stack)
    }
    val faviconUrl = remember(cleanDomain) {
        StackCardPresentation.resolveFaviconUrl(cleanDomain)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            var faviconFailed by remember(faviconUrl) { mutableStateOf(false) }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                modifier = Modifier.size(17.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!faviconFailed && faviconUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(faviconUrl)
                                .crossfade(true)
                                .listener(onError = { _, _ -> faviconFailed = true })
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        val initial = cleanDomain.firstOrNull()?.uppercaseChar()?.toString()
                            ?: stack.title.firstOrNull()?.uppercaseChar()?.toString()
                            ?: "W"
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = stack.title.ifBlank { stack.sourceDomain.ifBlank { "Saved Collection" } },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "$dateStr • ${stack.items.size} ${if (stack.items.size == 1) "item" else "items"}",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}
