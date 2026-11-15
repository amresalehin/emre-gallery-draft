package com.amresalehin.emregallery.data

import java.io.File

/**
 * Deletes orphaned or removed local media files backing [MediaInboxItem]s on disk.
 */
internal object MediaInboxFileCleaner {

    fun deleteItems(items: List<MediaInboxItem>, requireFileSchemeOrAbsPath: Boolean = false) {
        items.forEach { item ->
            deleteLocalUri(
                localUri = item.localUri,
                requireFileSchemeOrAbsPath = requireFileSchemeOrAbsPath
            )
        }
    }

    fun deleteStacks(stacks: List<MediaInboxStack>) {
        stacks.forEach { stack ->
            deleteItems(stack.items, requireFileSchemeOrAbsPath = false)
        }
    }

    private fun deleteLocalUri(localUri: String, requireFileSchemeOrAbsPath: Boolean) {
        if (requireFileSchemeOrAbsPath &&
            !localUri.startsWith("file://") &&
            !localUri.startsWith("/")
        ) {
            return
        }
        runCatching {
            val path = localUri.removePrefix("file://")
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
