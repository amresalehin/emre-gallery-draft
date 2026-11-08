package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import com.amresalehin.emregallery.ocr.MlKitLanguage
import com.amresalehin.emregallery.ocr.TesseractOcr


internal fun PersonalizeOcrCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    selectedEngine: String,
    onSelectEngine: (String) -> Unit,
    selectedMlKitLangs: Set<String>,
    onToggleMlKitLang: (String) -> Unit,
    selectedOcrLangs: Set<String>,
    onToggleOcrLang: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0x75F5F7FA),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("welcome_personalize_ocr_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row (Always visible and clickable to expand/collapse)
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
                        OcrIcon(tint = Color(0xFF181C22))
                    }
                }

                Spacer(Modifier.width(16.dp))

                Text(
                    text = "OCR",
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

                    // Engine Segmented Buttons: ML Kit | Tesseract
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
                            // ML Kit Option
                            SegmentButton(
                                text = "ML Kit",
                                isSelected = selectedEngine == "mlkit",
                                onClick = { onSelectEngine("mlkit") },
                                modifier = Modifier.weight(1f)
                            )

                            // Tesseract Option
                            SegmentButton(
                                text = "Tesseract",
                                isSelected = selectedEngine == "tesseract",
                                onClick = { onSelectEngine("tesseract") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Language(s) Label
                    Text(
                        text = "Language(s)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF14171B)
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    // Language Chips Row
                    if (selectedEngine == "mlkit") {
                        val mlKitOptions = remember {
                            MlKitLanguage.entries.map { it.code to it.title }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .testTag("welcome_mlkit_languages_row"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            mlKitOptions.forEach { (code, label) ->
                                val isSelected = code in selectedMlKitLangs
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) Color(0xFF161A20) else Color.White.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF161A20) else Color(0x33485666)
                                    ),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .defaultMinSize(minWidth = 72.dp)
                                        .clickable { onToggleMlKitLang(code) }
                                        .testTag("welcome_mlkit_lang_$code")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else Color(0xFF14171B)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val tesseractOptions = remember {
                            TesseractOcr.supportedLanguages.entries.map { it.value to it.key }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .testTag("welcome_tesseract_languages_row"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tesseractOptions.forEach { (code, label) ->
                                val isSelected = code in selectedOcrLangs
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) Color(0xFF161A20) else Color.White.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF161A20) else Color(0x33485666)
                                    ),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .defaultMinSize(minWidth = 72.dp)
                                        .clickable { onToggleOcrLang(code) }
                                        .testTag("welcome_ocr_lang_$code")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else Color(0xFF14171B)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
