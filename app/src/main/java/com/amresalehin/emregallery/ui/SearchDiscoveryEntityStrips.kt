package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.*
import java.util.*


internal fun androidx.compose.foundation.lazy.LazyListScope.discoveryEntityStripsItems(
    discoveryData: DiscoveryData?,
    onSelectEntity: (DiscoveryEntityItem) -> Unit,
    textColor: Color,
    secondaryText: Color,
    cardSurface: Color,
    dividerCol: Color,
    context: android.content.Context
) {
        // 3. Real People (Horizontal strip of recognized person clusters)
        if (discoveryData != null && discoveryData.people.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "People",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = textColor
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(discoveryData.people) { person ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable { onSelectEntity(person) }
                                    .testTag("discovery_person_${person.id}")
                            ) {
                                if (person.thumbnailUri != null) {
                                    AsyncImage(
                                        model = remember(person.thumbnailUri, context) {
                                            ThumbnailUtils.buildThumbnailRequest(context, person.thumbnailUri)
                                        },
                                        contentDescription = person.title,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, dividerCol, CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.size(64.dp),
                                        shape = CircleShape,
                                        color = cardSurface,
                                        border = BorderStroke(1.dp, dividerCol)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Person, null, tint = secondaryText, modifier = Modifier.size(28.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = person.title,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!person.subtitle.isNullOrBlank()) {
                                    Text(
                                        text = person.subtitle,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = secondaryText,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Real Places (Horizontal strip of indexed locations)
        if (discoveryData != null && discoveryData.places.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Places",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = textColor
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(discoveryData.places) { place ->
                            Surface(
                                modifier = Modifier
                                    .width(118.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectEntity(place) }
                                    .testTag("discovery_place_${place.id}"),
                                shape = RoundedCornerShape(12.dp),
                                color = cardSurface,
                                border = BorderStroke(1.dp, dividerCol)
                            ) {
                                Column {
                                    if (place.thumbnailUri != null) {
                                        AsyncImage(
                                            model = remember(place.thumbnailUri, context) {
                                                ThumbnailUtils.buildThumbnailRequest(context, place.thumbnailUri)
                                            },
                                            contentDescription = place.title,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(76.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(76.dp)
                                                .background(dividerCol.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Place, null, tint = secondaryText)
                                        }
                                    }
                                    Column(Modifier.padding(8.dp)) {
                                        Text(
                                            text = place.title,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!place.subtitle.isNullOrBlank()) {
                                            Text(
                                                text = place.subtitle,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = secondaryText,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    discoveryAlbumsAndTagsItems(
        discoveryData = discoveryData,
        onSelectEntity = onSelectEntity,
        textColor = textColor,
        secondaryText = secondaryText,
        cardSurface = cardSurface,
        dividerCol = dividerCol,
        context = context
    )
}
