package com.amresalehin.emregallery.ui

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.amresalehin.emregallery.data.MediaInboxStack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StackLayerPalettesLight = listOf(
    listOf(Color(0xFFEDE9FE), Color(0xFFDDD6FE)),
    listOf(Color(0xFFFEF3C7), Color(0xFFFFD1D8)),
    listOf(Color(0xFFD1FAE5), Color(0xFFA7F3D0)),
    listOf(Color(0xFFFCE7F3), Color(0xFFFBCFE8)),
    listOf(Color(0xFFCFFAFE), Color(0xFFA5F3FC)),
    listOf(Color(0xFFE0E7FF), Color(0xFFC7D2FE))
)

private val StackLayerPalettesDark = listOf(
    listOf(Color(0xFF312E81).copy(alpha = 0.65f), Color(0xFF3730A3).copy(alpha = 0.45f)),
    listOf(Color(0xFF78350F).copy(alpha = 0.65f), Color(0xFF92400E).copy(alpha = 0.45f)),
    listOf(Color(0xFF064E3B).copy(alpha = 0.65f), Color(0xFF065F46).copy(alpha = 0.5f)),
    listOf(Color(0xFF831843).copy(alpha = 0.65f), Color(0xFF9D174D).copy(alpha = 0.45f)),
    listOf(Color(0xFF164E63).copy(alpha = 0.65f), Color(0xFF155E75).copy(alpha = 0.45f)),
    listOf(Color(0xFF1E1B4B).copy(alpha = 0.65f), Color(0xFF312E81).copy(alpha = 0.45f))
)

/**
 * Presentation helpers for stacked inbox collection cards:
 * layer palettes, cover model lookup, domain/favicon extraction, and date formatting.
 */
internal object StackCardPresentation {

    fun layerColors(accentIndex: Int, isDark: Boolean): List<Color> {
        val list = if (isDark) StackLayerPalettesDark else StackLayerPalettesLight
        return list[accentIndex % list.size]
    }

    fun formatDate(timestampMillis: Long): String {
        return SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(timestampMillis))
    }

    fun resolveCoverModel(stack: MediaInboxStack): String? {
        val coverItem = stack.items.firstOrNull { !it.isVideo && it.localUri.isNotBlank() }
            ?: stack.items.firstOrNull { it.localUri.isNotBlank() }
            ?: stack.items.firstOrNull()
        return coverItem?.localUri?.takeIf { it.isNotBlank() }
            ?: coverItem?.sourceUrl?.takeIf { it.isNotBlank() }
    }

    fun resolveCleanDomain(stack: MediaInboxStack): String {
        val raw = stack.sourceDomain.ifBlank {
            try {
                Uri.parse(stack.sourceUrl).host.orEmpty()
            } catch (_: Exception) {
                ""
            }
        }
        return raw.removePrefix("www.").lowercase().trim()
    }

    fun resolveFaviconUrl(cleanDomain: String): String? {
        return if (cleanDomain.isNotBlank()) {
            "https://www.google.com/s2/favicons?domain=$cleanDomain&sz=64"
        } else {
            null
        }
    }
}
