package com.amresalehin.emregallery.ui
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun LockedFolderAuthView(
    hasPinSet: Boolean,
    onBack: () -> Unit,
    onSetPin: (String) -> Unit,
    onVerifyPin: suspend (String) -> Boolean,
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var enteredPin by remember { mutableStateOf("") }
    var isConfirmingSetup by remember { mutableStateOf(false) }
    var firstPinEntry by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var hasAutoPromptedBiometric by remember { mutableStateOf(false) }

    val biometricAvailable = remember(context) { BiometricAuthHelper.isBiometricAvailable(context) }

    // Trigger biometric authentication safely
    val launchBiometricAuth = {
        val activity = context as? FragmentActivity
        if (activity != null) {
            BiometricAuthHelper.authenticate(
                activity = activity,
                title = "Unlock Locked Folder",
                subtitle = "Authenticate to access private media",
                onSuccess = { onUnlockSuccess() },
                onError = { err -> errorMessage = err }
            )
        }
    }

    // Auto-launch biometric prompt ONLY ONCE when initially opening the Locked Folder with an existing PIN
    LaunchedEffect(Unit) {
        if (hasPinSet && biometricAvailable && !hasAutoPromptedBiometric) {
            hasAutoPromptedBiometric = true
            delay(300)
            launchBiometricAuth()
        }
    }

    val onDigitClick: (String) -> Unit = { digit ->
        if (enteredPin.length < 6 && !isVerifying) {
            val newPin = enteredPin + digit
            enteredPin = newPin

            if (hasPinSet && newPin.length == 6) {
                // PIN verification mode (normal unlock)
                isVerifying = true
                coroutineScope.launch {
                    val verified = onVerifyPin(newPin)
                    isVerifying = false
                    if (verified) {
                        onUnlockSuccess()
                    } else {
                        errorMessage = "Incorrect PIN. Try again."
                        enteredPin = ""
                    }
                }
            } else if (!hasPinSet && isConfirmingSetup) {
                // Real-time mismatch check on every typed digit
                if (!firstPinEntry.startsWith(newPin)) {
                    errorMessage = "Digits do not match"
                } else {
                    errorMessage = null
                }
            } else {
                errorMessage = null
            }
        }
    }

    val onBackspaceClick: () -> Unit = {
        if (enteredPin.isNotEmpty() && !isVerifying) {
            val newPin = enteredPin.dropLast(1)
            enteredPin = newPin
            if (!hasPinSet && isConfirmingSetup) {
                if (!firstPinEntry.startsWith(newPin)) {
                    errorMessage = "Digits do not match"
                } else {
                    errorMessage = null
                }
            } else {
                errorMessage = null
            }
        }
    }

    // Determine special action in the in-built keyboard undo button's slot
    val specialAction: PinSpecialAction? = when {
        !hasPinSet && !isConfirmingSetup && enteredPin.length == 6 -> PinSpecialAction.REENTER
        !hasPinSet && isConfirmingSetup && enteredPin.length == 6 && enteredPin == firstPinEntry -> PinSpecialAction.CONFIRM
        else -> null
    }

    val onSpecialActionClick: () -> Unit = {
        when (specialAction) {
            PinSpecialAction.REENTER -> {
                firstPinEntry = enteredPin
                enteredPin = ""
                isConfirmingSetup = true
                errorMessage = null
            }
            PinSpecialAction.CONFIRM -> {
                onSetPin(enteredPin)
                onUnlockSuccess()
            }
            null -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Locked folder",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.weight(0.5f))

        // Shield / Lock Icon
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(76.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = when {
                !hasPinSet && !isConfirmingSetup -> "Set up Locked Folder PIN"
                !hasPinSet && isConfirmingSetup -> "Confirm your 6-digit PIN"
                else -> "Enter PIN"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = when {
                !hasPinSet && !isConfirmingSetup -> "Choose a 6-digit PIN to secure your private items"
                !hasPinSet && isConfirmingSetup -> "Re-enter the same 6-digit PIN to confirm"
                else -> "Enter your 6-digit PIN to access locked photos"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(24.dp))

        // 6 PIN Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 6) {
                val filled = i < enteredPin.length
                val isMismatch = !hasPinSet && isConfirmingSetup && errorMessage != null && i == enteredPin.length - 1
                val dotColor = when {
                    isMismatch -> MaterialTheme.colorScheme.error
                    filled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                }
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }

        if (!hasPinSet && isConfirmingSetup) {
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = {
                    isConfirmingSetup = false
                    enteredPin = ""
                    firstPinEntry = ""
                    errorMessage = null
                }
            ) {
                Text("Start over with a different PIN", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Error message text if any
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        // Number Keypad with Re-enter / Confirm appearing in place of built-in undo button
        PinKeypad(
            onDigitClick = onDigitClick,
            onBackspaceClick = onBackspaceClick,
            showBiometric = hasPinSet && biometricAvailable,
            onBiometricClick = { launchBiometricAuth() },
            specialAction = specialAction,
            onSpecialActionClick = onSpecialActionClick,
            enteredPinNotEmpty = enteredPin.isNotEmpty()
        )

        Spacer(Modifier.height(24.dp))
    }
}

