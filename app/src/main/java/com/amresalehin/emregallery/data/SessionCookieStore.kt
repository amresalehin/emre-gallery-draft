package com.amresalehin.emregallery.data

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Bidirectional CookieJar store bridging Android WebView CookieManager and an in-memory cache.
 */
class SessionCookieStore : CookieJar {
    private val cookieLock = Any()
    private val inMemoryCookies = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val urlString = url.toString()

        synchronized(cookieLock) {
            val currentList = inMemoryCookies.getOrPut(host) { mutableListOf() }
            for (newCookie in cookies) {
                currentList.removeAll { it.name == newCookie.name }
                currentList.add(newCookie)
            }

            // Sync into Android WebView CookieManager if available
            try {
                val cookieManager = CookieManager.getInstance()
                for (cookie in cookies) {
                    cookieManager.setCookie(urlString, cookie.toString())
                }
                cookieManager.flush()
            } catch (_: Throwable) {
                // Ignore in non-Android or JVM unit test environments
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val urlString = url.toString()
        val now = System.currentTimeMillis()

        synchronized(cookieLock) {
            val result = mutableListOf<Cookie>()

            // 1. Load from in-memory cache
            inMemoryCookies[host]?.let { list ->
                list.removeAll { it.expiresAt <= now }
                result.addAll(list)
            }

            // 2. Load from Android WebView CookieManager if available
            try {
                val cookieManager = CookieManager.getInstance()
                val rawCookieStr = cookieManager.getCookie(urlString)
                if (!rawCookieStr.isNullOrBlank()) {
                    val parsed = rawCookieStr.split(";").mapNotNull { part ->
                        val trimmed = part.trim()
                        if (trimmed.isNotEmpty()) {
                            Cookie.parse(url, trimmed)
                        } else {
                            null
                        }
                    }
                    for (cookie in parsed) {
                        if (result.none { it.name == cookie.name }) {
                            result.add(cookie)
                        }
                    }
                }
            } catch (_: Throwable) {
                // Ignore in unit test environments
            }

            return result
        }
    }

    fun getCookieString(url: String): String {
        val httpUrl = url.toHttpUrlOrNull() ?: return ""
        return loadForRequest(httpUrl).joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun setManualCookie(url: String, cookieName: String, cookieValue: String) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val cookie = Cookie.Builder()
            .domain(httpUrl.host)
            .path("/")
            .name(cookieName)
            .value(cookieValue)
            .build()
        saveFromResponse(httpUrl, listOf(cookie))
    }

    fun clearSession() {
        synchronized(cookieLock) {
            inMemoryCookies.clear()
            try {
                val cm = CookieManager.getInstance()
                cm.removeAllCookies(null)
                cm.flush()
            } catch (_: Throwable) {
            }
        }
    }
}
