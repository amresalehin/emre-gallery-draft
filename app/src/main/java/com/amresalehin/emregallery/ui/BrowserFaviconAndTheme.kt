package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*


fun DuckDuckGoLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(38.dp)
            .background(Color(0xFFDE5833), shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            // White duck head
            drawCircle(
                color = Color.White,
                radius = size.width * 0.34f,
                center = Offset(size.width * 0.50f, size.height * 0.46f)
            )
            // Yellow beak
            val beak = Path().apply {
                moveTo(size.width * 0.66f, size.height * 0.40f)
                lineTo(size.width * 0.94f, size.height * 0.48f)
                lineTo(size.width * 0.66f, size.height * 0.56f)
                close()
            }
            drawPath(beak, color = Color(0xFFF7A200))
            // Duck eye
            drawCircle(
                color = Color(0xFF1E293B),
                radius = size.width * 0.055f,
                center = Offset(size.width * 0.56f, size.height * 0.42f)
            )
            // Green bow tie
            val bow = Path().apply {
                moveTo(size.width * 0.32f, size.height * 0.76f)
                lineTo(size.width * 0.48f, size.height * 0.68f)
                lineTo(size.width * 0.64f, size.height * 0.76f)
                lineTo(size.width * 0.48f, size.height * 0.84f)
                close()
            }
            drawPath(bow, color = Color(0xFF3B9E5A))
        }
    }
}

@Composable
fun ClaudeLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(38.dp)
            .background(Color.White, shape = CircleShape)
            .border(1.dp, Color(0xFFF1F1F1), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val orange = Color(0xFFCC5500)
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            val strokeW = 2.4.dp.toPx()
            val rOuter = size.width * 0.44f
            val rInner = size.width * 0.16f

            // 8 radiating sunburst lines
            val angles = listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)
            for (deg in angles) {
                val rad = Math.toRadians(deg)
                val cos = Math.cos(rad).toFloat()
                val sin = Math.sin(rad).toFloat()
                val pStart = Offset(center.x + rInner * cos, center.y + rInner * sin)
                val pEnd = Offset(center.x + rOuter * cos, center.y + rOuter * sin)
                drawLine(
                    color = orange,
                    start = pStart,
                    end = pEnd,
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun AnthropicAiLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(38.dp)
            .background(Color(0xFFEADBCE), shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "AI",
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            color = Color(0xFF1C1917)
        )
    }
}

@Composable
fun BrandFavicon(
    domain: String,
    title: String,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    val d = domain.lowercase()
    val t = title.lowercase()
    when {
        d.contains("duckduckgo") || t.contains("duckduckgo") -> DuckDuckGoLogo(modifier)
        d.contains("claude") || t.contains("claude") -> ClaudeLogo(modifier)
        d.contains("wikipedia") || t.contains("wikipedia") -> Box(
            modifier = modifier
                .size(38.dp)
                .background(if (isDark) Color(0xFF262628) else Color.White, shape = CircleShape)
                .border(1.dp, if (isDark) Color(0xFF38383C) else Color(0xFFEAEAEA), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            WikipediaLogo(Modifier.size(24.dp))
        }
        d.contains("youtube") || t.contains("youtube") -> Box(
            modifier = modifier
                .size(38.dp)
                .background(if (isDark) Color(0xFF262628) else Color(0xFFF9FAFB), shape = CircleShape)
                .border(1.dp, if (isDark) Color(0xFF38383C) else Color(0xFFEAEAEA), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            YouTubeLogo(Modifier.size(26.dp))
        }
        d.contains("anthropic") || t.contains("anthropic") -> AnthropicAiLogo(modifier)
        d.contains("github") || t.contains("github") -> Box(
            modifier = modifier
                .size(38.dp)
                .background(if (isDark) Color(0xFF262628) else Color.White, shape = CircleShape)
                .border(1.dp, if (isDark) Color(0xFF38383C) else Color(0xFFEAEAEA), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            GitHubLogo(Modifier.size(24.dp))
        }
        d.contains("drive.google") || t.contains("drive") -> Box(
            modifier = modifier
                .size(38.dp)
                .background(if (isDark) Color(0xFF262628) else Color.White, shape = CircleShape)
                .border(1.dp, if (isDark) Color(0xFF38383C) else Color(0xFFEAEAEA), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            GoogleDriveLogo(Modifier.size(24.dp))
        }
        else -> {
            val letter = (title.ifBlank { domain }).trim().firstOrNull()?.uppercaseChar()?.toString() ?: "W"
            Box(
                modifier = modifier
                    .size(38.dp)
                    .background(if (isDark) Color(0xFF262628) else Color(0xFFF4F4F5), shape = CircleShape)
                    .border(1.dp, if (isDark) Color(0xFF38383C) else Color(0xFFE4E4E7), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )
            }
        }
    }
}

// =========================================================================
// MONOCHROME THEME PALETTE (Day & Night View Optimization)
// =========================================================================

object BrowserThemeColors {
    fun background(isDark: Boolean): Color = if (isDark) Color(0xFF111111) else Color(0xFFFFFFFF)
    fun barSurface(isDark: Boolean): Color = if (isDark) Color(0xFF1E1E20) else Color(0xFFFFFFFF)
    fun barOutline(isDark: Boolean): Color = if (isDark) Color(0xFF38383A) else Color(0xFFE5E7EB)
    fun cardSurface(isDark: Boolean): Color = if (isDark) Color(0xFF1E1E20) else Color(0xFFFFFFFF)
    fun cardOutline(isDark: Boolean): Color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFEFEFEF)
    fun itemSurface(isDark: Boolean): Color = if (isDark) Color(0xFF262628) else Color(0xFFF9FAFB)
    fun itemOutline(isDark: Boolean): Color = if (isDark) Color(0xFF38383C) else Color(0xFFEEEEEE)
    fun textPrimary(isDark: Boolean): Color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF000000)
    fun textSecondary(isDark: Boolean): Color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)
    fun iconTint(isDark: Boolean): Color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF000000)
    fun secondaryIconTint(isDark: Boolean): Color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)
    fun badgeBg(isDark: Boolean): Color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF000000)
    fun badgeText(isDark: Boolean): Color = if (isDark) Color(0xFF111111) else Color(0xFFFFFFFF)
    fun divider(isDark: Boolean): Color = if (isDark) Color(0xFF27272A) else Color(0xFFF3F4F6)
    fun inputHint(isDark: Boolean): Color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B7280)
}

// =========================================================================
// 1. TOP BAR (Minimalist Monochrome Floating Pill with Discover Badge & Controls)
// =========================================================================

@Composable
