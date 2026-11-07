package com.amresalehin.emregallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.CloudAiProvider
import com.amresalehin.emregallery.data.CloudProviderStatus
import com.amresalehin.emregallery.data.FlorenceModelManager
import com.amresalehin.emregallery.data.FlorenceModelState
import com.amresalehin.emregallery.data.FlorenceStatus
import com.amresalehin.emregallery.data.PreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun AiConfigurationCard(
    modifier: Modifier = Modifier,
    prefs: PreferencesRepository,
    onShowSnackbar: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cloudProviders by prefs.cloudProviders.collectAsState(initial = emptyList())
    val activeProvider by prefs.activeCloudProvider.collectAsState(initial = null)
    val florenceState by prefs.florenceModelState.collectAsState(initial = FlorenceModelState())

    var providerName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }

    var isFormExpanded by remember { mutableStateOf(true) }
    var isTestingCloud by remember { mutableStateOf(false) }
    var cloudTestFeedback by remember { mutableStateOf<String?>(null) }
    var showFlorenceDetailsDialog by remember { mutableStateOf(false) }

    // Synchronize initial Florence model state on mount
    LaunchedEffect(Unit) {
        FlorenceModelManager.refreshState(context)
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_configuration_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ==================== CLOUD SECTION ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cloud,
                        contentDescription = "Cloud AI",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Cloud",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Dynamic status-indicator button for Cloud
                    DynamicCloudStatusIndicator(
                        activeProvider = activeProvider,
                        isTesting = isTestingCloud,
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
                                isFormExpanded = true
                                onShowSnackbar?.invoke("Please configure a Cloud provider below")
                            }
                        }
                    )

                    // Template '+' button
                    IconButton(
                        onClick = {
                            isFormExpanded = !isFormExpanded
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("ai_cloud_add_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isFormExpanded && cloudProviders.isNotEmpty()) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "Toggle Cloud form",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Quick preset chips & compact input form
            AnimatedVisibility(visible = isFormExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Quick presets row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(
                            Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o"),
                            Triple("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
                            Triple("Ollama", "http://10.0.2.2:11434/v1", "llama3.2"),
                            Triple("OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.2-3b-instruct")
                        )
                        presets.forEach { (name, url, model) ->
                            SuggestionChip(
                                onClick = {
                                    providerName = name
                                    baseUrl = url
                                    modelId = model
                                },
                                label = { Text(name, fontSize = 11.sp) },
                                shape = CircleShape,
                                modifier = Modifier.height(26.dp),
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                border = null
                            )
                        }
                    }

                    // 1. Provider name input (compact)
                    CompactAiInputField(
                        value = providerName,
                        onValueChange = { providerName = it },
                        placeholder = "Provider name",
                        testTag = "ai_provider_name_input"
                    )

                    // 2. Base URL input (compact)
                    CompactAiInputField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        placeholder = "Base URL",
                        testTag = "ai_base_url_input"
                    )

                    // 3. API key input (compact)
                    CompactAiInputField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        placeholder = "API key",
                        isPassword = true,
                        showPassword = showApiKey,
                        onToggleShowPassword = { showApiKey = !showApiKey },
                        testTag = "ai_api_key_input"
                    )

                    // 4. Model ID input (compact)
                    CompactAiInputField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        placeholder = "Model ID",
                        testTag = "ai_model_id_input"
                    )

                    // "Add provider" Button (compact pill button)
                    Button(
                        onClick = {
                            val name = providerName.trim()
                            val url = baseUrl.trim()
                            val model = modelId.trim()
                            if (name.isBlank() || url.isBlank() || model.isBlank()) {
                                onShowSnackbar?.invoke("Please fill in Provider name, Base URL, and Model ID")
                                return@Button
                            }

                            val newProvider = CloudAiProvider(
                                name = name,
                                baseUrl = url,
                                apiKey = apiKey.trim(),
                                modelId = model,
                                isActive = true,
                                status = CloudProviderStatus.UNCONFIGURED
                            )

                            scope.launch {
                                prefs.saveCloudProvider(newProvider)
                                onShowSnackbar?.invoke("Provider '$name' added")
                                providerName = ""
                                baseUrl = ""
                                apiKey = ""
                                modelId = ""
                                isTestingCloud = true
                                prefs.testCloudProvider(newProvider.id)
                                isTestingCloud = false
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("ai_add_provider_button"),
                        contentPadding = PaddingValues(vertical = 0.dp)
                    ) {
                        Text(
                            text = "Add provider",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Existing Configured Providers List (if any)
            if (cloudProviders.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Configured Providers",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    cloudProviders.forEach { provider ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (provider.isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (provider.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { prefs.setActiveCloudProvider(provider.id) }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = provider.isActive,
                                        onClick = {
                                            scope.launch { prefs.setActiveCloudProvider(provider.id) }
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            provider.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${provider.modelId} • ${provider.baseUrl.removePrefix("https://").removePrefix("http://").take(22)}...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                prefs.testCloudProvider(provider.id)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Test connection",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                prefs.deleteCloudProvider(provider.id)
                                                onShowSnackbar?.invoke("Provider '${provider.name}' removed")
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Remove provider",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==================== HORIZONTAL DIVIDER ====================
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            // ==================== LOCAL SECTION ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Smartphone,
                    contentDescription = "Local AI",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Local",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Florence-2 Model Item Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Florence-2",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (florenceState.status) {
                            FlorenceStatus.NOT_INSTALLED -> "Vision-Language model (~38 MB)"
                            FlorenceStatus.DOWNLOADING -> "Downloading weights and config..."
                            FlorenceStatus.VERIFYING -> "Verifying model artifacts..."
                            FlorenceStatus.INSTALLED -> "Ready for on-device inference"
                            FlorenceStatus.ERROR -> florenceState.errorMessage ?: "Installation failed"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (florenceState.status == FlorenceStatus.ERROR) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Dynamic Status-Indicator Button for Florence-2
                DynamicFlorenceStatusButton(
                    state = florenceState,
                    onInstall = {
                        scope.launch {
                            FlorenceModelManager.downloadAndInstall(context)
                        }
                    },
                    onCancel = {
                        FlorenceModelManager.cancelDownload(context)
                    },
                    onDetails = {
                        showFlorenceDetailsDialog = true
                    }
                )
            }

            // Progress bar when downloading
            if (florenceState.status == FlorenceStatus.DOWNLOADING || florenceState.status == FlorenceStatus.VERIFYING) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { florenceState.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (florenceState.totalBytes > 0) {
                                "${formatBytes(florenceState.bytesDownloaded)} / ${formatBytes(florenceState.totalBytes)}"
                            } else {
                                "Downloading..."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(florenceState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // Florence-2 Details / Options Dialog
    if (showFlorenceDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showFlorenceDetailsDialog = false },
            title = {
                Text(
                    "Florence-2 Local Model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Status: ${if (florenceState.status == FlorenceStatus.INSTALLED) "Installed & Validated" else "Not installed"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Model: Microsoft Florence-2 Base (ONNX)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (florenceState.installedSizeBytes > 0) {
                        Text(
                            "Storage footprint: ${formatBytes(florenceState.installedSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    florenceState.installedPath?.let { path ->
                        Text(
                            "Path: $path",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFlorenceDetailsDialog = false }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            FlorenceModelManager.uninstall(context)
                            showFlorenceDetailsDialog = false
                            onShowSnackbar?.invoke("Florence-2 uninstalled")
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Uninstall")
                }
            }
        )
    }
}

/**
 * Compact rounded outlined input field matching the template pill styling.
 */
@Composable
private fun CompactAiInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onToggleShowPassword: (() -> Unit)? = null,
    testTag: String = ""
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (isPassword && value.isNotEmpty()) {
                IconButton(
                    onClick = { onToggleShowPassword?.invoke() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide API key" else "Show API key",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dynamic Status-Indicator Button for Cloud State
 */
@Composable
private fun DynamicCloudStatusIndicator(
    activeProvider: CloudAiProvider?,
    isTesting: Boolean,
    onClick: () -> Unit
) {
    val status = when {
        isTesting -> CloudProviderStatus.CHECKING
        activeProvider == null -> CloudProviderStatus.UNCONFIGURED
        else -> activeProvider.status
    }

    val (bg, border, contentColor) = when (status) {
        CloudProviderStatus.CONNECTED -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF81C784),
            Color(0xFF2E7D32)
        )
        CloudProviderStatus.CHECKING -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.primary
        )
        CloudProviderStatus.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.error
        )
        CloudProviderStatus.UNCONFIGURED -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .height(30.dp)
            .testTag("ai_cloud_status_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            when (status) {
                CloudProviderStatus.CONNECTED -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Cloud Connected",
                        modifier = Modifier.size(14.dp),
                        tint = contentColor
                    )
                    Text(
                        text = activeProvider?.name?.ifBlank { "Connected" } ?: "Connected",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                }
                CloudProviderStatus.CHECKING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.8.dp,
                        color = contentColor
                    )
                    Text(
                        text = "Testing...",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                }
                CloudProviderStatus.ERROR -> {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Cloud Error",
                        modifier = Modifier.size(14.dp),
                        tint = contentColor
                    )
                    Text(
                        text = "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                }
                CloudProviderStatus.UNCONFIGURED -> {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = "Cloud Not Configured",
                        modifier = Modifier.size(14.dp),
                        tint = contentColor
                    )
                    Text(
                        text = "Not set",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

/**
 * Dynamic Status-Indicator Button for Florence-2 Local Model
 */
@Composable
private fun DynamicFlorenceStatusButton(
    state: FlorenceModelState,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDetails: () -> Unit
) {
    when (state.status) {
        FlorenceStatus.NOT_INSTALLED -> {
            OutlinedButton(
                onClick = onInstall,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("florence_install_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = "Install Florence-2",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "Install",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        FlorenceStatus.DOWNLOADING, FlorenceStatus.VERIFYING -> {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("florence_downloading_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { state.progress.coerceIn(0.05f, 1f) },
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 1.8.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel download",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        FlorenceStatus.INSTALLED -> {
            Surface(
                onClick = onDetails,
                shape = CircleShape,
                color = Color(0xFFE8F5E9),
                border = BorderStroke(1.dp, Color(0xFF81C784)),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("florence_installed_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Installed",
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFF2E7D32)
                    )
                    Text(
                        text = "Installed",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }

        FlorenceStatus.ERROR -> {
            OutlinedButton(
                onClick = onInstall,
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("florence_retry_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry download",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
