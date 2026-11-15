package com.amresalehin.emregallery.data

/**
 * Pure domain logic for consolidating duplicate [MediaInboxStack]s and upserting
 * [MediaInboxItem]s into stacks while surfacing replaced items for disk cleanup.
 */
internal object MediaInboxConsolidator {

    data class UpsertResult(
        val updatedStacks: List<MediaInboxStack>,
        val replacedItems: List<MediaInboxItem>
    )

    fun consolidateStacks(rawList: List<MediaInboxStack>): List<MediaInboxStack> {
        if (rawList.size <= 1) return rawList
        val merged = mutableListOf<MediaInboxStack>()
        for (stack in rawList) {
            val existingIndex = merged.indexOfFirst { existing ->
                when {
                    existing.id == stack.id -> true
                    existing.sourceUrl.isNotBlank() &&
                        stack.sourceUrl.isNotBlank() &&
                        existing.sourceUrl == stack.sourceUrl -> true
                    else -> false
                }
            }
            if (existingIndex >= 0) {
                val existing = merged[existingIndex]
                val existingKeys = existing.items.map { it.id to it.localUri }.toSet()
                val newItems = stack.items.filter { (it.id to it.localUri) !in existingKeys }
                merged[existingIndex] = existing.copy(
                    items = existing.items + newItems,
                    timestampMillis = maxOf(existing.timestampMillis, stack.timestampMillis),
                    sourceUrl = existing.sourceUrl.ifBlank { stack.sourceUrl },
                    sourceDomain = existing.sourceDomain.ifBlank { stack.sourceDomain }
                )
            } else {
                merged.add(stack)
            }
        }
        return merged
    }

    fun upsertItem(
        currentStacks: List<MediaInboxStack>,
        stackId: String,
        item: MediaInboxItem,
        stackTitle: String,
        sourceDomain: String,
        sourceUrl: String
    ): UpsertResult {
        val current = currentStacks.toMutableList()
        var existingIndex = current.indexOfFirst { it.id == stackId }
        if (existingIndex < 0 && sourceUrl.isNotBlank()) {
            existingIndex = current.indexOfFirst { it.sourceUrl == sourceUrl }
        }

        val replacedItems: List<MediaInboxItem>
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            replacedItems = existing.items.filter {
                (it.id == item.id || (it.sourceUrl.isNotBlank() && it.sourceUrl == item.sourceUrl)) &&
                    it.localUri != item.localUri
            }
            val updatedItems = existing.items.filter {
                it.id != item.id &&
                    (it.sourceUrl.isBlank() || it.sourceUrl != item.sourceUrl) &&
                    it.localUri != item.localUri
            } + item
            current[existingIndex] = existing.copy(
                items = updatedItems,
                timestampMillis = System.currentTimeMillis()
            )
        } else {
            replacedItems = emptyList()
            val newStack = MediaInboxStack(
                id = stackId.ifBlank { "stack_${System.currentTimeMillis()}" },
                title = stackTitle.ifBlank { sourceDomain.ifBlank { "Discovered Media" } },
                sourceDomain = sourceDomain,
                sourceUrl = sourceUrl,
                items = listOf(item),
                timestampMillis = System.currentTimeMillis()
            )
            current.add(0, newStack)
        }

        return UpsertResult(
            updatedStacks = consolidateStacks(current),
            replacedItems = replacedItems
        )
    }
}
