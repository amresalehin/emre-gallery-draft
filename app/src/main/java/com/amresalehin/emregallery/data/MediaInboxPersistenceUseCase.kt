package com.amresalehin.emregallery.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File


internal fun MediaInboxRepository.loadStacks() {
    val rawJson = prefs.getString(MediaInboxRepository.KEY_STACKS_JSON, null)
    if (rawJson.isNullOrBlank()) {
        _stacks.value = emptyList()
        return
    }

    try {
        val jsonArray = JSONArray(rawJson)
        val list = mutableListOf<MediaInboxStack>()
        for (i in 0 until jsonArray.length()) {
            val stackObj = jsonArray.getJSONObject(i)
            val id = stackObj.getString("id")
            val title = stackObj.optString("title", "Untitled Stack")
            val sourceDomain = stackObj.optString("sourceDomain", "")
            val sourceUrl = stackObj.optString("sourceUrl", "")
            val timestamp = stackObj.optLong("timestampMillis", System.currentTimeMillis())
            val description = stackObj.optString("description", "")

            val itemsArray = stackObj.optJSONArray("items") ?: JSONArray()
            val itemsList = mutableListOf<MediaInboxItem>()
            for (j in 0 until itemsArray.length()) {
                val itmObj = itemsArray.getJSONObject(j)
                itemsList.add(
                    MediaInboxItem(
                        id = itmObj.getString("id"),
                        title = itmObj.optString("title", "Media"),
                        localUri = itmObj.getString("localUri"),
                        sourceUrl = itmObj.optString("sourceUrl", ""),
                        isVideo = itmObj.optBoolean("isVideo", false),
                        isAudio = itmObj.optBoolean("isAudio", false),
                        isStream = itmObj.optBoolean("isStream", false),
                        format = itmObj.optString("format", ""),
                        sizeBytes = itmObj.optLong("sizeBytes", 0L),
                        dateAddedMillis = itmObj.optLong("dateAddedMillis", timestamp)
                    )
                )
            }

            list.add(
                MediaInboxStack(
                    id = id,
                    title = title,
                    sourceDomain = sourceDomain,
                    sourceUrl = sourceUrl,
                    items = itemsList,
                    timestampMillis = timestamp,
                    description = description
                )
            )
        }
        val consolidated = consolidateStacks(list)
        _stacks.value = consolidated
        if (consolidated.size != list.size) {
            persist()
        }
    } catch (e: Exception) {
        _stacks.value = emptyList()
    }
}

internal fun MediaInboxRepository.consolidateStacks(rawList: List<MediaInboxStack>): List<MediaInboxStack> {
    if (rawList.size <= 1) return rawList
    val merged = mutableListOf<MediaInboxStack>()
    for (stack in rawList) {
        val existingIndex = merged.indexOfFirst { existing ->
            when {
                existing.id == stack.id -> true
                existing.sourceUrl.isNotBlank() && stack.sourceUrl.isNotBlank() && existing.sourceUrl == stack.sourceUrl -> true
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
                sourceUrl = if (existing.sourceUrl.isNotBlank()) existing.sourceUrl else stack.sourceUrl,
                sourceDomain = if (existing.sourceDomain.isNotBlank()) existing.sourceDomain else stack.sourceDomain
            )
        } else {
            merged.add(stack)
        }
    }
    return merged
}

internal fun MediaInboxRepository.persist() {
    try {
        val jsonArray = JSONArray()
        _stacks.value.forEach { stack ->
            val stackObj = JSONObject().apply {
                put("id", stack.id)
                put("title", stack.title)
                put("sourceDomain", stack.sourceDomain)
                put("sourceUrl", stack.sourceUrl)
                put("timestampMillis", stack.timestampMillis)
                put("description", stack.description)

                val itemsArr = JSONArray()
                stack.items.forEach { itm ->
                    val itmObj = JSONObject().apply {
                        put("id", itm.id)
                        put("title", itm.title)
                        put("localUri", itm.localUri)
                        put("sourceUrl", itm.sourceUrl)
                        put("isVideo", itm.isVideo)
                        put("isAudio", itm.isAudio)
                        put("isStream", itm.isStream)
                        put("format", itm.format)
                        put("sizeBytes", itm.sizeBytes)
                        put("dateAddedMillis", itm.dateAddedMillis)
                    }
                    itemsArr.put(itmObj)
                }
                put("items", itemsArr)
            }
            jsonArray.put(stackObj)
        }
        prefs.edit().putString(MediaInboxRepository.KEY_STACKS_JSON, jsonArray.toString()).apply()
    } catch (e: Exception) {
        // Log or ignore
    }
}

