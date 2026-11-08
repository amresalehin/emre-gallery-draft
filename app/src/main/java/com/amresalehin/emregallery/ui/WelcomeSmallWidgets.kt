package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


internal fun SegmentButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isSelected) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF161A20),
            modifier = modifier.fillMaxHeight()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxHeight()
                .clickable(onClick = onClick)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF14171B)
                )
            )
        }
    }
}

@Composable


internal fun AiIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFF181C22)) {
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 1.6.dp.toPx()
            val cornerLength = 4.5.dp.toPx()
            val w = size.width
            val h = size.height

            // Top-left
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(cornerLength, 0f), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, cornerLength), strokeWidth)

            // Top-right
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w - cornerLength, 0f), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w, cornerLength), strokeWidth)

            // Bottom-left
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(cornerLength, h), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(0f, h - cornerLength), strokeWidth)

            // Bottom-right
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, h), androidx.compose.ui.geometry.Offset(w - cornerLength, h), strokeWidth)
            drawLine(tint, androidx.compose.ui.geometry.Offset(w, h), androidx.compose.ui.geometry.Offset(w, h - cornerLength), strokeWidth)
        }
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint
        )
    }
}

@Composable
internal fun PermissionCard(
    icon: ImageVector,
    title: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0x75F5F7FA),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF181C22)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF14171A)
                ),
                modifier = Modifier.weight(1f)
            )

            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Permission granted",
                    modifier = Modifier.size(22.dp),
                    tint = Color(0xFF14171A)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Grant permission",
                    modifier = Modifier.size(22.dp),
                    tint = Color(0xFF38404A)
                )
            }
        }
    }
}
