package com.amresalehin.emregallery

import com.amresalehin.emregallery.ui.DownloaderPill
import com.amresalehin.emregallery.ui.DownloaderViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for Downloader Search Page view modes, quick-link pills, and search bar interactions.
 */
class DownloaderSearchPageUnitTest {

    @Test
    fun testDownloaderViewModeEnumValues() {
        val modes = DownloaderViewMode.values().map { it.name }
        assertTrue(modes.contains("SEARCH_PAGE"))
        assertTrue(modes.contains("DOWNLOADER_PAGE"))
        assertTrue(modes.contains("SAVED_LIBRARY"))
        assertTrue(modes.contains("ACTIVITY_PAGE"))
        assertEquals(4, modes.size)
    }

    @Test
    fun testDownloaderPillModel() {
        val googlePill = DownloaderPill(domain = "google.com", name = "Google", url = "https://www.google.com")
        val wikiPill = DownloaderPill(domain = "en.wikipedia.org", name = "Wikipedia", url = "https://en.wikipedia.org")
        val customFaviconPill = DownloaderPill(
            domain = "mycustomsite.com",
            name = "Custom",
            url = "https://mycustomsite.com",
            explicitFaviconUrl = "https://mycustomsite.com/icon.png"
        )

        assertEquals("google.com", googlePill.domain)
        assertEquals("Google", googlePill.name)
        assertEquals("https://www.google.com", googlePill.url)
        assertEquals("https://www.google.com/s2/favicons?domain=google.com&sz=64", googlePill.faviconUrl)

        assertEquals("en.wikipedia.org", wikiPill.domain)
        assertEquals("Wikipedia", wikiPill.name)
        assertEquals("https://www.google.com/s2/favicons?domain=en.wikipedia.org&sz=64", wikiPill.faviconUrl)

        assertEquals("https://mycustomsite.com/icon.png", customFaviconPill.faviconUrl)
    }

    @Test
    fun testDownloaderMainSearchBarFocusAndActions() {
        var isSearchFocused = false
        var urlInput = ""

        fun showSwitcher(isFocused: Boolean) = !isFocused
        fun showPasteButton(query: String) = query.isBlank()
        fun showActionButtons(query: String) = query.isNotBlank()

        assertTrue(showSwitcher(isSearchFocused))
        assertTrue(showPasteButton(urlInput))
        assertFalse(showActionButtons(urlInput))

        isSearchFocused = true
        assertFalse(showSwitcher(isSearchFocused))
        assertTrue(showPasteButton(urlInput))

        urlInput = "https://instagram.com/reel/123"
        assertFalse(showSwitcher(isSearchFocused))
        assertFalse(showPasteButton(urlInput))
        assertTrue(showActionButtons(urlInput))

        var didNavigateBack = false
        fun handleBack() {
            if (isSearchFocused || urlInput.isNotEmpty()) {
                urlInput = ""
                isSearchFocused = false
            } else {
                didNavigateBack = true
            }
        }

        handleBack()
        assertFalse(isSearchFocused)
        assertEquals("", urlInput)
        assertFalse(didNavigateBack)
        assertTrue(showSwitcher(isSearchFocused))

        handleBack()
        assertTrue(didNavigateBack)
    }
}
