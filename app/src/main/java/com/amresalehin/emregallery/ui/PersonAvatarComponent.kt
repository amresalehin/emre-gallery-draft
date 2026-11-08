package com.amresalehin.emregallery.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import com.amresalehin.emregallery.face.FaceThumbnailHelper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.face.FaceClusterStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.amresalehin.emregallery.face.PersonSummary


@Composable
internal fun PersonAvatar(
    person: PersonSummary,
    size: androidx.compose.ui.unit.Dp = 88.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var faceBitmap by remember(person.id, person.portraitUri, person.portraitFaceIndex, person.sampleUri, person.engineId) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(person.id, person.portraitUri, person.portraitFaceIndex, person.sampleUri, person.engineId) {
        val targetUri = person.portraitUri ?: person.sampleUri
        if (targetUri != null) {
            val crop = withContext(Dispatchers.IO) {
                val parsed = runCatching { Uri.parse(targetUri) }.getOrNull() ?: return@withContext null
                val fIdx = person.portraitFaceIndex ?: run {
                    val st = FaceClusterStore(context.applicationContext)
                    st.facesForCluster(person.id, person.engineId).firstOrNull()?.faceIndex ?: 0
                }
                FaceThumbnailHelper.getFaceCrop(context, parsed, fIdx, person.engineId)
            }
            faceBitmap = crop
        }
    }

    val currentFaceBitmap = faceBitmap
    if (currentFaceBitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = currentFaceBitmap.asImageBitmap(),
            contentDescription = if (person.isNamed) person.displayName else "Person",
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = modifier.size(size),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (person.isNamed && person.displayName.isNotBlank()) {
                    Text(
                        text = person.displayName.trim().take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.38f).sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(
                        Icons.Default.Face,
                        contentDescription = null,
                        modifier = Modifier.size(size * 0.45f),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

