package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.CloudAiProvider
import com.amresalehin.emregallery.data.CloudProviderStatus
import com.amresalehin.emregallery.data.PreferencesRepository
import kotlinx.coroutines.launch


@Composable
internal fun AiCloudTabContent(
    isDark: Boolean,
    prefs: PreferencesRepository,
    cloudProviders: List<CloudAiProvider>,
    providerName: String,
    onProviderNameChange: (String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    showApiKey: Boolean,
    onToggleShowApiKey: () -> Unit,
    modelId: String,
    onModelIdChange: (String) -> Unit,
    onIsTestingCloudChange: (Boolean) -> Unit,
    onClearInputs: () -> Unit,
    onShowSnackbar: ((String) -> Unit)?
) {
    val scope = rememberCoroutineScope()
                    run {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. Provider name input
                            MinimalAiInputField(
                                value = providerName,
                                onValueChange = onProviderNameChange,
                                placeholder = "Provider name (e.g. OpenAI)",
                                icon = Icons.Outlined.Label,
                                testTag = "ai_provider_name_input"
                            )

                            // 2. Base URL input
                            MinimalAiInputField(
                                value = baseUrl,
                                onValueChange = onBaseUrlChange,
                                placeholder = "Base URL (e.g. https://api.openai.com/v1)",
                                icon = Icons.Outlined.Link,
                                testTag = "ai_base_url_input"
                            )

                            // 3. API key input
                            MinimalAiInputField(
                                value = apiKey,
                                onValueChange = onApiKeyChange,
                                placeholder = "API key",
                                icon = Icons.Outlined.Key,
                                isPassword = true,
                                showPassword = showApiKey,
                                onToggleShowPassword = onToggleShowApiKey,
                                testTag = "ai_api_key_input"
                            )

                            // 4. Model ID input
                            MinimalAiInputField(
                                value = modelId,
                                onValueChange = onModelIdChange,
                                placeholder = "Model ID (e.g. gpt-4o)",
                                icon = Icons.Outlined.Memory,
                                testTag = "ai_model_id_input"
                            )

                            Spacer(Modifier.height(2.dp))

                            // "Add Provider" Button matching Welcome Screen button style
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
                                        onClearInputs()
                                        onIsTestingCloudChange(true)
                                        prefs.testCloudProvider(newProvider.id)
                                        onIsTestingCloudChange(false)
                                    }
                                },
                                    shape = RoundedCornerShape(32.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF161A20),
                                        contentColor = if (isDark) MaterialTheme.colorScheme.onPrimary else Color.White
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF161A20)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("ai_add_provider_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Add Provider",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                        )
                                    }
                                }
                            }

                        // Configured Providers List
                        if (cloudProviders.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = "Configured Providers",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            Spacer(Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                cloudProviders.forEach { provider ->
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (provider.isActive) {
                                            if (isDark) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            else Color.White.copy(alpha = 0.85f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (provider.isActive) {
                                                if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                else Color(0xFF161A20).copy(alpha = 0.6f)
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                            }
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
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                                        text = provider.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    )
                                                    Text(
                                                        text = "${provider.modelId} • ${provider.baseUrl.removePrefix("https://").removePrefix("http://").take(24)}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 11.5.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    )
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            prefs.testCloudProvider(provider.id)
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Refresh,
                                                        contentDescription = "Test connection",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            prefs.deleteCloudProvider(provider.id)
                                                            onShowSnackbar?.invoke("Provider '${provider.name}' removed")
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Remove provider",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
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
