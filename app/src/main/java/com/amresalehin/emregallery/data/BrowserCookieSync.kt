package com.amresalehin.emregallery.data

import android.webkit.CookieManager
import android.webkit.WebView
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Bidirectional cookie synchronization between [OkHttpDownloader.sharedCookieJar] and Android's [CookieManager].
 */
internal object BrowserCookieSync {

    fun syncJarToWebView(webView: WebView, targetUrl: String) {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
            val existingCookieStr = OkHttpDownloader.sharedCookieJar.getCookieString(targetUrl)
            if (existingCookieStr.isNotBlank()) {
                for (cookiePart in existingCookieStr.split(";")) {
                    val trimmed = cookiePart.trim()
                    if (trimmed.isNotEmpty()) {
                        cookieManager.setCookie(targetUrl, trimmed)
                    }
                }
                cookieManager.flush()
            }
        } catch (_: Throwable) {
        }
    }

    fun syncWebViewToJar(targetUrl: String) {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()
            val latestCookieStr = cookieManager.getCookie(targetUrl)
            if (!latestCookieStr.isNullOrBlank()) {
                val httpUrl = targetUrl.toHttpUrlOrNull() ?: return
                val parsed = latestCookieStr.split(";").mapNotNull { part ->
                    val trimmed = part.trim()
                    if (trimmed.isNotEmpty()) Cookie.parse(httpUrl, trimmed) else null
                }
                if (parsed.isNotEmpty()) {
                    OkHttpDownloader.sharedCookieJar.saveFromResponse(httpUrl, parsed)
                }
            }
        } catch (_: Throwable) {
        }
    }
}
