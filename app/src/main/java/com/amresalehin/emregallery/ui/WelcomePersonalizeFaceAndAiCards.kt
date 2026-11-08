package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


internal fun PersonalizeFaceRecognitionCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    selectedEngineOption: String,
    onSelectEngineOption: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0x75F5F7FA),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("welcome_personalize_face_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row (Clickable to expand/collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.60f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FaceRecognitionIcon(tint = Color(0xFF181C22))
                    }
                }

                Spacer(Modifier.width(16.dp))

                Text(
                    text = "Face Recognition",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF14171A)
                    ),
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(22.dp),
                    tint = Color(0xFF38404A)
                )
            }

            // Expanded Content
            if (isExpanded) {
                HorizontalDivider(
                    color = Color(0x1F000000),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    // Engine Label
                    Text(
                        text = "Engine",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF14171B)
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    // Engine Segmented Buttons: Disable | Yolo V5 | Buffalo L
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0x0F000000),
                        border = BorderStroke(1.dp, Color(0x1F000000)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SegmentButton(
                                text = "Disable",
                                isSelected = selectedEngineOption == "disable",
                                onClick = { onSelectEngineOption("disable") },
                                modifier = Modifier.weight(1f)
                            )

                            SegmentButton(
                                text = "Yolo V5",
                                isSelected = selectedEngineOption == "yolo",
                                onClick = { onSelectEngineOption("yolo") },
                                modifier = Modifier.weight(1f)
                            )

                            SegmentButton(
                                text = "Buffalo L",
                                isSelected = selectedEngineOption == "buffalo",
                                onClick = { onSelectEngineOption("buffalo") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
internal fun PersonalizeAiConfigCard() {
    // "The AI config should be disabled and greyed out."
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0x60F5F7FA),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.45f)
            .testTag("welcome_personalize_ai_config_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AiIcon(tint = Color(0xFF181C22))
                }
            }

            Spacer(Modifier.width(16.dp))

            Text(
                text = "AI Configuration",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF14171A)
                ),
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color(0xFF38404A)
            )
        }
    }
}

@Composable
