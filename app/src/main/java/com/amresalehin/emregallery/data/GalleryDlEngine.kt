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
    val displayName: String,
    val extractorEngine: String = "yt-dlp"
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
 * Gallery-dl and yt-dlp integration service powered by Chaquopy Python SDK on Android.
 * yt-dlp is the primary engine; gallery-dl serves as secondary for galleries and image hosts.
 */
object GalleryDlEngine {
    internal const val TAG = "GalleryDlEngine"
    @Volatile
    private var isInitialized = false
    private val initLock = Any()

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(initLock) {
            if (isInitialized) return
            try {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context.applicationContext))
                }
                isInitialized = true
                Log.d(TAG, "Chaquopy Python SDK and yt-dlp/gallery-dl runtime initialized successfully")
            } catch (t: Throwable) {
                Log.w(TAG, "Python runtime initialization deferred or failed: ${t.message}")
            }
        }
    }

    fun isPythonStarted(): Boolean {
        return try {
            Python.isStarted()
        } catch (_: Throwable) {
            false
        }
    }

    fun isYtDlpAvailable(): Boolean {
        if (!isPythonStarted()) return false
        return try {
            val py = Python.getInstance()
            val runner = py.getModule("gallery_dl_runner")
            runner.callAttr("is_ytdlp_available").toBoolean()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed checking yt-dlp availability", e)
            false
        }
    }

    fun isGalleryDlAvailable(): Boolean {
        if (!isPythonStarted()) return false
        return try {
            val py = Python.getInstance()
            val runner = py.getModule("gallery_dl_runner")
            runner.callAttr("is_gallery_dl_available").toBoolean()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed checking gallery-dl availability", e)
            false
        }
    }

    fun isAvailable(): Boolean {
        return isPythonStarted() && (isYtDlpAvailable() || isGalleryDlAvailable())
    }

    /**
     * Retrieves runtime status, version, and supported popular sites.
     */
    fun getStatus(): GalleryDlStatus {
        if (!isAvailable()) {
            return GalleryDlStatus(
                available = false,
                version = "Unavailable",
                engineName = "yt-dlp (Primary) / gallery-dl (Secondary)",
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
                engineName = json.optString("engine", "yt-dlp (Primary) / gallery-dl (Secondary)"),
                supportedExtractorsCount = json.optInt("supported_categories_count", 287),
                popularSites = if (sitesList.isNotEmpty()) sitesList else defaultPopularSites()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get gallery-dl status", e)
            GalleryDlStatus(
                available = true,
                version = "1.32.19",
                engineName = "yt-dlp (Primary) / gallery-dl (Secondary)",
                supportedExtractorsCount = 287,
                popularSites = defaultPopularSites()
            )
        }
    }

    /**
     * Checks whether the target URL matches a site-specific yt-dlp or gallery-dl extractor.
     */
    fun checkSupport(url: String): GalleryDlSupportInfo {
        if (url.isBlank()) {
            return GalleryDlSupportInfo(false, "", "", "", "", "yt-dlp")
        }

        if (!isAvailable()) {
            val lower = url.lowercase()
            for (site in defaultPopularSites()) {
                if (site.domains.any { lower.contains(it) }) {
                    return GalleryDlSupportInfo(
                        isSupported = true,
                        category = site.name.lowercase(),
                        subcategory = "",
                        extractorName = "${site.name}Extractor",
                        displayName = site.name,
                        extractorEngine = "gallery-dl"
                    )
                }
            }
            return GalleryDlSupportInfo(false, "", "", "", "", "yt-dlp")
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
                displayName = json.optString("display_name", json.optString("category", "")),
                extractorEngine = json.optString("engine", "yt-dlp")
            )
        } catch (e: Exception) {
            Log.e(TAG, "checkSupport error", e)
            GalleryDlSupportInfo(false, "", "", "", "", "yt-dlp")
        }
    }

    /**
     * Specifically checks whether gallery-dl supports this URL.
     */
    fun checkGalleryDlSupport(url: String): GalleryDlSupportInfo {
        if (url.isBlank()) {
            return GalleryDlSupportInfo(false, "", "", "", "", "gallery-dl")
        }

        if (!isAvailable()) {
            val lower = url.lowercase()
            for (site in defaultPopularSites()) {
                if (site.domains.any { lower.contains(it) }) {
                    return GalleryDlSupportInfo(
                        isSupported = true,
                        category = site.name.lowercase(),
                        subcategory = "",
                        extractorName = "${site.name}Extractor",
                        displayName = site.name,
                        extractorEngine = "gallery-dl"
                    )
                }
            }
            return GalleryDlSupportInfo(false, "", "", "", "", "gallery-dl")
        }

        return try {
            val py = Python.getInstance()
            val runner = py.getModule("gallery_dl_runner")
            val checkJson = runner.callAttr("check_gallery_dl_support", url.trim()).toString()
            val json = JSONObject(checkJson)
            GalleryDlSupportInfo(
                isSupported = json.optBoolean("supported", false),
                category = json.optString("category", ""),
                subcategory = json.optString("subcategory", ""),
                extractorName = json.optString("extractor", ""),
                displayName = json.optString("display_name", json.optString("category", "")),
                extractorEngine = "gallery-dl"
            )
        } catch (e: Exception) {
            Log.e(TAG, "checkGalleryDlSupport error", e)
            GalleryDlSupportInfo(false, "", "", "", "", "gallery-dl")
        }
    }


    suspend fun extractGalleryDl(url: String, options: GalleryDlOptions = GalleryDlOptions()): GalleryDlExtractResult =
        extractGalleryDlImpl(url, options)

    suspend fun extractYtDlp(url: String, options: GalleryDlOptions = GalleryDlOptions()): GalleryDlExtractResult =
        extractYtDlpImpl(url, options)

    suspend fun extractMedia(url: String, options: GalleryDlOptions = GalleryDlOptions()): GalleryDlExtractResult =
        extractMediaImpl(url, options)

    suspend fun downloadMedia(
        mediaUrl: String,
        targetFile: File,
        referer: String? = null,
        userAgent: String? = null,
        cookies: String? = null,
        extraHeaders: Map<String, String>? = null
    ): Boolean = downloadMediaImpl(mediaUrl, targetFile, referer, userAgent, cookies, extraHeaders)

    fun defaultPopularSites(): List<GalleryDlSite> = defaultPopularSitesImpl()
}
