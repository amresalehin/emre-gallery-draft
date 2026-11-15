package com.amresalehin.emregallery.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates inbox download stacks state, delegating JSON persistence,
 * stack consolidation/upsertion, and local file cleanup to focused collaborators.
 */
class MediaInboxRepository(private val context: Context) {

    private val store = MediaInboxStore(context)

    private val _stacks = MutableStateFlow<List<MediaInboxStack>>(emptyList())
    val stacks: StateFlow<List<MediaInboxStack>> = _stacks.asStateFlow()

    init {
        loadStacks()
    }

    private fun loadStacks() {
        val loaded = store.load()
        val consolidated = MediaInboxConsolidator.consolidateStacks(loaded)
        _stacks.value = consolidated
        if (consolidated.size != loaded.size) {
            store.save(consolidated)
        }
    }

    fun addStack(stack: MediaInboxStack) {
        val updated = listOf(stack) + _stacks.value.filter { it.id != stack.id }
        val consolidated = MediaInboxConsolidator.consolidateStacks(updated)
        _stacks.value = consolidated
        store.save(consolidated)
    }

    @Synchronized
    fun addItemToStack(
        stackId: String,
        item: MediaInboxItem,
        stackTitle: String,
        sourceDomain: String,
        sourceUrl: String
    ) {
        val upsertResult = MediaInboxConsolidator.upsertItem(
            currentStacks = _stacks.value,
            stackId = stackId,
            item = item,
            stackTitle = stackTitle,
            sourceDomain = sourceDomain,
            sourceUrl = sourceUrl
        )
        MediaInboxFileCleaner.deleteItems(upsertResult.replacedItems)
        _stacks.value = upsertResult.updatedStacks
        store.save(upsertResult.updatedStacks)
    }

    fun deleteStack(stackId: String) {
        val stack = _stacks.value.firstOrNull { it.id == stackId }
        if (stack != null) {
            MediaInboxFileCleaner.deleteItems(
                items = stack.items,
                requireFileSchemeOrAbsPath = true
            )
        }
        val remaining = _stacks.value.filter { it.id != stackId }
        _stacks.value = remaining
        store.save(remaining)
    }

    fun clearAll() {
        MediaInboxFileCleaner.deleteStacks(_stacks.value)
        _stacks.value = emptyList()
        store.clear()
    }

    companion object {
        @Volatile
        private var INSTANCE: MediaInboxRepository? = null

        fun getInstance(context: Context): MediaInboxRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaInboxRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
