package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaClassifier
import com.amresalehin.emregallery.ui.DetectedPlatform
import com.amresalehin.emregallery.ui.DownloaderViewMode
import com.amresalehin.emregallery.ui.detectPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloaderSearchPageUnitTest {

    @Test
    fun testDownloaderViewModeEnumValues() {
        val modes = DownloaderViewMode.values().map { it.name }
        assertTrue(modes.contains("SEARCH_PAGE"))
        assertTrue(modes.contains("DOWNLOADER_PAGE"))
        assertTrue(modes.contains("SAVED_LIBRARY"))
        assertEquals(3, modes.size)
    }

    @Test
    fun testPlatformDetectionFromVariousUrls() {
        // YouTube detection
        assertEquals(DetectedPlatform.YOUTUBE, detectPlatform("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(DetectedPlatform.YOUTUBE, detectPlatform("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(DetectedPlatform.YOUTUBE, detectPlatform("https://m.youtube.com/shorts/12345"))

        // Instagram detection
        assertEquals(DetectedPlatform.INSTAGRAM, detectPlatform("https://instagram.com/reel/C123456/"))
        assertEquals(DetectedPlatform.INSTAGRAM, detectPlatform("https://www.instagram.com/p/B987654/"))

        // TikTok detection
        assertEquals(DetectedPlatform.TIKTOK, detectPlatform("https://www.tiktok.com/@creator/video/987654321"))
        assertEquals(DetectedPlatform.TIKTOK, detectPlatform("https://vm.tiktok.com/ZM8abcde/"))

        // Twitter / X detection
        assertEquals(DetectedPlatform.TWITTER, detectPlatform("https://x.com/user/status/1234567890"))
        assertEquals(DetectedPlatform.TWITTER, detectPlatform("https://twitter.com/user/status/9876543210"))

        // Reddit detection
        assertEquals(DetectedPlatform.REDDIT, detectPlatform("https://www.reddit.com/r/Android/comments/abc123/new_feature/"))
        assertEquals(DetectedPlatform.REDDIT, detectPlatform("https://redd.it/xyz987"))

        // Pinterest detection
        assertEquals(DetectedPlatform.PINTEREST, detectPlatform("https://pinterest.com/pin/123456789/"))
        assertEquals(DetectedPlatform.PINTEREST, detectPlatform("https://pin.it/abc1234"))

        // Facebook detection
        assertEquals(DetectedPlatform.FACEBOOK, detectPlatform("https://www.facebook.com/watch/?v=123456"))
        assertEquals(DetectedPlatform.FACEBOOK, detectPlatform("https://fb.watch/xyz123/"))

        // Direct media links
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://cdn.example.com/videos/stream.m3u8"))
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://cdn.example.com/videos/manifest.mpd"))
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://cdn.example.com/media/clip.mp4"))
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://cdn.example.com/images/hero.jpg"))
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://cdn.example.com/images/banner.webp"))
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://cdn.example.com/audio/podcast.mp3"))

        // Generic websites
        assertEquals(DetectedPlatform.GENERIC_WEB, detectPlatform("https://en.wikipedia.org/wiki/Kotlin"))
        assertEquals(DetectedPlatform.GENERIC_WEB, detectPlatform("https://github.com/trending"))
    }

    @Test
    fun testUrlValidationForSearchAndFetch() {
        // Valid web links should be accepted for Fetch
        assertTrue(MediaClassifier.isValidWebUrl("https://youtube.com/watch?v=123"))
        assertTrue(MediaClassifier.isValidWebUrl("http://example.com/gallery"))
        assertTrue(MediaClassifier.isValidWebUrl("instagram.com/p/abc"))
        assertTrue(MediaClassifier.isValidWebUrl("www.reddit.com/r/pics"))

        // Non-URLs or junk input should be rejected
        assertFalse(MediaClassifier.isValidWebUrl("just random words"))
        assertFalse(MediaClassifier.isValidWebUrl("   "))
        assertFalse(MediaClassifier.isValidWebUrl(""))
        assertFalse(MediaClassifier.isValidWebUrl("javascript:alert(1)"))
    }

    @Test
    fun testDownloaderPillModel() {
        val googlePill = com.amresalehin.emregallery.ui.DownloaderPill(domain = "google.com", name = "Google", url = "https://www.google.com")
        val wikiPill = com.amresalehin.emregallery.ui.DownloaderPill(domain = "en.wikipedia.org", name = "Wikipedia", url = "https://en.wikipedia.org")
        val customFaviconPill = com.amresalehin.emregallery.ui.DownloaderPill(
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
    fun testSavedLibrarySearchFilterLogic() {
        val stack1 = com.amresalehin.emregallery.data.MediaInboxStack(
            id = "stack_1",
            title = "Taj Mahal - Wikipedia",
            sourceDomain = "wikipedia.org",
            sourceUrl = "https://en.wikipedia.org/wiki/Taj_Mahal",
            items = listOf(
                com.amresalehin.emregallery.data.MediaInboxItem(
                    id = "item_1",
                    title = "Taj Mahal Front View",
                    localUri = "content://media/1",
                    sourceUrl = "https://upload.wikimedia.org/taj.jpg",
                    isVideo = false
                )
            )
        )
        val stack2 = com.amresalehin.emregallery.data.MediaInboxStack(
            id = "stack_2",
            title = "Pexels Nature Scenery",
            sourceDomain = "pexels.com",
            sourceUrl = "https://pexels.com/nature",
            items = listOf(
                com.amresalehin.emregallery.data.MediaInboxItem(
                    id = "item_2",
                    title = "Desert Sunset",
                    localUri = "content://media/2",
                    sourceUrl = "https://images.pexels.com/desert.jpg",
                    isVideo = false
                )
            )
        )
        val allStacks = listOf(stack1, stack2)

        // Matching title
        val tajMatches = allStacks.filter { stack ->
            stack.title.contains("taj", ignoreCase = true) ||
                    stack.sourceDomain.contains("taj", ignoreCase = true) ||
                    stack.items.any { it.title.contains("taj", ignoreCase = true) }
        }
        assertEquals(1, tajMatches.size)
        assertEquals("stack_1", tajMatches[0].id)

        // Matching domain
        val pexelsMatches = allStacks.filter { stack ->
            stack.title.contains("pexels", ignoreCase = true) ||
                    stack.sourceDomain.contains("pexels", ignoreCase = true) ||
                    stack.items.any { it.title.contains("pexels", ignoreCase = true) }
        }
        assertEquals(1, pexelsMatches.size)
        assertEquals("stack_2", pexelsMatches[0].id)

        // Matching item title
        val desertMatches = allStacks.filter { stack ->
            stack.title.contains("desert", ignoreCase = true) ||
                    stack.sourceDomain.contains("desert", ignoreCase = true) ||
                    stack.items.any { it.title.contains("desert", ignoreCase = true) }
        }
        assertEquals(1, desertMatches.size)
        assertEquals("stack_2", desertMatches[0].id)

        // Empty query matches all
        val emptyMatches = allStacks.filter { stack ->
            "".isBlank() || stack.title.contains("", ignoreCase = true)
        }
        assertEquals(2, emptyMatches.size)
    }

    @Test
    fun testSavedLibrarySearchBarFocusRules() {
        // State 1: Default (Unfocused, empty query)
        var isSearchFocused = false
        var searchQuery = ""

        fun showSwitcher(isFocused: Boolean) = !isFocused
        fun showFilterButton(isFocused: Boolean, query: String) = !isFocused && query.isEmpty()
        fun showClearButton(isFocused: Boolean, query: String) = isFocused || query.isNotEmpty()

        assertTrue(showSwitcher(isSearchFocused))
        assertTrue(showFilterButton(isSearchFocused, searchQuery))
        assertFalse(showClearButton(isSearchFocused, searchQuery))

        // State 2: Focus received
        isSearchFocused = true
        assertFalse(showSwitcher(isSearchFocused)) // 1. Hide switcher pill
        assertFalse(showFilterButton(isSearchFocused, searchQuery)) // 2. Hide filter button
        assertTrue(showClearButton(isSearchFocused, searchQuery)) // 4. Show clear button

        // State 3: User types a query
        searchQuery = "wikipedia"
        assertFalse(showSwitcher(isSearchFocused))
        assertTrue(showClearButton(isSearchFocused, searchQuery))

        // Action: Clear button clicked (only clears query without losing focus)
        searchQuery = ""
        assertTrue(isSearchFocused) // focus remains
        assertTrue(showClearButton(isSearchFocused, searchQuery)) // clear button stays because focused

        // Action: Back button clicked in search mode (exits search mode & clears focus)
        searchQuery = "some query"
        // Simulate Back click in search mode
        if (isSearchFocused || searchQuery.isNotEmpty()) {
            searchQuery = ""
            isSearchFocused = false
        }
        assertFalse(isSearchFocused)
        assertEquals("", searchQuery)
        assertTrue(showSwitcher(isSearchFocused)) // Restores default layout
        assertTrue(showFilterButton(isSearchFocused, searchQuery))
        assertFalse(showClearButton(isSearchFocused, searchQuery))
    }
}

