package com.amresalehin.emregallery.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.WebView

/**
 * Resolves and dispatches external/non-HTTP URI schemes (e.g., `intent://`, `market://`, `tel:`, `mailto:`)
 * encountered during in-app WebView navigation.
 */
internal object DirectBrowserSchemeHandler {

    private const val TAG = "DirectBrowserView"

    fun handleExternalNavigation(view: WebView?, urlString: String): Boolean {
        if (urlString.isBlank()) return false
        val lower = urlString.trim().lowercase()

        // Internal browser schemes should load within Chromium WebView
        if (isInternalBrowserScheme(lower)) {
            return false
        }

        val context = view?.context ?: return false

        if (lower.startsWith("intent://") || lower.startsWith("intent:")) {
            return handleIntentScheme(view, context, urlString)
        }

        return handleGenericExternalScheme(view, context, urlString, lower)
    }

    private fun isInternalBrowserScheme(lowerUrl: String): Boolean {
        return lowerUrl.startsWith("http://") ||
            lowerUrl.startsWith("https://") ||
            lowerUrl.startsWith("about:") ||
            lowerUrl.startsWith("data:") ||
            lowerUrl.startsWith("javascript:") ||
            lowerUrl.startsWith("blob:") ||
            lowerUrl.startsWith("file:///android_asset/")
    }

    private fun handleIntentScheme(
        view: WebView?,
        context: android.content.Context,
        urlString: String
    ): Boolean {
        try {
            val intent = Intent.parseUri(urlString, Intent.URI_INTENT_SCHEME)
            if (intent != null) {
                val pm = context.packageManager
                if (intent.resolveActivity(pm) != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                }

                // Fallback URL if target app is not installed
                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                if (!fallbackUrl.isNullOrBlank() &&
                    (fallbackUrl.startsWith("http://", ignoreCase = true) ||
                        fallbackUrl.startsWith("https://", ignoreCase = true))
                ) {
                    view?.loadUrl(fallbackUrl)
                    return true
                }

                // Route target package to Google Play Store
                val packageName = intent.`package`
                if (!packageName.isNullOrBlank()) {
                    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (marketIntent.resolveActivity(pm) != null) {
                        context.startActivity(marketIntent)
                        return true
                    } else {
                        view?.loadUrl("https://play.google.com/store/apps/details?id=$packageName")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve intent scheme: $urlString", e)
        }
        return true // Prevent Chromium from displaying net::ERR_UNKNOWN_URL_SCHEME
    }

    private fun handleGenericExternalScheme(
        view: WebView?,
        context: android.content.Context,
        urlString: String,
        lowerUrl: String
    ): Boolean {
        try {
            val uri = Uri.parse(urlString)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pm = context.packageManager
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                return true
            } else if (lowerUrl.startsWith("market://details?id=")) {
                val pkg = uri.getQueryParameter("id")
                if (!pkg.isNullOrBlank()) {
                    view?.loadUrl("https://play.google.com/store/apps/details?id=$pkg")
                    return true
                }
            }
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app available to handle scheme: $urlString", e)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle external URI: $urlString", e)
            return true
        }
        return true
    }
}
