package com.amresalehin.emregallery.pocket

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.galleryDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class PocketRepository(private val context: Context) {

    private val pocketKey = stringPreferencesKey("pocket_items_data_v1")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val items: Flow<List<PocketItem>> = context.galleryDataStore.data.map { prefs ->
        val raw = prefs[pocketKey]
        if (raw.isNullOrBlank()) {
            getDefaultSeedItems()
        } else {
            try {
                deserializeItems(raw).ifEmpty { getDefaultSeedItems() }
            } catch (_: Exception) {
                getDefaultSeedItems()
            }
        }
    }.distinctUntilChanged()

    suspend fun addItem(item: PocketItem) {
        val current = getCurrentItemsList().toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        saveItems(current)
    }

    suspend fun updateItem(item: PocketItem) {
        val current = getCurrentItemsList().toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index != -1) {
            current[index] = item
            saveItems(current)
        } else {
            addItem(item)
        }
    }

    suspend fun deleteItem(id: String): PocketItem? {
        val current = getCurrentItemsList().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        return if (index != -1) {
            val removed = current.removeAt(index)
            saveItems(current)
            removed
        } else {
            null
        }
    }

    suspend fun saveFromUrl(
        urlInput: String,
        customTitle: String? = null,
        userNote: String = "",
        customTags: List<String> = emptyList()
    ): PocketItem = withContext(Dispatchers.IO) {
        val normalizedUrl = if (!urlInput.startsWith("http://") && !urlInput.startsWith("https://")) {
            "https://$urlInput"
        } else {
            urlInput
        }
        val domain = PocketItem.extractDomain(normalizedUrl)

        var title = customTitle.orEmpty()
        var description = ""
        val extractedImages = mutableListOf<String>()

        // Try fetching lightweight web metadata
        try {
            val req = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) EmreGallery/1.0")
                .build()
            val response = httpClient.newCall(req).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (title.isBlank()) {
                    val titleMatcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(body)
                    if (titleMatcher.find()) {
                        title = titleMatcher.group(1)?.trim()?.replace("&amp;", "&")?.replace("&#39;", "'") ?: ""
                    }
                }
                val descMatcher = Pattern.compile("<meta\\s+(?:name|property)=[\"'](?:description|og:description)[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE).matcher(body)
                if (descMatcher.find()) {
                    description = descMatcher.group(1)?.trim() ?: ""
                }
                val imgMatcher = Pattern.compile("<meta\\s+property=[\"']og:image[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE).matcher(body)
                if (imgMatcher.find()) {
                    val img = imgMatcher.group(1)?.trim()
                    if (!img.isNullOrBlank()) extractedImages.add(img)
                }
            }
        } catch (_: Exception) {
            // Network fallback or offline
        }

        if (title.isBlank()) {
            title = normalizedUrl.substringAfter("://").substringAfter("/").substringBefore("?").replace("-", " ").replace("_", " ").trim()
            if (title.isBlank() || title.length < 3) {
                title = domain.replaceFirstChar { it.uppercase() }
            } else {
                title = title.replaceFirstChar { it.uppercase() }
            }
        }

        val tags = if (customTags.isNotEmpty()) {
            customTags
        } else {
            listOf(domain.substringBefore(".").replaceFirstChar { it.uppercase() }, "Saved")
        }

        val colorPresets = listOf("#7986CB", "#FF8A50", "#26A69A", "#BA68C8", "#42A5F5", "#AB47BC")
        val chosenColor = colorPresets[(normalizedUrl.hashCode() and 0x7FFFFFFF) % colorPresets.size]

        val newItem = PocketItem(
            id = UUID.randomUUID().toString(),
            title = title,
            url = normalizedUrl,
            domain = domain,
            imageUrls = extractedImages,
            timestamp = System.currentTimeMillis(),
            note = userNote,
            tags = tags,
            description = description.ifBlank { "Article saved from $domain." },
            themeColorHex = chosenColor
        )
        addItem(newItem)
        newItem
    }

    suspend fun saveFromGalleryMedia(
        mediaItem: MediaItem,
        customTitle: String? = null,
        note: String = "",
        tags: List<String> = listOf("Gallery", "Photo")
    ): PocketItem {
        val title = customTitle?.takeIf { it.isNotBlank() } ?: mediaItem.name.substringBeforeLast(".")
        val item = PocketItem(
            id = UUID.randomUUID().toString(),
            title = title,
            url = mediaItem.uri,
            domain = "emregallery.local",
            imageUrls = listOf(mediaItem.uri),
            timestamp = when {
                mediaItem.dateTakenMillis > 0L -> mediaItem.dateTakenMillis
                mediaItem.dateAddedSeconds > 0L -> mediaItem.dateAddedSeconds * 1000L
                else -> System.currentTimeMillis()
            },
            note = note.ifBlank { "Saved from local device camera & gallery collection." },
            tags = tags,
            description = "High resolution photo from device library (${mediaItem.width} × ${mediaItem.height} px, ${mediaItem.mimeType}).",
            themeColorHex = "#7986CB"
        )
        addItem(item)
        return item
    }

    private suspend fun getCurrentItemsList(): List<PocketItem> {
        val prefs = context.galleryDataStore.data.first()
        val raw = prefs[pocketKey]
        return if (raw.isNullOrBlank()) {
            getDefaultSeedItems()
        } else {
            try {
                deserializeItems(raw).ifEmpty { getDefaultSeedItems() }
            } catch (_: Exception) {
                getDefaultSeedItems()
            }
        }
    }

    private suspend fun saveItems(list: List<PocketItem>) {
        val jsonArray = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
                put("domain", item.domain)
                put("timestamp", item.timestamp)
                put("note", item.note)
                put("description", item.description)
                put("themeColorHex", item.themeColorHex)
                put("isFavorite", item.isFavorite)
                put("isRead", item.isRead)

                val imgArr = JSONArray()
                item.imageUrls.forEach { imgArr.put(it) }
                put("imageUrls", imgArr)

                val tagArr = JSONArray()
                item.tags.forEach { tagArr.put(it) }
                put("tags", tagArr)
            }
            jsonArray.put(obj)
        }
        context.galleryDataStore.edit { it[pocketKey] = jsonArray.toString() }
    }

    private fun deserializeItems(rawJson: String): List<PocketItem> {
        val array = JSONArray(rawJson)
        val list = mutableListOf<PocketItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val imgArr = obj.optJSONArray("imageUrls")
            val imgs = mutableListOf<String>()
            if (imgArr != null) {
                for (j in 0 until imgArr.length()) imgs.add(imgArr.getString(j))
            }

            val tagArr = obj.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagArr != null) {
                for (j in 0 until tagArr.length()) tags.add(tagArr.getString(j))
            }

            list.add(
                PocketItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", "Untitled"),
                    url = obj.optString("url", ""),
                    domain = obj.optString("domain", "web"),
                    imageUrls = imgs,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    note = obj.optString("note", ""),
                    tags = tags,
                    description = obj.optString("description", ""),
                    themeColorHex = obj.optString("themeColorHex", "#7986CB"),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    isRead = obj.optBoolean("isRead", false)
                )
            )
        }
        return list
    }

    companion object {
        fun getDefaultSeedItems(): List<PocketItem> {
            return listOf(
                PocketItem(
                    id = "seed-taj-mahal",
                    title = "Taj Mahal",
                    url = "https://en.wikipedia.org/wiki/Taj_Mahal",
                    domain = "wikipedia.org",
                    imageUrls = listOf(
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Taj_Mahal_%28Edited%29.jpeg/800px-Taj_Mahal_%28Edited%29.jpeg",
                        "https://images.unsplash.com/photo-1564507592333-c60657eea523?auto=format&fit=crop&w=1200&q=80",
                        "https://images.unsplash.com/photo-1548013146-72479768bada?auto=format&fit=crop&w=1200&q=80"
                    ),
                    timestamp = 1793816640000L, // Nov 04, 2026 · 10:24 AM
                    note = "Commissioned in 1631 by the fifth Mughal emperor Shah Jahan to house the tomb of his favourite wife, Mumtaz Mahal.",
                    tags = listOf("Architecture", "UNESCO", "Monument", "India"),
                    description = "The Taj Mahal is an ivory-white marble mausoleum on the south bank of the Yamuna river in the Indian city of Agra. It was commissioned in 1631 by Mughal emperor Shah Jahan to house the tomb of his favourite wife, Mumtaz Mahal; it also houses the tomb of Shah Jahan himself. The tomb is the centrepiece of a 17-hectare (42-acre) complex, which includes a mosque and a guest house, and is set in formal gardens bounded on three sides by a crenellated wall.",
                    themeColorHex = "#7986CB"
                ),
                PocketItem(
                    id = "seed-ancient-india",
                    title = "Ancient India",
                    url = "https://www.britannica.com/place/ancient-India",
                    domain = "britannica.com",
                    imageUrls = listOf(
                        "https://images.unsplash.com/photo-1599661046289-e31897846e41?auto=format&fit=crop&w=1200&q=80",
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b3/Mohenjo-daro.jpg/800px-Mohenjo-daro.jpg"
                    ),
                    timestamp = 1793226960000L, // Oct 28, 2026 · 5:16 PM
                    note = "Study of the Bronze Age Indus Valley Civilization, urban drainage systems, brick kilns, and early trade routes.",
                    tags = listOf("History", "Archaeology", "Civilization", "Harappa"),
                    description = "Ancient India encompasses the prehistoric roots of the subcontinent through the urban maturity of the Indus Valley Civilization (c. 2500–1700 BCE), the composition of Vedic hymns, and the emergence of early state polities. Archaeological discoveries at Harappa and Mohenjo-daro illustrate grid-planned streets, sophisticated hydraulic sanitation, and flourishing artistic crafts.",
                    themeColorHex = "#FF8A50"
                ),
                PocketItem(
                    id = "seed-mughal-art",
                    title = "Mughal Art",
                    url = "https://artsandculture.google.com/story/mughal-art",
                    domain = "google.com",
                    imageUrls = listOf(
                        "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=1200&q=80",
                        "https://images.unsplash.com/photo-1461360370896-922624d12aa1?auto=format&fit=crop&w=1200&q=80"
                    ),
                    timestamp = 1791835680000L, // Oct 12, 2026 · 2:48 PM
                    note = "Miniatures painted with ground lapis lazuli, gold leaf leafing, and fine squirrel-hair brushes illustrating dynastic histories.",
                    tags = listOf("Art", "Manuscripts", "Calligraphy", "Heritage"),
                    description = "Mughal art is a particular style of South Asian painting, generally confined to miniatures either as book illustrations or as single works to be kept in albums (muraqqa). It emerged from the Persian miniature painting tradition with additional Hindu, Buddhist, and Jain influences and developed largely in the court of the Mughal Empire of the 16th to 18th centuries.",
                    themeColorHex = "#26A69A"
                ),
                PocketItem(
                    id = "seed-indian-architecture",
                    title = "Indian Architecture",
                    url = "https://www.researchgate.net/publication/indian_architecture_evolution",
                    domain = "researchgate.net",
                    imageUrls = listOf(
                        "https://images.unsplash.com/photo-1587474260584-136574528ed5?auto=format&fit=crop&w=1200&q=80",
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/Fatehpur_Sikri_Panch_Mahal.jpg/800px-Fatehpur_Sikri_Panch_Mahal.jpg"
                    ),
                    timestamp = 1790795220000L, // Sep 30, 2026 · 11:07 AM
                    note = "Evolution of corbelled and true arches, geometric jali fretwork filters, chhatri pavilions, and courtyards.",
                    tags = listOf("Architecture", "Arches", "Heritage", "Design"),
                    description = "Indian architecture is rooted in its history, culture and religion. Among a number of architectural styles and traditions, the best-known include the many varieties of Hindu temple architecture and Indo-Islamic architecture, especially Rajput architecture, Mughal architecture, South Indian Dravidian architecture, and Indo-Saracenic architecture.",
                    themeColorHex = "#BA68C8"
                )
            )
        }
    }
}
