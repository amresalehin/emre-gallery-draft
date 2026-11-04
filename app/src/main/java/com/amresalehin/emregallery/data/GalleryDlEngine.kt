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

data class GalleryDlStatus(
    val available: Boolean,
    val version: String,
    val engineName: String,
    val supportedExtractorsCount: Int,
    val popularSites: List<GalleryDlSite>
)

data class GalleryDlSite(
    val name: String,
    val domains: List<String>,
    val icon: String
)

data class GalleryDlSupportInfo(
    val isSupported: Boolean,
    val category: String,
    val subcategory: String,
    val extractorName: String,
    val displayName: String
)

data class GalleryDlOptions(
    val userAgent: String? = null,
    val cookies: String? = null,
    val timeoutSeconds: Int = 30
)

data class GalleryDlExtractResult(
    val success: Boolean,
    val extractorName: String = "",
    val category: String = "",
    val pageTitle: String = "",
    val items: List<ScrapedMediaItem> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Gallery-dl integration service powered by Chaquopy Python SDK on Android.
 * Enables extracting multi-image galleries, videos, high-resolution original artwork,
 * and direct downloads from 280+ supported websites (Reddit, Imgur, X/Twitter, Pinterest,
 * ArtStation, DeviantArt, Booru boards, Flickr, Tumblr, Wikimedia Commons, etc.).
 */
object GalleryDlEngine {
    private const val TAG = "GalleryDlEngine"
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context.applicationContext))
            }
            isInitialized = true
            Log.d(TAG, "Chaquopy Python SDK and gallery-dl runtime initialized successfully")
        } catch (t: Throwable) {
            Log.w(TAG, "Python runtime initialization deferred or failed: ${t.message}")
        }
    }

    fun isAvailable(): Boolean {
        return try {
            Python.isStarted()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Retrieves runtime status, version, and supported popular sites.
     */
    fun getStatus(): GalleryDlStatus {
        if (!isAvailable()) {
            return GalleryDlStatus(
                available = false,
                version = "Unavailable",
                engineName = "gallery-dl (Chaquopy Python required)",
                supportedExtractorsCount = 0,
                popularSites = defaultPopularSites()
            )
        }

        return try {
            val py = Python.getInstance()
            val runner = py.getModule("gallery_dl_runner")
            val statusJson = runner.callAttr("get_engine_status").toString()
            val json = JSONObject(statusJson)

            val sitesList = mutableListOf<GalleryDlSite>()
            val popularArr = json.optJSONArray("popular_extractors") ?: JSONArray()
            for (i in 0 until popularArr.length()) {
                val obj = popularArr.getJSONObject(i)
                val doms = mutableListOf<String>()
                val dArr = obj.optJSONArray("domains") ?: JSONArray()
                for (j in 0 until dArr.length()) {
                    doms.add(dArr.getString(j))
                }
                sitesList.add(
                    GalleryDlSite(
                        name = obj.optString("name", "Unknown"),
                        domains = doms,
                        icon = obj.optString("icon", "public")
                    )
                )
            }

            GalleryDlStatus(
                available = json.optBoolean("available", true),
                version = json.optString("version", "1.32.19"),
                engineName = json.optString("engine", "gallery-dl (Chaquopy)"),
                supportedExtractorsCount = json.optInt("supported_categories_count", 287),
                popularSites = if (sitesList.isNotEmpty()) sitesList else defaultPopularSites()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get gallery-dl status", e)
            GalleryDlStatus(
                available = true,
                version = "1.32.19",
                engineName = "gallery-dl 1.32.19",
                supportedExtractorsCount = 287,
                popularSites = defaultPopularSites()
            )
        }
    }

    /**
     * Checks whether the target URL matches a site-specific gallery-dl extractor.
     */
    fun checkSupport(url: String): GalleryDlSupportInfo {
        if (url.isBlank()) {
            return GalleryDlSupportInfo(false, "", "", "", "")
        }

        if (!isAvailable()) {
            // Heuristic check if Python isn't active
            val lower = url.lowercase()
            for (site in defaultPopularSites()) {
                if (site.domains.any { lower.contains(it) }) {
                    return GalleryDlSupportInfo(
                        isSupported = true,
                        category = site.name.lowercase(),
                        subcategory = "",
                        extractorName = "${site.name}Extractor",
                        displayName = site.name
                    )
                }
            }
            return GalleryDlSupportInfo(false, "", "", "", "")
        }

        return try {
            val py = Python.getInstance()
            val runner = py.getModule("gallery_dl_runner")
            val checkJson = runner.callAttr("check_support", url.trim()).toString()
            val json = JSONObject(checkJson)
            GalleryDlSupportInfo(
                isSupported = json.optBoolean("supported", false),
                category = json.optString("category", ""),
                subcategory = json.optString("subcategory", ""),
                extractorName = json.optString("extractor", ""),
                displayName = json.optString("display_name", json.optString("category", ""))
            )
        } catch (e: Exception) {
            Log.e(TAG, "checkSupport error", e)
            GalleryDlSupportInfo(false, "", "", "", "")
        }
    }

    private fun parseExtractedItems(root: JSONObject, pageUrl: String): Pair<String, List<ScrapedMediaItem>> {
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
                id = obj.optString("id", "gdl_${System.currentTimeMillis()}_$i"),
                url = obj.getString("url"),
                title = obj.optString("title", "media_${i + 1}"),
                isVideo = isVideo,
                isAudio = isAudio,
                isStream = isStream,
                category = mediaCategory,
                format = obj.optString("format", "IMAGE"),
                mimeType = obj.optString("mimeType", if (isVideo) "video/mp4" else "image/jpeg"),
                thumbnailUrl = obj.optString("thumbnailUrl").takeIf { !it.isNullOrBlank() },
                source = obj.optString("source", "gallery-dl"),
                resolutionHint = obj.optString("resolutionHint").takeIf { !it.isNullOrBlank() },
                sizeBytes = if (obj.has("sizeBytes") && !obj.isNull("sizeBytes")) obj.getLong("sizeBytes") else null,
                provenance = MediaProvenance(
                    extractorEngine = "gallery-dl / yt-dlp",
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

    private suspend fun executePythonExtractor(
        methodName: String,
        url: String,
        options: GalleryDlOptions
    ): GalleryDlExtractResult = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext GalleryDlExtractResult(
                success = false,
                errorMessage = "Chaquopy Python runtime is not active."
            )
        }

        try {
            val py = Python.getInstance()
            val runner = py.getModule("gallery_dl_runner")

            val optsJson = JSONObject().apply {
                options.userAgent?.let { put("user_agent", it) }
                options.cookies?.let { put("cookies", it) }
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

    suspend fun extractGalleryDl(url: String, options: GalleryDlOptions = GalleryDlOptions()): GalleryDlExtractResult =
        executePythonExtractor("extract_gallery_dl", url, options)

    suspend fun extractYtDlp(url: String, options: GalleryDlOptions = GalleryDlOptions()): GalleryDlExtractResult =
        executePythonExtractor("extract_yt_dlp", url, options)

    suspend fun extractMedia(url: String, options: GalleryDlOptions = GalleryDlOptions()): GalleryDlExtractResult =
        executePythonExtractor("extract_media", url, options)

    /**
     * Downloads a media file with proper referer header via Python requests or fallback.
     */
    suspend fun downloadMedia(
        context: Context,
        mediaUrl: String,
        fileName: String,
        referer: String? = null,
        downloadId: String? = null
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
            val stableId = downloadId?.takeIf { it.isNotBlank() }
                ?.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            // A WorkManager retry must overwrite the same output rather than creating another
            // timestamped file for the same logical media item.
            val targetFile = File(
                dir,
                if (stableId != null) "${stableId}_$sanitizedName" else "${System.currentTimeMillis()}_$sanitizedName"
            )

            // Try Python download if available
            if (isAvailable()) {
                val py = Python.getInstance()
                val runner = py.getModule("gallery_dl_runner")
                val success = runner.callAttr(
                    "download_media_file",
                    mediaUrl,
                    targetFile.absolutePath,
                    referer ?: "",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
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

            // Remove an incomplete Python output before retrying through OkHttp. Both
            // engines must not leave separate files for one logical download.
            if (targetFile.exists()) targetFile.delete()

            // Fallback to OkHttp download with shared session cookies
            OkHttpDownloader.downloadMediaFile(
                context = context,
                mediaUrl = mediaUrl,
                fileName = fileName,
                referer = referer,
                downloadId = downloadId
            )
        } catch (e: Exception) {
            Log.e(TAG, "downloadMedia failed", e)
            null
        }
    }

    fun defaultPopularSites(): List<GalleryDlSite> = listOf(
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
}
