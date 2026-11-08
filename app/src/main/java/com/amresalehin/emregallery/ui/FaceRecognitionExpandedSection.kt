package com.amresalehin.emregallery.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.EnteFaceConstants
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import com.amresalehin.emregallery.face.StageState
import java.text.NumberFormat
import kotlin.math.roundToInt


@Composable
internal fun FaceRecognitionExpandedContent(
    selectedOption: String,
    onSelectOption: (String) -> Unit,
    currentFindingState: FaceFindingState,
    availableFolders: List<FaceFolderInfo>,
    onSelectAllFolders: () -> Unit,
    onToggleFolder: (String) -> Unit
) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    // Engine Label (No explanatory text)
                    Text(
                        text = "Engine",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    // Engine Segmented Buttons: Disable | Yolo V5 | Buffalo L
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
                            FaceSegmentOption(
                                text = "Disable",
                                isSelected = selectedOption == "disable",
                                onClick = { onSelectOption("disable") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("face_engine_segment_disable")
                            )

                            VerticalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxHeight(0.6f).width(1.dp)
                            )

                            FaceSegmentOption(
                                text = "Yolo V5",
                                isSelected = selectedOption == "yolo",
                                onClick = { onSelectOption("yolo") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("face_engine_segment_yolo")
                            )

                            VerticalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxHeight(0.6f).width(1.dp)
                            )

                            FaceSegmentOption(
                                text = "Buffalo L",
                                isSelected = selectedOption == "buffalo",
                                onClick = { onSelectOption("buffalo") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("face_engine_segment_buffalo")
                            )
                        }
                    }

                    if (selectedOption != "disable") {
                        Spacer(Modifier.height(16.dp))

                        // Folders Header Row: Icon + "Folders" on left, "All folders" pill on right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Folders",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            // "All folders" toggle pill
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (currentFindingState.isAllFolders) Color(0xFF161A20) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    if (currentFindingState.isAllFolders) Color(0xFF161A20) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .height(36.dp)
                                    .clickable(onClick = onSelectAllFolders)
                                    .testTag("face_folders_all_pill")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                ) {
                                    Text(
                                        text = "All folders",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 13.sp,
                                            fontWeight = if (currentFindingState.isAllFolders) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (currentFindingState.isAllFolders) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Horizontally scrollable row of real device folders
                        if (availableFolders.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .testTag("face_folders_horizontal_row"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableFolders.forEach { folder ->
                                    val isSelected = !currentFindingState.isAllFolders && folder.path in currentFindingState.selectedFolders
                                    FolderChip(
                                        label = folder.name,
                                        isSelected = isSelected,
                                        onClick = { onToggleFolder(folder.path) },
                                        testTag = "face_folder_chip_${folder.name}"
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                }
}
