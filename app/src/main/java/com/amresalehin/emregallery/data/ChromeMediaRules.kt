package com.amresalehin.emregallery.data

/**
 * Shared keyword taxonomy and spatial dimension thresholds identifying website UI chrome.
 */
internal object ChromeMediaRules {

    val CHROME_KEYWORDS = setOf(
        "favicon", "site-logo", "header-logo", "brand-logo", "nav-logo", "logo",
        "wordmark", "tagline", "watermark",
        "avatar_default", "default-avatar", "placeholder-avatar",
        "donate", "donation", "fundraising", "paypal", "patreon", "kofi",
        "poweredby_mediawiki", "wikimedia-button", "license-badge", "badge",
        "magnifying-glass", "search-icon", "nav-icon", "menu-icon", "close-icon",
        "arrow-left", "arrow-right", "arrow-up", "arrow-down", "chevron",
        "bullet", "divider", "spacer", "blank.gif", "transparent.gif", "pixel.gif",
        "1x1.png", "1x1.gif", "1x1.jpg", "spinner.gif", "loading.gif",
        "app-store-badge", "google-play-badge", "social-share", "share-button"
    )

    fun isTinyOrExtremeAspectRatio(width: Int?, height: Int?): Boolean {
        if (width == null || height == null || width <= 0 || height <= 0) return false
        if (width <= 48 && height <= 48) return true
        if (width <= 32 || height <= 32) return true
        val ratio = width.toFloat() / height.toFloat()
        return ratio > 12f || ratio < (1f / 12f)
    }
}
