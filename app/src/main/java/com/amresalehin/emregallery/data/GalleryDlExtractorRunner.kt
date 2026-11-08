package com.amresalehin.emregallery.data

import android.content.Context
import android.os.Environment
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL


internal fun GalleryDlEngine.parseExtractedItems(root: JSONObject, pageUrl: String): Pair<String, List<ScrapedMediaItem>> {
    val pageTitle = root.optString("pageTitle", "")
    val itemsArray = root.optJSONArray("items") ?: JSONArray()
    val itemsList = mutableListOf<ScrapedMediaItem>()

    for (i in 0 until itemsArray.length()) {
        val obj = itemsArray.getJSONObject(i)
        val catStr = obj.optString("category", "IMAGES")
        val mediaCategory = when (catStr.uppercase()) {
            "VIDEOS" -> MediaCategory.VIDEOS
            "AUDIO" -> MediaCategory.AUDIO
            "STREAMS" -> MediaCategory.STREAMS
            else -> MediaCategory.IMAGES
        }

        val isVideo = obj.optBoolean("isVideo", false)
        val isAudio = obj.optBoolean("isAudio", false)
        val isStream = obj.optBoolean("isStream", false)

        val item = ScrapedMediaItem(
            id = obj.optString("id", "dl_${System.currentTimeMillis()}_$i"),
            url = obj.getString("url"),
            title = obj.optString("title", "media_${i + 1}"),
            isVideo = isVideo,
            isAudio = isAudio,
            isStream = isStream,
            category = mediaCategory,
            format = obj.optString("format", "IMAGE"),
            mimeType = obj.optString("mimeType", if (isVideo) "video/mp4" else "image/jpeg"),
            thumbnailUrl = obj.optString("thumbnailUrl").takeIf { !it.isNullOrBlank() },
            source = obj.optString("source", "yt-dlp"),
            resolutionHint = obj.optString("resolutionHint").takeIf { !it.isNullOrBlank() },
            sizeBytes = if (obj.has("sizeBytes") && !obj.isNull("sizeBytes")) obj.getLong("sizeBytes") else null,
            provenance = MediaProvenance(
                extractorEngine = obj.optString("source", "yt-dlp / gallery-dl"),
                pageUrl = pageUrl
            ),
            metadata = mapOf("source_url" to pageUrl),
            downloadHeaders = mapOf(
                "User-Agent" to OkHttpDownloader.DEFAULT_USER_AGENT,
                "Referer" to pageUrl
            )
        )
        itemsList.add(item)
    }
    return Pair(pageTitle, itemsList)
}

internal suspend fun GalleryDlEngine.executePythonExtractor(
    methodName: String,
    url: String,
    options: GalleryDlOptions
): GalleryDlExtractResult = withContext(Dispatchers.IO) {
    if (!isAvailable()) {
        var waitCount = 0
        while (!isAvailable() && waitCount < 10) {
            kotlinx.coroutines.delay(100L)
            waitCount++
        }
    }
    if (!isAvailable()) {
        return@withContext GalleryDlExtractResult(
            success = false,
            errorMessage = "Chaquopy Python runtime is not active."
        )
    }

    try {
        val py = Python.getInstance()
        val runner = py.getModule("gallery_dl_runner")

        val effectiveUa = options.userAgent ?: BotDetectionMitigator.STEALTH_USER_AGENT
        val effectiveCookies = options.cookies
            ?: runCatching { android.webkit.CookieManager.getInstance().getCookie(url.trim()) }.getOrNull()

        val optsJson = JSONObject().apply {
            put("user_agent", effectiveUa)
            effectiveCookies?.let { put("cookies", it) }
            put("timeout", options.timeoutSeconds)
        }.toString()

        val rawResult = runner.callAttr(methodName, url.trim(), optsJson).toString()
        val root = JSONObject(rawResult)

        val success = root.optBoolean("success", false)
        if (!success) {
            val err = root.optString("error", "Extraction failed or produced no items")
            return@withContext GalleryDlExtractResult(
                success = false,
                errorMessage = err
            )
        }

        val extractorName = root.optString("extractor", root.optString("engine", methodName))
        val (pageTitle, itemsList) = parseExtractedItems(root, url.trim())

        GalleryDlExtractResult(
            success = true,
            extractorName = extractorName,
            pageTitle = pageTitle,
            items = itemsList
        )
    } catch (e: Exception) {
        Log.e(TAG, "$methodName execution failed", e)
        GalleryDlExtractResult(
            success = false,
            errorMessage = e.localizedMessage ?: "$methodName execution failed"
        )
    }
}

internal suspend fun GalleryDlEngine.extractGalleryDlImpl(url: String, options: GalleryDlOptions = GalleryDlOptions()): GalleryDlExtractResult =
    executePythonExtractor("extract_gallery_dl", url, options)

internal suspend fun GalleryDlEngine.extractYtDlpImpl(url: String, options: GalleryDlOptions = GalleryDlOptions()): GalleryDlExtractResult =
    executePythonExtractor("extract_yt_dlp", url, options)

internal suspend fun GalleryDlEngine.extractMediaImpl(url: String, options: GalleryDlOptions = GalleryDlOptions()): GalleryDlExtractResult =
    executePythonExtractor("extract_media", url, options)

/**
 * Downloads a media file exclusively using yt-dlp (primary) and gallery-dl (secondary fallback).
 */
internal suspend fun GalleryDlEngine.downloadMediaImpl(
    context: Context,
    mediaUrl: String,
    fileName: String,
    referer: String? = null,
    headers: Map<String, String>? = null,
    cookies: String? = null
): File? = withContext(Dispatchers.IO) {
    try {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val emreGalleryDir = File(publicDownloads, "Emre Gallery")
        if (!emreGalleryDir.exists()) {
            emreGalleryDir.mkdirs()
        }
        val dir = if (emreGalleryDir.exists() && emreGalleryDir.canWrite()) {
            emreGalleryDir
        } else {
            val appExternal = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val fallbackFolder = File(appExternal ?: context.filesDir, "Emre Gallery")
            if (!fallbackFolder.exists()) fallbackFolder.mkdirs()
            fallbackFolder
        }

        val sanitizedName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val targetFile = File(dir, "${System.currentTimeMillis()}_$sanitizedName")

        val userAgent = headers?.get("User-Agent") ?: BotDetectionMitigator.STEALTH_USER_AGENT
        val effectiveCookies = cookies ?: headers?.get("Cookie")
            ?: runCatching {
                val cm = android.webkit.CookieManager.getInstance()
                cm.getCookie(mediaUrl) ?: referer?.let { cm.getCookie(it) }
            }.getOrNull()
        val headersJson = if (!headers.isNullOrEmpty()) {
            val json = JSONObject()
            headers.forEach { (k, v) -> json.put(k, v) }
            json.toString()
        } else ""

        if (isAvailable()) {
            val py = Python.getInstance()
            val runner = py.getModule("gallery_dl_runner")
            val success = runner.callAttr(
                "download_media_file",
                mediaUrl,
                targetFile.absolutePath,
                referer ?: "",
                userAgent,
                effectiveCookies ?: "",
                headersJson
            ).toBoolean()

            if (success && targetFile.exists() && targetFile.length() > 0L) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    null,
                    null
                )
                return@withContext targetFile
            }
        }

        // Exclusively handled by yt-dlp & gallery-dl. Return null if both failed.
        null
    } catch (e: Exception) {
        Log.e(TAG, "downloadMedia failed", e)
        null
    }
}

internal fun GalleryDlEngine.defaultPopularSitesImpl(): List<GalleryDlSite> = listOf(
    GalleryDlSite("Reddit", listOf("reddit.com", "redd.it", "i.redd.it", "v.redd.it"), "reddit"),
    GalleryDlSite("Imgur", listOf("imgur.com", "i.imgur.com"), "image"),
    GalleryDlSite("Twitter / X", listOf("twitter.com", "x.com", "pbs.twimg.com"), "tag"),
    GalleryDlSite("Pinterest", listOf("pinterest.com", "i.pinimg.com"), "pin"),
    GalleryDlSite("ArtStation", listOf("artstation.com"), "palette"),
    GalleryDlSite("DeviantArt", listOf("deviantart.com"), "brush"),
    GalleryDlSite("Danbooru", listOf("donmai.us", "gelbooru.com", "safebooru.org", "yande.re"), "collections"),
    GalleryDlSite("Flickr", listOf("flickr.com"), "photo_camera"),
    GalleryDlSite("Tumblr", listOf("tumblr.com"), "feed"),
    GalleryDlSite("Wikimedia", listOf("wikimedia.org", "wikipedia.org"), "public"),
    GalleryDlSite("Bluesky", listOf("bsky.app"), "cloud"),
    GalleryDlSite("500px", listOf("500px.com"), "camera_alt"),
    GalleryDlSite("Behance", listOf("behance.net"), "design_services"),
    GalleryDlSite("Bilibili", listOf("bilibili.com"), "smart_display"),
    GalleryDlSite("4chan", listOf("4chan.org", "4channel.org", "i.4cdn.org"), "forum")
)
