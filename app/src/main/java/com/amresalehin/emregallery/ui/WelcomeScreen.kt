package com.amresalehin.emregallery.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amresalehin.emregallery.R
import com.amresalehin.emregallery.data.PreferencesRepository
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.EnteFaceConstants
import com.amresalehin.emregallery.face.FaceRecognitionSettings
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    initialStep: Int = 0,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val preferencesRepository = remember { PreferencesRepository(context.applicationContext) }

    var currentStep by remember { mutableIntStateOf(initialStep) }
    var mediaGranted by remember { mutableStateOf(hasMediaPermission(context)) }
    var notificationGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var allFilesGranted by remember { mutableStateOf(hasAllFilesAccess()) }

    BackHandler(enabled = currentStep > 0) {
        currentStep--
    }

    // Re-check permissions when coming back to the app (e.g. from system settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mediaGranted = hasMediaPermission(context)
                notificationGranted = hasNotificationPermission(context)
                allFilesGranted = hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mediaGranted = hasMediaPermission(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationGranted = isGranted
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        allFilesGranted = hasAllFilesAccess()
    }

    fun requestMedia() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            Build.VERSION.SDK_INT >= 29 -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        mediaPermissionLauncher.launch(permissions)
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationGranted = true
        }
    }

    fun requestAllFiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = try {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            } catch (_: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
            allFilesLauncher.launch(intent)
        } else {
            allFilesGranted = true
        }
    }

    // Atmospheric mist/fog gradient matching Emre style (#F3F5F7 to #161A20)
    val mistGradient = Brush.verticalGradient(
        0.00f to Color(0xFFF3F5F7),
        0.28f to Color(0xFFE2E7EC),
        0.58f to Color(0xFF8F9BA8),
        0.82f to Color(0xFF38414D),
        1.00f to Color(0xFF161A20)
    )

    val grantedCount = (if (mediaGranted) 1 else 0) +
        (if (notificationGranted) 1 else 0) +
        (if (allFilesGranted) 1 else 0)
    val allPermissionsGranted = grantedCount == 3

    val buttonText = when {
        allPermissionsGranted -> "Enter Emre"
        grantedCount in 1..2 -> "Growing Wings"
        else -> "Liberate Emre"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(mistGradient)
            .testTag("welcome_screen")
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState > initialState
                if (forward) {
                    (fadeIn(animationSpec = tween(280)) + slideInHorizontally(animationSpec = tween(280)) { it / 3 })
                        .togetherWith(fadeOut(animationSpec = tween(220)) + slideOutHorizontally(animationSpec = tween(220)) { -it / 3 })
                } else {
                    (fadeIn(animationSpec = tween(280)) + slideInHorizontally(animationSpec = tween(280)) { -it / 3 })
                        .togetherWith(fadeOut(animationSpec = tween(220)) + slideOutHorizontally(animationSpec = tween(220)) { it / 3 })
                }
            },
            label = "welcome_flow"
        ) { step ->
            when (step) {
                0 -> {
                    // Screen 1: Minimalist Emre Splash for First Setup Run
                    // Centered large logo, no corner arrow, and subtle "Tap to continue" at the bottom
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { currentStep = 1 }
                            .testTag("welcome_intro_next_button")
                    ) {
                        // Centered large logo
                        Icon(
                            painter = painterResource(R.drawable.ic_app_logo),
                            contentDescription = "Emre Gallery",
                            modifier = Modifier
                                .size(168.dp)
                                .align(Alignment.Center)
                                .testTag("welcome_intro_logo"),
                            tint = Color(0xFF1D2228)
                        )

                        // Bottom "Tap to continue" in small font size
                        Text(
                            text = stringResource(R.string.welcome_tap_to_continue),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.3.sp,
                                color = Color.White.copy(alpha = 0.82f)
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 42.dp)
                                .testTag("welcome_intro_tap_to_continue")
                        )
                    }
                }
                1 -> {
                    // Screen 2: "Let's Personalize" Welcome Customization Screen
                    PersonalizeStep(
                        onSkip = {
                            currentStep = 2
                        },
                        onSave = { ocrEngine, mlKitLangs, ocrLangs, faceEngineOption ->
                            coroutineScope.launch {
                                // Save OCR configuration
                                if (ocrEngine == "mlkit") {
                                    preferencesRepository.setOcrEngine("mlkit")
                                    preferencesRepository.setMlKitLanguages(mlKitLangs)
                                } else {
                                    preferencesRepository.setOcrEngine("tesseract")
                                    preferencesRepository.setOcrLanguages(ocrLangs)
                                }

                                // Save Face Recognition configuration
                                when (faceEngineOption) {
                                    "disable" -> {
                                        FaceRecognitionSettings.preferences(context).edit()
                                            .putBoolean("face_recognition_enabled", false)
                                            .putString(FaceRecognitionSettings.KEY_ACTIVE_ENGINE, "disabled")
                                            .apply()
                                    }
                                    "yolo" -> {
                                        FaceRecognitionSettings.preferences(context).edit()
                                            .putBoolean("face_recognition_enabled", true)
                                            .apply()
                                        FaceRecognitionSettings.setActiveEngine(context, EnteFaceConstants.ENGINE_ID)
                                    }
                                    "buffalo" -> {
                                        FaceRecognitionSettings.preferences(context).edit()
                                            .putBoolean("face_recognition_enabled", true)
                                            .apply()
                                        FaceRecognitionSettings.setActiveEngine(context, BuffaloLFaceEngine.ENGINE_ID)
                                    }
                                }
                            }
                            currentStep = 2
                        }
                    )
                }
                2 -> {
                    // Screen 3: "Let's set things up" Permissions screen
                    WelcomePermissionsStepContent(
                        mediaGranted = mediaGranted,
                        notificationGranted = notificationGranted,
                        allFilesGranted = allFilesGranted,
                        allPermissionsGranted = allPermissionsGranted,
                        buttonText = buttonText,
                        onRequestMedia = { requestMedia() },
                        onRequestNotifications = { requestNotifications() },
                        onRequestAllFiles = { requestAllFiles() },
                        onComplete = onComplete
                    )
                }
            }
        }
    }
}
