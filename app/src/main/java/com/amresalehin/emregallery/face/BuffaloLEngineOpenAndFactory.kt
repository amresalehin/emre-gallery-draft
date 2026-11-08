package com.amresalehin.emregallery.face

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File

internal fun BuffaloLModelInstaller.open(
    context: Context,
    detectionThreshold: Float = FaceRecognitionSettings.DEFAULT_DETECTION_CONFIDENCE
): BuffaloLFaceEngine {
    if (!isModelInstalled(context)) {
        runCatching { installBundledModel(context) }
    }
    check(isModelInstalled(context)) {
        "Buffalo_L model is not installed. Please download it first."
    }
    val modelDir = File(context.filesDir, "face-models/buffalo_l")
    val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(2)
        setInterOpNumThreads(1)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
    }
    val env = OrtEnvironment.getEnvironment()
    val detector = env.createSession(modelDir.resolve("det_10g.onnx").absolutePath, options)
    try {
        val recognizer = env.createSession(modelDir.resolve("w600k_r50.onnx").absolutePath, options)
        return BuffaloLFaceEngine(
            context.applicationContext,
            detector,
            recognizer,
            detectionThreshold.coerceIn(0.30f, 0.90f)
        )
    } catch (error: Throwable) {
        detector.close()
        throw error
    }
}

/**
 * Factory for Buffalo_L engine registered with FaceEngineRegistry.
 */
object BuffaloLFaceEngineFactory : FaceEngineFactory {
    override val engineId: String = BuffaloLFaceEngine.ENGINE_ID
    override val displayName: String = "Buffalo_L (InsightFace ONNX 512-d)"
    override val modelVersion: String = BuffaloLFaceEngine.MODEL_VERSION
    override val embeddingDimension: Int = BuffaloLFaceEngine.EMBEDDING_DIMENSION
    override val isExperimental: Boolean = false

    override fun isInstalled(context: Context): Boolean = BuffaloLFaceEngine.isModelInstalled(context)

    override fun create(context: Context, detectionThreshold: Float): FaceEngine =
        BuffaloLFaceEngine.open(context, detectionThreshold)

    override suspend fun downloadModel(
        context: Context,
        force: Boolean,
        onProgress: (Long, Long) -> Unit,
        onStage: (String) -> Unit,
        awaitIfResumed: suspend () -> Unit,
        onDetailedProgress: (DetailedDownloadProgress) -> Unit
    ) = BuffaloLFaceEngine.downloadModel(context, force, onProgress, onStage, awaitIfResumed, onDetailedProgress)

    init {
        FaceEngineRegistry.register(this)
    }
}
