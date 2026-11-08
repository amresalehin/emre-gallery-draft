package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.R


@Composable
internal fun PersonalizeStep(
    onSkip: () -> Unit,
    onSave: (ocrEngine: String, mlKitLangs: Set<String>, ocrLangs: Set<String>, faceEngineOption: String) -> Unit
) {
    var ocrExpanded by remember { mutableStateOf(false) }
    var faceExpanded by remember { mutableStateOf(false) }

    var selectedOcrEngine by remember { mutableStateOf("mlkit") } // "mlkit" or "tesseract"
    var selectedMlKitLanguages by remember { mutableStateOf(setOf("latin")) }
    var selectedOcrLanguages by remember { mutableStateOf(setOf("eng")) }
    var selectedFaceEngineOption by remember { mutableStateOf("disable") } // "disable", "yolo", "buffalo"

    val isAnyExpanded = ocrExpanded || faceExpanded
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("welcome_personalize_step")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            // Soft circular halo with Emre logo
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.50f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_app_logo),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = Color(0xFF161A20)
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            // Editorial Serif Headline
            Text(
                text = "Let’s Personalize",
                fontFamily = FontFamily.Serif,
                fontSize = 32.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF14171B)
            )

            Spacer(Modifier.height(26.dp))

            // Card 1: OCR (Expandable)
            PersonalizeOcrCard(
                isExpanded = ocrExpanded,
                onToggleExpand = {
                    val willExpand = !ocrExpanded
                    ocrExpanded = willExpand
                    if (willExpand) faceExpanded = false // Smart Accordion: keep one section open at a time
                },
                selectedEngine = selectedOcrEngine,
                onSelectEngine = { selectedOcrEngine = it },
                selectedMlKitLangs = selectedMlKitLanguages,
                onToggleMlKitLang = { langCode ->
                    selectedMlKitLanguages = if (langCode in selectedMlKitLanguages) {
                        if (selectedMlKitLanguages.size > 1) selectedMlKitLanguages - langCode else selectedMlKitLanguages
                    } else {
                        selectedMlKitLanguages + langCode
                    }
                },
                selectedOcrLangs = selectedOcrLanguages,
                onToggleOcrLang = { langCode ->
                    selectedOcrLanguages = if (langCode in selectedOcrLanguages) {
                        if (selectedOcrLanguages.size > 1) selectedOcrLanguages - langCode else selectedOcrLanguages
                    } else {
                        selectedOcrLanguages + langCode
                    }
                }
            )

            Spacer(Modifier.height(14.dp))

            // Card 2: Face Recognition (Expandable)
            PersonalizeFaceRecognitionCard(
                isExpanded = faceExpanded,
                onToggleExpand = {
                    val willExpand = !faceExpanded
                    faceExpanded = willExpand
                    if (willExpand) ocrExpanded = false // Smart Accordion: keep one section open at a time
                },
                selectedEngineOption = selectedFaceEngineOption,
                onSelectEngineOption = { selectedFaceEngineOption = it }
            )

            Spacer(Modifier.height(14.dp))

            // Card 3: AI Configuration (Disabled and greyed out per instructions)
            PersonalizeAiConfigCard()

            // Generous bottom spacer so that scrolled content never gets obscured behind buttons
            Spacer(Modifier.height(116.dp))
        }

        // Pinned Bottom Navigation Buttons with soft gradient scrim
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.32f to Color(0x99161A20),
                        1.0f to Color(0xF2161A20)
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp)
        ) {
            if (isAnyExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip Button
                    Button(
                        onClick = onSkip,
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x1F14171B),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("welcome_personalize_skip_button")
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                    }

                    // Save Button
                    Button(
                        onClick = {
                            onSave(
                                selectedOcrEngine,
                                selectedMlKitLanguages,
                                selectedOcrLanguages,
                                selectedFaceEngineOption
                            )
                        },
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF14171B)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("welcome_personalize_save_button")
                    ) {
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF14171B)
                            )
                        )
                    }
                }
            } else {
                // Single Skip Button
                Button(
                    onClick = onSkip,
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x1F14171B),
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("welcome_personalize_skip_button")
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
