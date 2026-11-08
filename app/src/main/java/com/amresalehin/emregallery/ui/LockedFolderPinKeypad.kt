package com.amresalehin.emregallery.ui
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class PinSpecialAction {
    REENTER,
    CONFIRM
}

@Composable
internal fun PinKeypad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    showBiometric: Boolean,
    onBiometricClick: () -> Unit,
    specialAction: PinSpecialAction? = null,
    onSpecialActionClick: () -> Unit = {},
    enteredPinNotEmpty: Boolean = false
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("action", "0", "backspace")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 44.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                row.forEach { key ->
                    when (key) {
                        "action" -> {
                            if (showBiometric) {
                                IconButton(
                                    onClick = onBiometricClick,
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Fingerprint,
                                        contentDescription = "Unlock with biometrics",
                                        modifier = Modifier.size(34.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                Spacer(Modifier.size(68.dp))
                            }
                        }
                        "backspace" -> {
                            when (specialAction) {
                                PinSpecialAction.REENTER -> {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        onClick = onSpecialActionClick,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .testTag("pin_reenter_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = "Re-enter",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Text(
                                                    text = "Re-enter",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                                PinSpecialAction.CONFIRM -> {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        onClick = onSpecialActionClick,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .testTag("pin_confirm_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Confirm",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Text(
                                                    text = "Confirm",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                                null -> {
                                    IconButton(
                                        onClick = onBackspaceClick,
                                        enabled = enteredPinNotEmpty,
                                        modifier = Modifier.size(68.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Backspace,
                                            contentDescription = "Delete digit",
                                            modifier = Modifier.size(26.dp),
                                            tint = if (enteredPinNotEmpty) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                onClick = { onDigitClick(key) },
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
