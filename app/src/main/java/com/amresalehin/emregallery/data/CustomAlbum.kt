package com.amresalehin.emregallery.data

/**
 * Represents a user-created custom album, which can be nested under a parent album or path.
 */
data class CustomAlbum(
    val id: String,
    val name: String,
    val parentPath: String = "",
    val mediaUris: Set<String> = emptySet()
) {
    val fullPath: String
        get() = if (parentPath.isBlank()) name else "$parentPath / $name"
}
