package com.amresalehin.emregallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

class MediaRepository(private val resolver: ContentResolver) {

    fun loadMedia(): List<MediaItem> {
        val rows = ArrayList<MediaItem>()
        queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, rows)
        queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, rows)
        return rows.sortedWith(compareByDescending<MediaItem> {
            if (it.dateTakenMillis > 0) it.dateTakenMillis else it.dateAddedSeconds * 1000L
        }.thenBy { it.id })
    }

    private fun queryCollection(collection: Uri, video: Boolean, rows: MutableList<MediaItem>) {
        val projectionList = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.RELATIVE_PATH)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            if (video) {
                add(MediaStore.Video.VideoColumns.DURATION)
            }
        }
        val projection = projectionList.toTypedArray()
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
            val takenCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            val durationCol = if (video) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val itemUri = ContentUris.withAppendedId(collection, id)
                val name = cursor.getString(nameCol) ?: "Untitled"
                val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                val added = if (addedCol >= 0) cursor.getLong(addedCol) else 0L
                val taken = if (takenCol >= 0 && !cursor.isNull(takenCol)) cursor.getLong(takenCol) else 0L
                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                val path = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                val duration = if (durationCol >= 0 && !cursor.isNull(durationCol)) cursor.getLong(durationCol) else 0L

                rows.add(
                    MediaItem(
                        id = id,
                        uri = itemUri.toString(),
                        name = name,
                        mimeType = mimeType,
                        dateAddedSeconds = added,
                        dateTakenMillis = taken,
                        sizeBytes = size,
                        width = width,
                        height = height,
                        relativePath = path,
                        isVideo = video,
                        durationMillis = duration
                    )
                )
            }
        }
    }
}
