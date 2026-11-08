package com.amresalehin.emregallery.ui

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.R


@Composable
internal fun WelcomePermissionsStepContent(
    mediaGranted: Boolean,
    notificationGranted: Boolean,
    allFilesGranted: Boolean,
    allPermissionsGranted: Boolean,
    buttonText: String,
    onRequestMedia: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestAllFiles: () -> Unit,
    onComplete: () -> Unit
) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(36.dp))

                        // Soft circular halo with Emre logo
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.50f),
                            modifier = Modifier.size(84.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_app_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(38.dp),
                                    tint = Color(0xFF161A20)
                                )
                            }
                        }

                        Spacer(Modifier.height(28.dp))

                        // Editorial Serif Headline
                        Text(
                            text = "Let’s set things up",
                            fontFamily = FontFamily.Serif,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF14171B)
                        )

                        Spacer(Modifier.height(32.dp))

                        // Card 1: Photos and videos
                        PermissionCard(
                            icon = Icons.Outlined.Photo,
                            title = "Photos and videos",
                            isGranted = mediaGranted,
                            onClick = { onRequestMedia() },
                            testTag = "welcome_permission_media"
                        )

                        Spacer(Modifier.height(14.dp))

                        // Card 2: Notifications
                        PermissionCard(
                            icon = Icons.Outlined.Notifications,
                            title = "Notifications",
                            isGranted = notificationGranted,
                            onClick = { onRequestNotifications() },
                            testTag = "welcome_permission_notifications"
                        )

                        Spacer(Modifier.height(14.dp))

                        // Card 3: All files access
                        PermissionCard(
                            icon = Icons.Outlined.FolderCopy,
                            title = "All files access",
                            isGranted = allFilesGranted,
                            onClick = { onRequestAllFiles() },
                            testTag = "welcome_permission_all_files"
                        )

                        Spacer(Modifier.weight(1f))

                        // Dynamic Action Button:
                        // "Liberate Emre" (0 granted) -> "Growing Wings" (1 or 2 granted) -> "Enter Emre" (all 3 granted)
                        // Arrow appears ONLY on "Enter Emre"
                        Button(
                            onClick = {
                                if (allPermissionsGranted) {
                                    onComplete()
                                } else if (!mediaGranted) {
                                    onRequestMedia()
                                } else if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    onRequestNotifications()
                                } else if (!allFilesGranted) {
                                    onRequestAllFiles()
                                }
                            },
                            shape = RoundedCornerShape(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (allPermissionsGranted) Color.White else Color(0x33485666),
                                contentColor = if (allPermissionsGranted) Color(0xFF14171B) else Color.White
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (allPermissionsGranted) Color.White else Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(bottom = 4.dp)
                                .testTag("welcome_continue_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = buttonText,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (allPermissionsGranted) Color(0xFF14171B) else Color.White
                                    )
                                )
                                if (allPermissionsGranted) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = Color(0xFF14171B)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(28.dp))
                    }
}
