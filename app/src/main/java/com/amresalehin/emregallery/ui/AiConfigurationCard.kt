package com.amresalehin.emregallery.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.CloudProviderStatus
import com.amresalehin.emregallery.data.FlorenceModelManager
import com.amresalehin.emregallery.data.FlorenceModelState
import com.amresalehin.emregallery.data.PreferencesRepository
import kotlinx.coroutines.launch

internal enum class AiModeTab {
    CLOUD,
    LOCAL
}

/**
 * Sleek, minimal AI Configuration card designed to match the Welcome Screen cards aesthetic.
 * Features rounded 26.dp corners, frosted/clean surface styling, circular avatar halo with Emre's
 * signature corner-bracket icon, segmented button controls, and a minimal layout free of redundant
 * explanatory subtitles. Cloud setup is collapsed by default.
 */
@Composable
fun AiConfigurationCard(
    modifier: Modifier = Modifier,
    prefs: PreferencesRepository,
    cardBackground: Color? = null,
    cardBorder: BorderStroke? = null,
    onShowSnackbar: ((String) -> Unit)? = null,
    initiallyExpanded: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cloudProviders by prefs.cloudProviders.collectAsState(initial = emptyList())
    val activeProvider by prefs.activeCloudProvider.collectAsState(initial = null)
    val florenceState by prefs.florenceModelState.collectAsState(initial = FlorenceModelState())
    val allModelStates by FlorenceModelManager.allModelStates.collectAsState()

    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(initiallyExpanded) {
        if (initiallyExpanded) {
            isExpanded = true
        }
    }
    var selectedTab by remember { mutableStateOf(AiModeTab.CLOUD) }

    var providerName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }

    var isTestingCloud by remember { mutableStateOf(false) }
    var cloudTestFeedback by remember { mutableStateOf<String?>(null) }
    var selectedModelForDetails by remember { mutableStateOf<FlorenceModelManager.LocalModelSpec?>(null) }

    // Synchronize initial Local models state on mount
    LaunchedEffect(Unit) {
        FlorenceModelManager.refreshAllStates(context)
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val effectiveCardBackground = cardBackground ?: if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        Color(0x75F5F7FA)
    }

    val effectiveCardBorder = cardBorder ?: BorderStroke(
        1.dp,
        if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        else Color.White.copy(alpha = 0.65f)
    )

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = effectiveCardBackground,
        border = effectiveCardBorder,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("ai_configuration_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row (Clickable to expand / collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Halo with Emre AI Signature Icon
                Surface(
                    shape = CircleShape,
                    color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    else Color.White.copy(alpha = 0.60f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AiSignatureIcon(tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Clean title without explanatory text below it
                Text(
                    text = "AI Configuration",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Header status badge / test pill when collapsed or expanded
                DynamicCloudStatusIndicator(
                    activeProvider = activeProvider,
                    isTesting = isTestingCloud,
                    isDark = isDark,
                    onClick = {
                        if (activeProvider != null) {
                            scope.launch {
                                isTestingCloud = true
                                cloudTestFeedback = "Testing connection..."
                                val status = prefs.testActiveCloudProvider()
                                isTestingCloud = false
                                cloudTestFeedback = if (status == CloudProviderStatus.CONNECTED) {
                                    "Connected successfully"
                                } else {
                                    activeProvider?.statusMessage ?: "Connection test failed"
                                }
                                onShowSnackbar?.invoke(cloudTestFeedback ?: "")
                            }
                        } else {
                            isExpanded = true
                            selectedTab = AiModeTab.CLOUD
                            onShowSnackbar?.invoke("Please configure a Cloud provider below")
                        }
                    }
                )

                Spacer(Modifier.width(6.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(22.dp)
                        .testTag("ai_expand_toggle"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded Content
            if (isExpanded) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    // Segmented Button: Cloud | Local
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
                            AiSegmentOption(
                                text = "Cloud",
                                isSelected = selectedTab == AiModeTab.CLOUD,
                                isDark = isDark,
                                onClick = { selectedTab = AiModeTab.CLOUD },
                                modifier = Modifier.weight(1f)
                            )

                            VerticalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxHeight(0.6f)
                                    .width(1.dp)
                            )

                            AiSegmentOption(
                                text = "Local",
                                isSelected = selectedTab == AiModeTab.LOCAL,
                                isDark = isDark,
                                onClick = { selectedTab = AiModeTab.LOCAL },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ==========================================
                    // TAB 1: CLOUD
                    // ==========================================
                    if (selectedTab == AiModeTab.CLOUD) {
                        AiCloudTabContent(
                            isDark = isDark,
                            prefs = prefs,
                            cloudProviders = cloudProviders,
                            providerName = providerName,
                            onProviderNameChange = { providerName = it },
                            baseUrl = baseUrl,
                            onBaseUrlChange = { baseUrl = it },
                            apiKey = apiKey,
                            onApiKeyChange = { apiKey = it },
                            showApiKey = showApiKey,
                            onToggleShowApiKey = { showApiKey = !showApiKey },
                            modelId = modelId,
                            onModelIdChange = { modelId = it },
                            onIsTestingCloudChange = { isTestingCloud = it },
                            onClearInputs = {
                                providerName = ""
                                baseUrl = ""
                                apiKey = ""
                                modelId = ""
                            },
                            onShowSnackbar = onShowSnackbar
                        )
                    }

                    if (selectedTab == AiModeTab.LOCAL) {
                        AiLocalModelsTabContent(
                            isDark = isDark,
                            allModelStates = allModelStates,
                            onSelectModelForDetails = { selectedModelForDetails = it }
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    AiLocalModelDetailsDialog(
        selectedModelForDetails = selectedModelForDetails,
        allModelStates = allModelStates,
        onDismiss = { selectedModelForDetails = null },
        onShowSnackbar = onShowSnackbar
    )
}
