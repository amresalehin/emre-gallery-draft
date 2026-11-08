package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
internal fun AiSignatureIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF181C22)
) {
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

/**
 * Sleek Segment Option button matching Welcome Screen's SegmentButton.
 */
@Composable
internal fun AiSegmentOption(
    text: String,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isSelected) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF161A20),
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
                        color = if (isDark) MaterialTheme.colorScheme.onPrimary else Color.White
                    )
                )
            }
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

/**
 * Minimalist input field with rounded capsule styling matching Welcome Screen elements.
 */
@Composable
internal fun MinimalAiInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onToggleShowPassword: (() -> Unit)? = null,
    testTag: String = ""
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (isPassword && value.isNotEmpty()) {
                IconButton(
                    onClick = { onToggleShowPassword?.invoke() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide API key" else "Show API key",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dynamic Status-Indicator Button for Cloud State with sleek capsule styling.
 */
@Composable
