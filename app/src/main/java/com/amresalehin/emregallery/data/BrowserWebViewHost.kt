package com.amresalehin.emregallery.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Creates, configures, lays out offscreen, and cleans up a headless [WebView] instance for extraction.
 */
internal object BrowserWebViewHost {

    @SuppressLint("SetJavaScriptEnabled")
    fun createOffscreenWebView(context: Context): WebView {
        val activity = findActivity(context)
        val webView = if (activity != null) WebView(activity) else WebView(context)

        val displayMetrics = context.resources.displayMetrics
        val viewWidth = if (displayMetrics.widthPixels > 0) displayMetrics.widthPixels else 1080
        val viewHeight = if (displayMetrics.heightPixels > 0) displayMetrics.heightPixels else 1920

        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            if (root != null) {
                webView.alpha = 0.01f
                webView.translationX = -9999f
                val lp = ViewGroup.LayoutParams(viewWidth, viewHeight)
                root.addView(webView, lp)
            }
        }

        webView.measure(
            View.MeasureSpec.makeMeasureSpec(viewWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(viewHeight, View.MeasureSpec.EXACTLY)
        )
        webView.layout(0, 0, viewWidth, viewHeight)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = OkHttpDownloader.DEFAULT_USER_AGENT
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.onResume()
        webView.resumeTimers()
        return webView
    }

    fun destroyWebView(webView: WebView?, javascriptInterfaceName: String = "EmreBridge") {
        if (webView == null) return
        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.removeJavascriptInterface(javascriptInterfaceName)
            webView.webViewClient = WebViewClient()
            webView.webChromeClient = WebChromeClient()
            webView.destroy()
        } catch (_: Exception) {
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
