package com.amresalehin.emregallery.data

import org.jsoup.nodes.Element

/**
 * Content-Aware Media Extraction Filter.
 * Delegates URL/metadata and DOM/CSS chrome classification to focused single-responsibility rules.
 */
object ContentAwareMediaFilter {

    /**
     * Evaluates whether a media URL or its metadata indicates website chrome rather than content.
     */
    fun isUiChromeUrl(
        url: String,
        title: String? = null,
        width: Int? = null,
        height: Int? = null,
        isVideoOrAudioOrStream: Boolean = false
    ): Boolean = UrlChromeClassifier.isUiChromeUrl(
        url = url,
        title = title,
        width = width,
        height = height,
        isVideoOrAudioOrStream = isVideoOrAudioOrStream
    )

    /**
     * Checks whether an HTML DOM element in Jsoup is part of website chrome.
     */
    fun isUiChromeElement(element: Element): Boolean =
        DomChromeClassifier.isUiChromeElement(element)

    /**
     * Evaluates CSS background-image candidate element to ensure it's not a button, pattern, or gradient icon.
     */
    fun isAcceptableCssBackground(element: Element): Boolean =
        DomChromeClassifier.isAcceptableCssBackground(element)
}
