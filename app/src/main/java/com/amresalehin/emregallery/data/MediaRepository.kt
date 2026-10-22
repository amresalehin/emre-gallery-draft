package com.amresalehin.emregallery.data

import android.content.ContentUris
import android.content.ContentResolver
import android.provider.MediaStore

/** Immutable item exposed to the UI; [uri] is the stable ContentResolver URI. */
data class MediaItem(
    val id: Long,
    val uri: String,
    val name: String,
    val mimeType: String?,
    val dateAddedSeconds: Long,
    val dateTakenMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val relativePath: String,
    val isVideo: Boolean,
    val durationMillis: Long = 0L,
)

class MediaRepository(private val resolver: ContentResolver) {
    fun loadMedia(): List<MediaItem> {
        val rows = mutableListOf<MediaItem>()
        queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, rows)
        queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, rows)
        return rows.sortedWith(compareByDescending<MediaItem> { if (it.dateTakenMillis > 0) it.dateTakenMillis else it.dateAddedSeconds * 1000 })
    }

    private fun queryCollection(collection: android.net.Uri, video: Boolean, rows: MutableList<MediaItem>) {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.RELATIVE_PATH)
            add(MediaStore.Images.ImageColumns.DATE_TAKEN)
            if (video) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()
        val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        resolver.query(collection, projection, null, null, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val addedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val takenCol = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN)
            val durationCol = if (video) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val itemUri = ContentUris.withAppendedId(collection, id)
                rows += MediaItem(
                    id = id,
                    uri = itemUri.toString(),
                    name = cursor.getString(nameCol) ?: "Untitled",
                    mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else null,
                    dateAddedSeconds = if (addedCol >= 0) cursor.getLong(addedCol) else 0,
                    dateTakenMillis = if (takenCol >= 0 && !cursor.isNull(takenCol)) cursor.getLong(takenCol) else 0,
                    sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0,
                    width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                    height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                    relativePath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else "",
                    isVideo = video,
                    durationMillis = if (durationCol >= 0 && !cursor.isNull(durationCol)) cursor.getLong(durationCol) else 0,
                )
            }
        }
    }
}
