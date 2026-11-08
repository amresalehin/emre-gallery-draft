package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.CloudAiProvider
import com.amresalehin.emregallery.data.CloudProviderStatus


internal fun DynamicCloudStatusIndicator(
    activeProvider: CloudAiProvider?,
    isTesting: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val status = when {
        isTesting -> CloudProviderStatus.CHECKING
        activeProvider == null -> CloudProviderStatus.UNCONFIGURED
        else -> activeProvider.status
    }

    val (bg, border, contentColor) = when (status) {
        CloudProviderStatus.CONNECTED -> Triple(
            if (isDark) Color(0xFF1B3822) else Color(0xFFE8F5E9),
            if (isDark) Color(0xFF2E7D32) else Color(0xFF81C784),
            if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        )
        CloudProviderStatus.CHECKING -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.primary
        )
        CloudProviderStatus.ERROR -> Triple(
            if (isDark) Color(0xFF381E1E) else Color(0xFFFFEBEE),
            if (isDark) Color(0xFFC62828) else Color(0xFFEF9A9A),
            if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
        )
        CloudProviderStatus.UNCONFIGURED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .height(32.dp)
            .testTag("ai_cloud_status_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            when (status) {
                CloudProviderStatus.CONNECTED -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Cloud Connected",
                        modifier = Modifier.size(14.dp),
                        tint = contentColor
                    )
                    Text(
                        text = activeProvider?.name?.ifBlank { "Connected" } ?: "Connected",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = contentColor
                    )
                }
                CloudProviderStatus.CHECKING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.8.dp,
                        color = contentColor
                    )
                    Text(
                        text = "Testing...",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = contentColor
                    )
                }
                CloudProviderStatus.ERROR -> {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Cloud Error",
                        modifier = Modifier.size(14.dp),
                        tint = contentColor
                    )
                    Text(
                        text = "Offline",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = contentColor
                    )
                }
                CloudProviderStatus.UNCONFIGURED -> {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = "Cloud Not Configured",
                        modifier = Modifier.size(14.dp),
                        tint = contentColor
                    )
                    Text(
                        text = "Not set",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = contentColor
                    )
                }
            }
        }
    }
}

/**
 * Dynamic Status Button for Florence-2 Local Model matching Welcome Screen capsule buttons
 * and app monochrome theme tokens.
 */
