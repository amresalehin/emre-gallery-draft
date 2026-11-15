package com.amresalehin.emregallery.data

/**
 * Single persisted media file belonging to an inbox download stack.
 */
data class MediaInboxItem(
    val id: String,
    val title: String,
    val localUri: String,
    val sourceUrl: String,
    val isVideo: Boolean,
    val isAudio: Boolean = false,
    val isStream: Boolean = false,
    val format: String = "",
    val sizeBytes: Long = 0L,
    val dateAddedMillis: Long = System.currentTimeMillis()
)

/**
 * Grouped collection of downloaded media originating from a single page/session.
 */
data class MediaInboxStack(
    val id: String,
    val title: String,
    val sourceDomain: String,
    val sourceUrl: String,
    val items: List<MediaInboxItem>,
    val timestampMillis: Long = System.currentTimeMillis(),
    val description: String = ""
)
