package com.amresalehin.emregallery.data

import org.jsoup.nodes.Element
import java.util.Locale

/**
 * Inspects HTML DOM ancestors, element attributes, and inline CSS background targets
 * to distinguish page chrome elements from genuine content media.
 */
internal object DomChromeClassifier {

    fun isUiChromeElement(element: Element): Boolean {
        val tagName = element.tagName().lowercase(Locale.ROOT)
        // Video and audio tags are preserved
        if (tagName == "video" || tagName == "audio") return false

        if (isInsideChromeAncestor(element)) return true
        if (hasChromeSelfAttributes(element)) return true

        val w = element.attr("width").toIntOrNull() ?: element.attr("data-width").toIntOrNull()
        val h = element.attr("height").toIntOrNull() ?: element.attr("data-height").toIntOrNull()
        return ChromeMediaRules.isTinyOrExtremeAspectRatio(w, h)
    }

    fun isAcceptableCssBackground(element: Element): Boolean {
        if (isUiChromeElement(element)) return false
        val tag = element.tagName().lowercase(Locale.ROOT)
        if (tag == "button" || tag == "input" || tag == "a" || tag == "span" || tag == "i") {
            val w = element.attr("width").toIntOrNull() ?: 0
            val h = element.attr("height").toIntOrNull() ?: 0
            if (w < 120 || h < 120) return false
        }
        return true
    }

    private fun isInsideChromeAncestor(element: Element): Boolean {
        var curr: Element? = element.parent()
        while (curr != null && curr.tagName() != "body" && curr.tagName() != "html") {
            val tag = curr.tagName().lowercase(Locale.ROOT)
            val id = curr.id().lowercase(Locale.ROOT)
            val className = curr.className().lowercase(Locale.ROOT)

            // If we hit a content container first, do not reject based on outer ancestors
            if (tag == "article" || tag == "main" || tag == "figure" ||
                className.contains("mw-parser-output") || className.contains("gallery") ||
                className.contains("post-content") || className.contains("entry-content") ||
                id == "content" || id == "main-content"
            ) {
                return false
            }

            if (matchesChromeContainer(curr, tag, id, className)) {
                return true
            }

            curr = curr.parent()
        }
        return false
    }

    private fun matchesChromeContainer(
        element: Element,
        tag: String,
        id: String,
        className: String
    ): Boolean {
        return tag == "header" || tag == "nav" || tag == "footer" || tag == "aside" || tag == "menu" || tag == "form" ||
            element.attr("role").equals("banner", ignoreCase = true) ||
            element.attr("role").equals("navigation", ignoreCase = true) ||
            element.attr("role").equals("contentinfo", ignoreCase = true) ||
            className.contains("navbar") || className.contains("nav") || className.contains("menu") ||
            className.contains("header") || className.contains("footer") || className.contains("sidebar") ||
            className.contains("breadcrumb") || className.contains("pagination") || className.contains("toolbar") ||
            id.contains("header") || id.contains("nav") || id.contains("footer") || id.contains("sidebar") ||
            id.contains("mw-navigation") || id.contains("p-logo")
    }

    private fun hasChromeSelfAttributes(element: Element): Boolean {
        val selfId = element.id().lowercase(Locale.ROOT)
        val selfClass = element.className().lowercase(Locale.ROOT)
        val selfAlt = element.attr("alt").lowercase(Locale.ROOT)
        val selfTitle = element.attr("title").lowercase(Locale.ROOT)

        return ChromeMediaRules.CHROME_KEYWORDS.any { kw ->
            selfId.contains(kw) || selfClass.contains(kw) || selfAlt.contains(kw) || selfTitle.contains(kw)
        }
    }
}
