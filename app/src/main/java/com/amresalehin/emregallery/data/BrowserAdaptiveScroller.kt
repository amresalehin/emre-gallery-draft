package com.amresalehin.emregallery.data

import android.os.Handler
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.abs

/**
 * Executes an adaptive stepped scroll sequence on a [WebView] to trigger lazy-loaded media
 * while stopping early once scroll position stagnates or the end of the page is reached.
 */
internal class BrowserAdaptiveScroller(
    private val mainHandler: Handler,
    private val isCancelled: () -> Boolean
) {
    fun executeScrollSequence(
        webView: WebView,
        step: Int = 1,
        maxSteps: Int = 12,
        stagnantCount: Int = 0,
        lastScrollY: Int = -1,
        onDone: () -> Unit
    ) {
        if (isCancelled() || step > maxSteps || stagnantCount >= 2) {
            onDone()
            return
        }

        webView.evaluateJavascript(MediaCaptureEngine.getAdaptiveScrollScript()) { resultJson ->
            if (isCancelled()) {
                onDone()
                return@evaluateJavascript
            }

            val (currentScrollY, remaining) = parseScrollMetrics(resultJson, lastScrollY)
            val isStagnant = lastScrollY != -1 && abs(currentScrollY - lastScrollY) < 10
            val newStagnantCount = if (isStagnant) stagnantCount + 1 else 0

            if (remaining <= 5 || newStagnantCount >= 2) {
                onDone()
            } else {
                mainHandler.postDelayed({
                    if (!isCancelled()) {
                        executeScrollSequence(
                            webView = webView,
                            step = step + 1,
                            maxSteps = maxSteps,
                            stagnantCount = newStagnantCount,
                            lastScrollY = currentScrollY,
                            onDone = onDone
                        )
                    } else {
                        onDone()
                    }
                }, 500L)
            }
        }
    }

    private fun parseScrollMetrics(resultJson: String?, defaultScrollY: Int): Pair<Int, Int> {
        var currentScrollY = defaultScrollY
        var remaining = 1000
        runCatching {
            val unescaped = if (resultJson != null && resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                JSONTokener(resultJson).nextValue().toString()
            } else {
                resultJson ?: "{}"
            }
            val obj = JSONObject(unescaped)
            currentScrollY = obj.optInt("scrollY", defaultScrollY)
            remaining = obj.optInt("remaining", 1000)
        }
        return currentScrollY to remaining
    }
}
