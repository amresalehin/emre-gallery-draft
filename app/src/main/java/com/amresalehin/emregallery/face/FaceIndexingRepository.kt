package com.amresalehin.emregallery.face

import android.content.Context
import android.net.Uri
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FaceIndexingProgress(
    val processed: Int,
    val total: Int,
    val facesFound: Int,
    val failed: Int
)

/**
 * Incremental indexer: completed media are skipped on later runs, so adding new photos does not
 * require recomputing embeddings for the entire library.
 */
class FaceIndexingRepository(private val context: Context) {

    companion object {
        @Volatile
        var activeEngineDescription: String = "Detecting..."
            private set

        @Volatile
        var lastPrimaryFailure: String? = null

        fun queryEngineInfo(context: Context): Pair<String, String?> {
            val installed = BuffaloLFaceEngine.isModelInstalled(context)
            return if (!installed) {
                "Android Native FaceDetector (128-d fallback)" to "Buffalo_L model pack not downloaded (using 128-d native fallback)"
            } else if (lastPrimaryFailure != null) {
                "Android Native FaceDetector (128-d fallback)" to lastPrimaryFailure
            } else {
                "Buffalo_L (InsightFace ONNX 512-d)" to null
            }
        }
    }

    suspend fun indexNewMedia(
        media: List<MediaItem>,
        store: FaceClusterStore,
        onProgress: suspend (FaceIndexingProgress) -> Unit = {},
        awaitIfResumed: suspend () -> Unit = {},
        allowNativeFallback: Boolean = false,
        detectionThreshold: Float = FaceRecognitionSettings.DEFAULT_DETECTION_CONFIDENCE,
        recognitionDistance: Float = FaceRecognitionSettings.DEFAULT_RECOGNITION_DISTANCE
    ): FaceIndexingProgress = withContext(Dispatchers.IO) {
        val photos = media.filterNot { it.isVideo }
        val pending = photos.filterNot { store.isMediaIndexed(it.uri) }
        var processed = 0
        var facesFound = 0
        var failed = 0
        var engine: FaceEngine? = null
        try {
            if (pending.isNotEmpty()) {
                engine = try {
                    if (BuffaloLFaceEngine.isModelInstalled(context)) {
                        val bEngine = BuffaloLFaceEngine.open(context, detectionThreshold)
                        activeEngineDescription = "Buffalo_L (InsightFace ONNX 512-d)"
                        lastPrimaryFailure = null
                        bEngine
                    } else {
                        if (!allowNativeFallback) error("Buffalo_L model is not installed; choose native fallback explicitly or download the model.")
                        activeEngineDescription = "Android Native FaceDetector (128-d fallback)"
                        lastPrimaryFailure = "Buffalo_L model pack not downloaded"
                        AndroidNativeFaceEngine(context)
                    }
                } catch (e: Exception) {
                    if (!allowNativeFallback) throw e
                    activeEngineDescription = "Android Native FaceDetector (128-d fallback)"
                    lastPrimaryFailure = "Buffalo_L initialization failed: " + (e.localizedMessage ?: e.message ?: "Unknown error")
                    AndroidNativeFaceEngine(context)
                }
            }
            for (item in pending) {
                // Pause and cancellation take effect between images; an in-flight native
                // inference is allowed to complete before the next checkpoint.
                awaitIfResumed()
                currentCoroutineContext().ensureActive()
                try {
                    val activeEngine = engine ?: AndroidNativeFaceEngine(context)
                    val detections = activeEngine.embed(Uri.parse(item.uri))
                    detections.forEach { face ->
                        store.addFace(item.uri, face.faceIndex, face.embedding, recognitionDistance)
                    }
                    store.markMediaIndexed(item.uri, detections.size)
                    facesFound += detections.size
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    store.markMediaFailed(item.uri)
                    failed++
                } finally {
                    processed++
                    onProgress(FaceIndexingProgress(processed, pending.size, facesFound, failed))
                }
            }
        } finally {
            engine?.close()
        }
        FaceIndexingProgress(processed, pending.size, facesFound, failed)
    }

    suspend fun downloadModel(
        force: Boolean = false,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        onStage: (String) -> Unit = {},
        awaitIfResumed: suspend () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        BuffaloLFaceEngine.downloadModel(context, force, onProgress, onStage, awaitIfResumed)
        lastPrimaryFailure = null
        activeEngineDescription = "Buffalo_L (InsightFace ONNX 512-d)"
    }
}
