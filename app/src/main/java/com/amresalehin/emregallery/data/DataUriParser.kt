package com.amresalehin.emregallery.data

import java.util.Locale
import kotlin.math.abs

data class DataUriInfo(
    val mimeType: String,
    val format: String,
    val estimatedSizeBytes: Long,
    val cleanTitle: String
)

/**
 * Validates and inspects inline `data:` URIs, filtering out tiny 1x1 tracking pixels
 * and empty SVG placeholders while estimating decoded payload sizes.
 */
object DataUriParser {

    /**
     * Checks if a string is a substantial, legitimate Base64 inline data URI (e.g. artwork, canvas exports),
     * while rejecting tiny spacer GIFs, 1x1 transparent tracking pixels, and empty vectors.
     */
    fun isSubstantialDataUri(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("data:image/", ignoreCase = true)) return false
        // Exclude SVG/XML data URIs which are placeholders/spacers
        if (trimmed.contains("svg+xml", ignoreCase = true) ||
            trimmed.contains("<svg", ignoreCase = true) ||
            trimmed.contains("PHN2Zy", ignoreCase = true)
        ) {
            return false
        }
        // Exclude tiny spacers, 1x1 pixel tracking GIFs (< 2048 characters base64)
        if (trimmed.length < 2048) return false
        // Known 1x1 transparent GIF signature
        if (trimmed.contains("R0lGODlhAQABA", ignoreCase = true)) return false
        return trimmed.contains(";base64,", ignoreCase = true)
    }

    fun parse(dataUri: String): DataUriInfo? {
        if (!isSubstantialDataUri(dataUri)) return null
        val header = dataUri.substringBefore(";base64,").removePrefix("data:").trim().lowercase(Locale.ROOT)
        val mimeType = if (header.isBlank()) "image/png" else header
        val format = when {
            mimeType.contains("png") -> "PNG"
            mimeType.contains("webp") -> "WEBP"
            mimeType.contains("gif") -> "GIF"
            mimeType.contains("svg") -> "SVG"
            else -> "JPEG"
        }
        val ext = format.lowercase(Locale.ROOT)
        val base64Payload = dataUri.substringAfter(";base64,", "")
        val estimatedBytes = (base64Payload.length * 3L) / 4L
        val cleanTitle = "embedded_artwork_${abs(dataUri.hashCode()) % 100000}.$ext"
        return DataUriInfo(
            mimeType = mimeType,
            format = format,
            estimatedSizeBytes = estimatedBytes,
            cleanTitle = cleanTitle
        )
    }

    /**
     * Estimates the size in bytes of a data URI without loading full bitmap into memory.
     */
    fun estimateSizeBytes(url: String): Long? {
        if (!url.startsWith("data:", ignoreCase = true)) return null
        val base64Payload = url.substringAfter(";base64,", "")
        if (base64Payload.isNotEmpty()) {
            return (base64Payload.length * 3L) / 4L
        }
        val commaIdx = url.indexOf(',')
        if (commaIdx != -1) {
            return (url.length - commaIdx - 1).toLong()
        }
        return null
    }
}
