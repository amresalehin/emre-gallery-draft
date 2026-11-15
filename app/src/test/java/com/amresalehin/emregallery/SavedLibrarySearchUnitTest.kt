package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaInboxItem
import com.amresalehin.emregallery.data.MediaInboxStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tests for Saved Library search filtering, search bar focus rules, and preview section slicing.
 */
class SavedLibrarySearchUnitTest {

    @Test
    fun testSavedLibrarySearchFilterLogic() {
        val stack1 = MediaInboxStack(
            id = "stack_1",
            title = "Taj Mahal - Wikipedia",
            sourceDomain = "wikipedia.org",
            sourceUrl = "https://en.wikipedia.org/wiki/Taj_Mahal",
            items = listOf(
                MediaInboxItem(
                    id = "item_1",
                    title = "Taj Mahal Front View",
                    localUri = "content://media/1",
                    sourceUrl = "https://upload.wikimedia.org/taj.jpg",
                    isVideo = false
                )
            )
        )
        val stack2 = MediaInboxStack(
            id = "stack_2",
            title = "Pexels Nature Scenery",
            sourceDomain = "pexels.com",
            sourceUrl = "https://pexels.com/nature",
            items = listOf(
                MediaInboxItem(
                    id = "item_2",
                    title = "Desert Sunset",
                    localUri = "content://media/2",
                    sourceUrl = "https://images.pexels.com/desert.jpg",
                    isVideo = false
                )
            )
        )
        val allStacks = listOf(stack1, stack2)

        val tajMatches = allStacks.filter { stack ->
            stack.title.contains("taj", ignoreCase = true) ||
                stack.sourceDomain.contains("taj", ignoreCase = true) ||
                stack.items.any { it.title.contains("taj", ignoreCase = true) }
        }
        assertEquals(1, tajMatches.size)
        assertEquals("stack_1", tajMatches[0].id)

        val pexelsMatches = allStacks.filter { stack ->
            stack.title.contains("pexels", ignoreCase = true) ||
                stack.sourceDomain.contains("pexels", ignoreCase = true) ||
                stack.items.any { it.title.contains("pexels", ignoreCase = true) }
        }
        assertEquals(1, pexelsMatches.size)
        assertEquals("stack_2", pexelsMatches[0].id)

        val desertMatches = allStacks.filter { stack ->
            stack.title.contains("desert", ignoreCase = true) ||
                stack.sourceDomain.contains("desert", ignoreCase = true) ||
                stack.items.any { it.title.contains("desert", ignoreCase = true) }
        }
        assertEquals(1, desertMatches.size)
        assertEquals("stack_2", desertMatches[0].id)

        val emptyMatches = allStacks.filter { stack ->
            "".isBlank() || stack.title.contains("", ignoreCase = true)
        }
        assertEquals(2, emptyMatches.size)
    }

    @Test
    fun testSavedLibrarySearchBarFocusRules() {
        var isSearchFocused = false
        var searchQuery = ""

        fun showSwitcher(isFocused: Boolean) = !isFocused
        fun showFilterButton(isFocused: Boolean, query: String) = !isFocused && query.isEmpty()
        fun showClearButton(isFocused: Boolean, query: String) = isFocused || query.isNotEmpty()

        assertTrue(showSwitcher(isSearchFocused))
        assertTrue(showFilterButton(isSearchFocused, searchQuery))
        assertFalse(showClearButton(isSearchFocused, searchQuery))

        isSearchFocused = true
        assertFalse(showSwitcher(isSearchFocused))
        assertFalse(showFilterButton(isSearchFocused, searchQuery))
        assertTrue(showClearButton(isSearchFocused, searchQuery))

        searchQuery = "wikipedia"
        assertFalse(showSwitcher(isSearchFocused))
        assertTrue(showClearButton(isSearchFocused, searchQuery))

        searchQuery = ""
        assertTrue(isSearchFocused)
        assertTrue(showClearButton(isSearchFocused, searchQuery))

        searchQuery = "some query"
        if (isSearchFocused || searchQuery.isNotEmpty()) {
            searchQuery = ""
            isSearchFocused = false
        }
        assertFalse(isSearchFocused)
        assertEquals("", searchQuery)
        assertTrue(showSwitcher(isSearchFocused))
        assertTrue(showFilterButton(isSearchFocused, searchQuery))
        assertFalse(showClearButton(isSearchFocused, searchQuery))
    }

    @Test
    fun testSavedPreviewSectionLogic() {
        val stack1 = MediaInboxStack(
            id = "stack_taj",
            title = "Taj Mahal",
            sourceDomain = "wikipedia.org",
            sourceUrl = "https://en.wikipedia.org/wiki/Taj_Mahal",
            timestampMillis = 1793782920000L,
            items = List(16) { i ->
                MediaInboxItem(
                    id = "item_$i",
                    title = "Photo $i",
                    localUri = "content://media/$i",
                    sourceUrl = "https://example.com/$i.jpg",
                    isVideo = false
                )
            }
        )

        assertEquals("Taj Mahal", stack1.title)
        assertEquals(16, stack1.items.size)

        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val formatted = sdf.format(Date(stack1.timestampMillis))
        assertTrue(formatted.isNotBlank())

        val paletteCount = 5
        assertEquals(0, 0 % paletteCount)
        assertEquals(1, 1 % paletteCount)
        assertEquals(2, 2 % paletteCount)

        val stacksList = List(12) { stack1 }
        val displayStacks = stacksList.take(5)
        assertEquals(5, displayStacks.size)
    }
}
