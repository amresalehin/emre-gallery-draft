package com.amresalehin.emregallery.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import com.amresalehin.emregallery.face.DetectedMediaFace
import com.amresalehin.emregallery.face.FaceThumbnailHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun FaceCropThumbnail(
    mediaUri: String?,
    faceIndex: Int?,
    engineId: String,
    contentDescription: String?,
    bounds: RectF? = null,
    fallbackText: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var faceCrop by remember(engineId, mediaUri, faceIndex, bounds) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(engineId, mediaUri, faceIndex, bounds) {
        faceCrop = if (mediaUri.isNullOrBlank() || faceIndex == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                FaceThumbnailHelper.getFaceCrop(
                    context = context,
                    uri = Uri.parse(mediaUri),
                    faceIndex = faceIndex,
                    engineId = engineId,
                    providedBounds = bounds
                )
            }
        }
    }

    val currentFaceCrop = faceCrop
    if (currentFaceCrop != null) {
        Image(
            bitmap = currentFaceCrop.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = modifier,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!fallbackText.isNullOrBlank()) {
                    Text(
                        text = fallbackText.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = contentDescription,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Large portrait for Overview tab. Uses a person's persistent master-face identity.
 */
@Composable
fun OverviewFacePortrait(
    face: DetectedMediaFace,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val portraitUri = face.portraitMediaUri ?: face.mediaUri
    val portraitIndex = face.portraitFaceIndex ?: face.faceIndex

    val displayName = face.displayName.trim()
    val isNamed = displayName.isNotBlank() && !displayName.equals("Unknown", ignoreCase = true)
    val width = kotlin.math.max(0, (face.boxRight - face.boxLeft).toInt())
    val height = kotlin.math.max(0, (face.boxBottom - face.boxTop).toInt())
    val boxText = if (width > 0 && height > 0) "${width}×${height}" else null

    val faceBounds = if (face.boxRight > face.boxLeft && face.boxBottom > face.boxTop) {
        RectF(face.boxLeft, face.boxTop, face.boxRight, face.boxBottom)
    } else null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(min = 96.dp, max = 110.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
            modifier = Modifier.size(96.dp)
        ) {
            FaceCropThumbnail(
                mediaUri = portraitUri,
                faceIndex = portraitIndex,
                engineId = face.engineId,
                bounds = if (portraitUri == face.mediaUri && portraitIndex == face.faceIndex) faceBounds else null,
                fallbackText = if (isNamed) displayName else null,
                contentDescription = if (isNamed) displayName else "Face",
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        }
        if (isNamed) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Stylized cartoon avatar portrait matching reference screenshot.
 */

@OptIn(ExperimentalMaterial3Api::class)


@Composable
fun ActionFacePortrait(
    face: DetectedMediaFace,
    enabled: Boolean = true,
    onRename: () -> Unit,
    modifier: Modifier = Modifier
) {
    val portraitUri = face.portraitMediaUri ?: face.mediaUri
    val portraitIndex = face.portraitFaceIndex ?: face.faceIndex

    val displayName = face.displayName.trim()
    val isNamed = displayName.isNotBlank() && !displayName.equals("Unknown", ignoreCase = true)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(min = 56.dp, max = 70.dp)
            .alpha(if (enabled) 1.0f else 0.45f)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer { alpha = 0.88f }
        ) {
            FaceCropThumbnail(
                mediaUri = portraitUri,
                faceIndex = portraitIndex,
                engineId = face.engineId,
                contentDescription = if (isNamed) displayName else null,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        }
        if (isNamed) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(2.dp))
        IconButton(
            onClick = onRename,
            enabled = enabled,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Rename person",
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
