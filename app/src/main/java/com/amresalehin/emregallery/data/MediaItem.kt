package com.amresalehin.emregallery.data

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
