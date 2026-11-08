package com.amresalehin.emregallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.face.*
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * State holder for the media details drawer (metadata and OCR).
 */
@Stable
class ViewerDetailsState(
    val context: Context,
    val item: MediaItem,
    defaultOcrLanguages: Set<String>,
    initialOcrModel: String = "fast",
    defaultMlKitLanguages: Set<String> = setOf("latin")
) {
    var metadata by mutableStateOf<Map<String, String>>(emptyMap())
    var ocrText by mutableStateOf("")
    var ocrError by mutableStateOf<String?>(null)
    var ocrBusy by mutableStateOf(false)
    var ocrModel by mutableStateOf(initialOcrModel)
    val ocrLanguages = mutableStateListOf<String>().apply {
        val initial = if (defaultOcrLanguages.isNotEmpty()) defaultOcrLanguages else setOf("eng")
        addAll(initial)
    }
    val mlKitLanguages = mutableStateListOf<String>().apply {
        val initial = if (defaultMlKitLanguages.isNotEmpty()) defaultMlKitLanguages else setOf("latin")
        addAll(initial)
    }

    suspend fun loadData() {
        metadata = withContext(Dispatchers.IO) {
            runCatching { readMediaMetadata(context, item) }.getOrDefault(emptyMap())
        }
        ocrText = withContext(Dispatchers.IO) {
            TesseractOcr.readCachedResult(context, Uri.parse(item.uri)).orEmpty()
        }
        ocrError = null
    }


    suspend fun runOcr(engine: String = "tesseract") {
        val isMlKit = engine.equals("google_mlkit", ignoreCase = true) || engine.equals("mlkit", ignoreCase = true)
        if (item.isVideo || ocrBusy || (!isMlKit && ocrLanguages.isEmpty()) || (isMlKit && mlKitLanguages.isEmpty())) return
        ocrBusy = true
        ocrError = null
        ocrText = ""
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val mediaUri = Uri.parse(item.uri)
                val recognized = if (isMlKit) {
                    com.amresalehin.emregallery.ocr.MlKitOcr.recognize(context, mediaUri, mlKitLanguages)
                } else {
                    val model = com.amresalehin.emregallery.ocr.TesseractModel.fromId(ocrModel)
                    TesseractOcr.recognize(context, mediaUri, ocrLanguages.toList(), model)
                }
                runCatching { TesseractOcr.saveCachedResult(context, mediaUri, recognized) }
                recognized
            }
        }
        result.onSuccess { text ->
            ocrText = text.ifBlank { "No text detected in this image." }
        }.onFailure { error ->
            ocrError = error.message ?: "OCR failed. Please try again."
        }
        ocrBusy = false
    }

    fun copyOcrText() {
        if (ocrText.isNotBlank() && ocrText != "No text detected in this image.") {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Recognized text", ocrText))
        }
    }
}

@Composable
fun rememberViewerDetailsState(
    item: MediaItem,
    defaultOcrLanguages: Set<String>,
    defaultOcrModel: String = "fast",
    defaultMlKitLanguages: Set<String> = setOf("latin")
): ViewerDetailsState {
    val context = LocalContext.current
    val state = remember(item.uri, defaultOcrLanguages, defaultOcrModel, defaultMlKitLanguages) {
        ViewerDetailsState(context, item, defaultOcrLanguages, defaultOcrModel, defaultMlKitLanguages)
    }

    LaunchedEffect(item.uri, defaultOcrLanguages, defaultOcrModel, defaultMlKitLanguages) {
        state.loadData()
    }

    return state
}

internal data class FaceStateSnapshot(
    val allFaces: List<DetectedMediaFace>,
    val visibleFaces: List<DetectedMediaFace>,
    val info: FaceClusterStore.MediaIndexedInfo?,
    val groupingRequired: Boolean,
    val groupingError: String?
)



internal fun computeViewerFaceUiState(
    isModelInstalled: Boolean,
    isInstallingModel: Boolean,
    modelInstallProgress: String?,
    isScanning: Boolean,
    scanPhase: String?,
    detectedFaces: List<DetectedMediaFace>,
    mediaIndexInfo: FaceClusterStore.MediaIndexedInfo?,
    lastScanError: String?,
    isLoadingIndexInfo: Boolean,
    groupingRequired: Boolean,
    groupingError: String?,
    allDetectedFaces: List<DetectedMediaFace>,
    engineDisplayName: String,
    selectedDrawerEngineId: String
): ViewerFaceUiState {
        val detectedCount = maxOf(mediaIndexInfo?.faceCount ?: 0, allDetectedFaces.size)
        when {
            isScanning -> ViewerFaceUiState(
                state = ViewerFaceState.IN_PROGRESS,
                progressPhase = scanPhase ?: "Scanning photo...",
                engineName = engineDisplayName,
                engineId = selectedDrawerEngineId
            )
            isInstallingModel -> ViewerFaceUiState(
                state = ViewerFaceState.IN_PROGRESS,
                progressPhase = modelInstallProgress ?: "Installing model...",
                engineName = engineDisplayName,
                engineId = selectedDrawerEngineId
            )
            !isModelInstalled -> ViewerFaceUiState(
                state = ViewerFaceState.MODEL_MISSING,
                engineName = engineDisplayName,
                engineId = selectedDrawerEngineId
            )
            isLoadingIndexInfo -> ViewerFaceUiState(
                state = ViewerFaceState.NOT_INDEXED,
                engineName = engineDisplayName,
                engineId = selectedDrawerEngineId
            )
            lastScanError != null && detectedFaces.isEmpty() && groupingError == null -> ViewerFaceUiState(
                state = ViewerFaceState.FAILED,
                errorMessage = lastScanError,
                engineName = engineDisplayName,
                engineId = selectedDrawerEngineId
            )
            mediaIndexInfo?.status == "failed" -> ViewerFaceUiState(
                state = ViewerFaceState.FAILED,
                errorMessage = mediaIndexInfo?.errorMessage ?: "Face scan failed for this photo",
                engineName = engineDisplayName,
                engineId = selectedDrawerEngineId
            )
            groupingError != null -> ViewerFaceUiState(
                state = ViewerFaceState.FAILED,
                errorMessage = "Grouping failed: $groupingError",
                engineName = engineDisplayName,
                engineId = selectedDrawerEngineId,
                groupingFailure = true
            )
            detectedFaces.isNotEmpty() -> ViewerFaceUiState(
                state = ViewerFaceState.COMPLETED_FACES_FOUND,
                faces = detectedFaces,
                progressPhase = if (groupingRequired) "$detectedCount faces found · waiting to be grouped" else null,
                engineName = engineDisplayName,
                engineId = selectedDrawerEngineId
            )
            groupingRequired && detectedCount > 0 -> ViewerFaceUiState(
                state = ViewerFaceState.WAITING_FOR_GROUPING,
                progressPhase = "$detectedCount faces found · waiting to be grouped",
                engineName = engineDisplayName,
                engineId = selectedDrawerEngineId
            )
            detectedCount > 0 -> ViewerFaceUiState(
                state = ViewerFaceState.GROUPING_INCOMPLETE,
                progressPhase = "$detectedCount faces found · waiting for a person group",
                engineName = engineDisplayName,
                engineId = selectedDrawerEngineId
            )
            mediaIndexInfo?.status == "indexed" -> ViewerFaceUiState(
                state = ViewerFaceState.COMPLETED_NO_FACES,
                engineName = engineDisplayName,
                engineId = selectedDrawerEngineId
            )
            else -> ViewerFaceUiState(
                state = ViewerFaceState.NOT_INDEXED,
                engineName = engineDisplayName,
                engineId = selectedDrawerEngineId
            )
        }
}
