package com.amresalehin.emregallery.data

/**
 * Parses HTML `srcset` attributes into validated `(resolvedUrl, resolutionDescriptor)` pairs.
 */
object SrcsetParser {

    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Parses srcset attribute (e.g., "img1.jpg 300w, img2.jpg 600w, img3.jpg 1200w").
     * Returns a list of Pair(url, resolutionDescriptor).
     */
    fun parse(srcset: String, baseUrl: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        for (entry in srcset.split(",")) {
            val parts = entry.trim().split(WHITESPACE_REGEX)
            if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                val resolved = WebUrlUtils.resolveUrl(parts[0], baseUrl)
                if (resolved != null && MediaUrlValidator.isValidMedia(resolved)) {
                    val descriptor = if (parts.size > 1) parts[1] else ""
                    results.add(Pair(resolved, descriptor))
                }
            }
        }
        return results
    }
}
