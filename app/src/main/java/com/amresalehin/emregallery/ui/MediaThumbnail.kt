package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.ThumbnailRequestFactory

/**
 * One renderer for collection thumbnails. Target resolution follows the measured slot and
 * changes only when its normalized bucket changes, avoiding recomposition-driven cache churn.
 */
@Composable
fun MediaThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
    contentDescription: String? = item.name,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val widthPx = if (maxWidth != Dp.Infinity && maxWidth.value > 0f) {
            with(density) { maxWidth.roundToPx() }
        } else 320
        val heightPx = if (maxHeight != Dp.Infinity && maxHeight.value > 0f) {
            with(density) { maxHeight.roundToPx() }
        } else 320
        val size = ThumbnailRequestFactory.sizeFor(widthPx, heightPx)
        val request = remember(context, item.uri, item.isVideo, item.sizeBytes, size.width, size.height) {
            ThumbnailRequestFactory.create(context, item, size.width, size.height)
        }

        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale
        )
    }
}
