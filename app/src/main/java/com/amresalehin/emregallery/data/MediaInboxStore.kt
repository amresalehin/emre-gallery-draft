package com.amresalehin.emregallery.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles SharedPreferences JSON persistence and deserialization for [MediaInboxStack]s.
 */
internal class MediaInboxStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<MediaInboxStack> {
        val rawJson = prefs.getString(KEY_STACKS_JSON, null)
        if (rawJson.isNullOrBlank()) return emptyList()

        return try {
            val jsonArray = JSONArray(rawJson)
            val list = ArrayList<MediaInboxStack>(jsonArray.length())
            for (i in 0 until jsonArray.length()) {
                list.add(decodeStack(jsonArray.getJSONObject(i)))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(stacks: List<MediaInboxStack>) {
        try {
            val jsonArray = JSONArray()
            stacks.forEach { stack ->
                jsonArray.put(encodeStack(stack))
            }
            prefs.edit().putString(KEY_STACKS_JSON, jsonArray.toString()).apply()
        } catch (_: Exception) {
            // Ignore persistence errors
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_STACKS_JSON).apply()
    }

    private fun decodeStack(stackObj: JSONObject): MediaInboxStack {
        val timestamp = stackObj.optLong("timestampMillis", System.currentTimeMillis())
        val itemsArray = stackObj.optJSONArray("items") ?: JSONArray()
        val itemsList = ArrayList<MediaInboxItem>(itemsArray.length())
        for (j in 0 until itemsArray.length()) {
            itemsList.add(decodeItem(itemsArray.getJSONObject(j), timestamp))
        }

        return MediaInboxStack(
            id = stackObj.getString("id"),
            title = stackObj.optString("title", "Untitled Stack"),
            sourceDomain = stackObj.optString("sourceDomain", ""),
            sourceUrl = stackObj.optString("sourceUrl", ""),
            items = itemsList,
            timestampMillis = timestamp,
            description = stackObj.optString("description", "")
        )
    }

    private fun decodeItem(itmObj: JSONObject, fallbackTimestamp: Long): MediaInboxItem {
        return MediaInboxItem(
            id = itmObj.getString("id"),
            title = itmObj.optString("title", "Media"),
            localUri = itmObj.getString("localUri"),
            sourceUrl = itmObj.optString("sourceUrl", ""),
            isVideo = itmObj.optBoolean("isVideo", false),
            isAudio = itmObj.optBoolean("isAudio", false),
            isStream = itmObj.optBoolean("isStream", false),
            format = itmObj.optString("format", ""),
            sizeBytes = itmObj.optLong("sizeBytes", 0L),
            dateAddedMillis = itmObj.optLong("dateAddedMillis", fallbackTimestamp)
        )
    }

    private fun encodeStack(stack: MediaInboxStack): JSONObject {
        return JSONObject().apply {
            put("id", stack.id)
            put("title", stack.title)
            put("sourceDomain", stack.sourceDomain)
            put("sourceUrl", stack.sourceUrl)
            put("timestampMillis", stack.timestampMillis)
            put("description", stack.description)

            val itemsArr = JSONArray()
            stack.items.forEach { itm ->
                itemsArr.put(encodeItem(itm))
            }
            put("items", itemsArr)
        }
    }

    private fun encodeItem(itm: MediaInboxItem): JSONObject {
        return JSONObject().apply {
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
    }

    private companion object {
        const val PREFS_NAME = "media_inbox_prefs"
        const val KEY_STACKS_JSON = "inbox_stacks_json"
    }
}
