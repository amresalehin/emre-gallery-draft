package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.text.SimpleDateFormat
import java.util.*


fun formatRelativeBrowserTime(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        diff < 60_000L -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> if (hours == 1L) "1 hr ago" else "$hours hr ago"
        days < 7 -> if (days == 1L) "1 day ago" else "$days days ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
    }
}

// =========================================================================
// BRAND LOGOS & FAVICONS (High fidelity, offline-ready Compose vector art)
// =========================================================================

@Composable
fun NorthEastArrowIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF71717A)
) {
    Canvas(modifier = modifier.size(17.dp)) {
        val strokeW = 1.8.dp.toPx()
        val cap = StrokeCap.Round
        val start = Offset(size.width * 0.24f, size.height * 0.76f)
        val end = Offset(size.width * 0.76f, size.height * 0.24f)
        // Diagonal shaft
        drawLine(color = tint, start = start, end = end, strokeWidth = strokeW, cap = cap)
        // Horizontal barb
        drawLine(color = tint, start = Offset(size.width * 0.42f, size.height * 0.24f), end = end, strokeWidth = strokeW, cap = cap)
        // Vertical barb
        drawLine(color = tint, start = Offset(size.width * 0.76f, size.height * 0.58f), end = end, strokeWidth = strokeW, cap = cap)
    }
}

@Composable
fun YouTubeLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(Color(0xFFFF0000), shape = RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(14.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.15f)
                lineTo(size.width * 0.82f, size.height * 0.5f)
                lineTo(size.width * 0.28f, size.height * 0.85f)
                close()
            }
            drawPath(path, color = Color.White)
        }
    }
}

@Composable
fun WikipediaLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(Color.White, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "W",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            color = Color.Black
        )
    }
}

@Composable
fun GitHubLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(Color.White, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            // Draw head circle
            drawCircle(
                color = Color.Black,
                radius = size.width * 0.38f,
                center = Offset(size.width * 0.5f, size.height * 0.54f)
            )
            // Left ear
            val leftEar = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.38f)
                lineTo(size.width * 0.15f, size.height * 0.14f)
                lineTo(size.width * 0.42f, size.height * 0.22f)
                close()
            }
            drawPath(leftEar, color = Color.Black)
            // Right ear
            val rightEar = Path().apply {
                moveTo(size.width * 0.78f, size.height * 0.38f)
                lineTo(size.width * 0.85f, size.height * 0.14f)
                lineTo(size.width * 0.58f, size.height * 0.22f)
                close()
            }
            drawPath(rightEar, color = Color.Black)
        }
    }
}

@Composable
fun GoogleDriveLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(Color.White, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val yellow = Color(0xFFFFBA00)
            val green = Color(0xFF00AC47)
            val blue = Color(0xFF0066DA)

            val pYellow = Path().apply {
                moveTo(size.width * 0.35f, size.height * 0.18f)
                lineTo(size.width * 0.88f, size.height * 0.18f)
                lineTo(size.width * 0.65f, size.height * 0.55f)
                lineTo(size.width * 0.12f, size.height * 0.55f)
                close()
            }
            drawPath(pYellow, color = yellow)

            val pGreen = Path().apply {
                moveTo(size.width * 0.12f, size.height * 0.55f)
                lineTo(size.width * 0.35f, size.height * 0.18f)
                lineTo(size.width * 0.50f, size.height * 0.45f)
                lineTo(size.width * 0.26f, size.height * 0.85f)
                close()
            }
            drawPath(pGreen, color = green)

            val pBlue = Path().apply {
                moveTo(size.width * 0.26f, size.height * 0.85f)
                lineTo(size.width * 0.78f, size.height * 0.85f)
                lineTo(size.width * 0.92f, size.height * 0.60f)
                lineTo(size.width * 0.40f, size.height * 0.60f)
                close()
            }
            drawPath(pBlue, color = blue)
        }
    }
}

@Composable
