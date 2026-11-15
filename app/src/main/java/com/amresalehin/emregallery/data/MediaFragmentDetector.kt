package com.amresalehin.emregallery.data

import java.util.Locale

/**
 * Detects unmerged streaming chunks, transport stream segments, and initialization fragments
 * so standalone downloads do not save partial or multiplied video slices.
 */
internal object MediaFragmentDetector {

    private val FRAGMENT_EXTENSIONS = listOf(
        ".m4s", ".m4f", ".f4f", ".f4m", ".dash", ".cmfv", ".cmfa"
    )

    private val TS_SEGMENT_REGEX =
        Regex("""(?i)(?:seg|segment|chunk|frag|fragment|track|stream|hls)?[_\-.]?\d+\.ts""")
    private val TS_NUMERIC_REGEX = Regex("""(?i)\d+\.ts""")
    private val SEGMENT_PATH_REGEX =
        Regex("""[/_.-](?:seg|segment|frag|fragment|chunk|part)[-_.]?\d+""", RegexOption.IGNORE_CASE)
    private val NUMBERED_FRAGMENT_REGEX =
        Regex("""\d+-(?:frag|seg|segment|chunk)\.(?:mp4|ts|m4s|m4v)""", RegexOption.IGNORE_CASE)
    private val CHUNK_STREAM_REGEX =
        Regex("""chunk-stream\d+-\d+\.m4s""", RegexOption.IGNORE_CASE)

    fun isMediaFragment(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        if (lower.startsWith("blob:")) return true
        if (lower.startsWith("data:")) {
            return !DataUriParser.isSubstantialDataUri(url)
        }

        val clean = lower.substringBefore("?").substringBefore("#")

        if (FRAGMENT_EXTENSIONS.any { clean.endsWith(it) }) return true
        if (isTransportStreamSegment(clean)) return true
        if (isInitSegment(clean)) return true
        if (hasFragmentPathPattern(clean)) return true

        val query = lower.substringAfter("?", "")
        return query.isNotBlank() && hasFragmentQueryParam(query)
    }

    private fun isTransportStreamSegment(cleanPath: String): Boolean {
        if (!cleanPath.endsWith(".ts")) return false
        val fileName = cleanPath.substringAfterLast('/')
        return TS_SEGMENT_REGEX.matches(fileName) ||
            TS_NUMERIC_REGEX.matches(fileName) ||
            fileName.contains("slice") || fileName.contains("stream") ||
            cleanPath.contains("/hls/") || cleanPath.contains("/dash/") ||
            cleanPath.contains("/segments/") || cleanPath.contains("/chunks/") ||
            cleanPath.contains("/live/")
    }

    private fun isInitSegment(cleanPath: String): Boolean {
        return cleanPath.contains("/init.") || cleanPath.contains("/init-") || cleanPath.contains("_init.") ||
            cleanPath.endsWith("init.mp4") || cleanPath.endsWith("init.m4s") || cleanPath.contains("init-stream") ||
            cleanPath.contains("/header.mp4") || cleanPath.contains("/init_") || cleanPath.contains("init_video") ||
            cleanPath.contains("init_audio")
    }

    private fun hasFragmentPathPattern(cleanPath: String): Boolean {
        return SEGMENT_PATH_REGEX.containsMatchIn(cleanPath) ||
            NUMBERED_FRAGMENT_REGEX.containsMatchIn(cleanPath) ||
            CHUNK_STREAM_REGEX.containsMatchIn(cleanPath)
    }

    private fun hasFragmentQueryParam(query: String): Boolean {
        for (p in query.split("&")) {
            val key = p.substringBefore("=").lowercase(Locale.ROOT)
            val value = p.substringAfter("=", "")
            if ((key == "sq" || key == "seg" || key == "segment" || key == "frag" || key == "chunk" || key == "part") &&
                value.toIntOrNull() != null
            ) {
                return true
            }
            if (key == "bytestart" || key == "byteend") {
                return true
            }
        }
        return false
    }
}
