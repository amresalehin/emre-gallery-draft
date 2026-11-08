package com.amresalehin.emregallery.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap


internal suspend fun BrowserSession.fetchCookiesInBackgroundImpl(
    context: Context,
    targetUrl: String,
    onStatusUpdate: (String) -> Unit = {}
): BackgroundCookieResult = suspendCancellableCoroutine { continuation ->
    mainHandler.post {
        val normalizedUrl = if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
            "https://$targetUrl"
        } else targetUrl

        val domain = runCatching { URL(normalizedUrl).host }.getOrDefault("")
        var webView: WebView? = null
        var isCompleted = false

        fun cleanup() {
            try {
                mainHandler.removeCallbacksAndMessages(null)
                (webView?.parent as? ViewGroup)?.removeView(webView)
                webView?.stopLoading()
                webView?.webViewClient = WebViewClient()
                webView?.webChromeClient = WebChromeClient()
                webView?.destroy()
            } catch (_: Throwable) {
            } finally {
                webView = null
            }
        }

        continuation.invokeOnCancellation {
            mainHandler.post { cleanup() }
        }

        fun finish(requiresSignIn: Boolean, signInUrl: String?) {
            if (isCompleted) return
            isCompleted = true
            try {
                val cm = CookieManager.getInstance()
                cm.flush()
                val rawCookies = cm.getCookie(normalizedUrl) ?: cm.getCookie("https://$domain")
                cleanup()
                if (continuation.isActive) {
                    continuation.resume(
                        BackgroundCookieResult(
                            success = !rawCookies.isNullOrBlank() || !requiresSignIn,
                            cookies = rawCookies,
                            requiresSignIn = requiresSignIn,
                            signInUrl = signInUrl
                        ),
                        onCancellation = { cleanup() }
                    )
                }
            } catch (e: Exception) {
                cleanup()
                if (continuation.isActive) {
                    continuation.resume(
                        BackgroundCookieResult(
                            success = false,
                            cookies = null,
                            requiresSignIn = requiresSignIn,
                            signInUrl = signInUrl
                        ),
                        onCancellation = { cleanup() }
                    )
                }
            }
        }

        try {
            val act = findActivity(context)
            val wv = if (act != null) WebView(act) else WebView(context)
            webView = wv

            if (act != null && !act.isFinishing && !act.isDestroyed) {
                val root = act.findViewById<ViewGroup>(android.R.id.content)
                if (root != null) {
                    wv.alpha = 0.001f
                    wv.isClickable = false
                    wv.isFocusable = false
                    val lp = ViewGroup.LayoutParams(1080, 1920)
                    try { root.addView(wv, 0, lp) } catch (_: Throwable) { root.addView(wv, lp) }
                }
            }

            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = BotDetectionMitigator.STEALTH_USER_AGENT
            }

            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)

            wv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    BotDetectionMitigator.injectStealth(view)
                    if (BotDetectionMitigator.isSignInPage(url, view?.title)) {
                        onStatusUpdate("Sign-in required for $domain")
                        finish(requiresSignIn = true, signInUrl = url)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    BotDetectionMitigator.injectStealth(view)
                    val title = view?.title
                    if (BotDetectionMitigator.isSignInPage(url, title)) {
                        finish(requiresSignIn = true, signInUrl = url)
                        return
                    }

                    // Check for password inputs in DOM indicating login requirement
                    view?.evaluateJavascript(
                        "Boolean(document.querySelector('input[type=\"password\"]') || document.body.innerText.includes('Sign in to confirm your age') || document.body.innerText.includes('Sign in to confirm you\\'re not a bot') || document.body.innerText.includes('Log in to continue'))"
                    ) { result ->
                        val isPwdForm = result == "true"
                        if (isPwdForm) {
                            finish(requiresSignIn = true, signInUrl = url)
                        } else {
                            mainHandler.postDelayed({
                                finish(requiresSignIn = false, signInUrl = null)
                            }, 1200L)
                        }
                    }
                }
            }

            // 10-second timeout
            mainHandler.postDelayed({
                if (!isCompleted) {
                    finish(requiresSignIn = false, signInUrl = null)
                }
            }, 10000L)

            onStatusUpdate("Obtaining cookies in background for $domain...")
            wv.loadUrl(normalizedUrl)
        } catch (e: Exception) {
            Log.e("BrowserSession", "Failed background cookie fetch", e)
            cleanup()
            if (continuation.isActive) {
                continuation.resume(
                    BackgroundCookieResult(success = false, cookies = null, requiresSignIn = false, signInUrl = null),
                    onCancellation = {}
                )
            }
        }
    }
}
