package com.amresalehin.emregallery.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object FlorenceModelManager {

    internal const val MODEL_SUBDIR = "models/florence2"
    internal const val MANIFEST_FILE = "florence_manifest.json"
    const val MODEL_NAME = "Florence-2"
    const val MODEL_VERSION = "Florence-2-base"

    internal val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    internal val activeDownloadJobs = ConcurrentHashMap<String, Job>()

    internal val _modelState = MutableStateFlow(FlorenceModelState())
    val modelState: StateFlow<FlorenceModelState> = _modelState.asStateFlow()

    internal val _allModelStates = MutableStateFlow<Map<String, FlorenceModelState>>(emptyMap())
    val allModelStates: StateFlow<Map<String, FlorenceModelState>> = _allModelStates.asStateFlow()

    data class ModelArtifact(
        val fileName: String,
        val url: String,
        val estimatedSize: Long
    )

    data class LocalModelSpec(
        val id: String,
        val displayName: String,
        val description: String,
        val subdir: String,
        val manifestFileName: String,
        val testTagPrefix: String,
        val artifacts: List<ModelArtifact>
    )

    internal val FLORENCE_ARTIFACTS = listOf(
        ModelArtifact(
            fileName = "config.json",
            url = "https://huggingface.co/onnx-community/Florence-2-base/resolve/main/config.json",
            estimatedSize = 5_447L
        ),
        ModelArtifact(
            fileName = "tokenizer_config.json",
            url = "https://huggingface.co/onnx-community/Florence-2-base/resolve/main/tokenizer_config.json",
            estimatedSize = 197_658L
        ),
        ModelArtifact(
            fileName = "tokenizer.json",
            url = "https://huggingface.co/onnx-community/Florence-2-base/resolve/main/tokenizer.json",
            estimatedSize = 2_297_961L
        ),
        ModelArtifact(
            fileName = "florence2_encoder.onnx",
            url = "https://huggingface.co/onnx-community/Florence-2-base/resolve/main/onnx/encoder_model_quantized.onnx",
            estimatedSize = 43_651_493L
        )
    )

    val SUPPORTED_MODELS: List<LocalModelSpec> = listOf(
        LocalModelSpec(
            id = "gemma_4_e2b",
            displayName = "Gemma 4 E2B",
            description = "Google Gemma 4 E2B Multimodal Edge Model",
            subdir = "models/gemma_4_e2b",
            manifestFileName = "gemma_manifest.json",
            testTagPrefix = "gemma",
            artifacts = listOf(
                ModelArtifact("config.json", "https://huggingface.co/onnx-community/gemma-3-1b-it-ONNX/resolve/main/config.json", 1_975L),
                ModelArtifact("tokenizer_config.json", "https://huggingface.co/onnx-community/gemma-3-1b-it-ONNX/resolve/main/tokenizer_config.json", 2_327L),
                ModelArtifact("tokenizer.json", "https://huggingface.co/onnx-community/gemma-3-1b-it-ONNX/resolve/main/tokenizer.json", 20_323_013L),
                ModelArtifact("gemma_weights.onnx", "https://huggingface.co/onnx-community/gemma-3-1b-it-ONNX/resolve/main/onnx/model_q4.onnx", 347_363L)
            )
        ),
        LocalModelSpec(
            id = "lfm2_5_vl_450m_extract",
            displayName = "LFM2.5-VL-450M-Extract",
            description = "Liquid Foundation Model 2.5 Vision-Language 450M Extract Model",
            subdir = "models/lfm2_5_vl_450m",
            manifestFileName = "lfm_manifest.json",
            testTagPrefix = "lfm",
            artifacts = listOf(
                ModelArtifact("config.json", "https://huggingface.co/onnx-community/LFM2-350M-ONNX/resolve/main/config.json", 1_384L),
                ModelArtifact("tokenizer_config.json", "https://huggingface.co/onnx-community/LFM2-350M-ONNX/resolve/main/tokenizer_config.json", 93_557L),
                ModelArtifact("tokenizer.json", "https://huggingface.co/onnx-community/LFM2-350M-ONNX/resolve/main/tokenizer.json", 3_296_940L),
                ModelArtifact("lfm_encoder.onnx", "https://huggingface.co/onnx-community/LFM2-350M-ONNX/resolve/main/onnx/model_quantized.onnx", 185_941L)
            )
        ),
        LocalModelSpec(
            id = "qwen3_5_0_8b",
            displayName = "Qwen3.5-0.8B",
            description = "Alibaba Qwen 3.5 0.8B Ultra-Compact Edge Model",
            subdir = "models/qwen3_5_0_8b",
            manifestFileName = "qwen_manifest.json",
            testTagPrefix = "qwen",
            artifacts = listOf(
                ModelArtifact("config.json", "https://huggingface.co/onnx-community/Qwen2.5-0.5B/resolve/main/config.json", 691L),
                ModelArtifact("tokenizer_config.json", "https://huggingface.co/onnx-community/Qwen2.5-0.5B/resolve/main/tokenizer_config.json", 7_229L),
                ModelArtifact("tokenizer.json", "https://huggingface.co/onnx-community/Qwen2.5-0.5B/resolve/main/tokenizer.json", 7_031_673L),
                ModelArtifact("qwen_model.onnx", "https://huggingface.co/onnx-community/Qwen2.5-0.5B/resolve/main/onnx/model_quantized.onnx", 512_096_557L)
            )
        ),
        LocalModelSpec(
            id = "florence2",
            displayName = "Florence-2 Base",
            description = "Microsoft Florence-2 Base ONNX Vision-Language Model",
            subdir = "models/florence2",
            manifestFileName = "florence_manifest.json",
            testTagPrefix = "florence",
            artifacts = FLORENCE_ARTIFACTS
        )
    )

    fun getSpec(modelId: String): LocalModelSpec {
        return SUPPORTED_MODELS.firstOrNull { it.id == modelId } ?: SUPPORTED_MODELS.first { it.id == "florence2" }
    }

    fun getModelDir(context: Context, modelId: String = "florence2"): File {
        val spec = getSpec(modelId)
        return File(context.filesDir, spec.subdir)
    }

    fun isInstalled(context: Context, modelId: String = "florence2"): Boolean {
        val spec = getSpec(modelId)
        val dir = getModelDir(context, modelId)
        if (!dir.exists() || !dir.isDirectory) return false
        val manifest = File(dir, spec.manifestFileName)
        if (!manifest.exists() || manifest.length() == 0L) return false

        // Check that all required artifacts exist and have non-zero size
        return spec.artifacts.all { artifact ->
            val file = File(dir, artifact.fileName)
            file.isFile && file.length() > 0L
        }
    }

    fun getInstalledSizeBytes(context: Context, modelId: String = "florence2"): Long {
        val dir = getModelDir(context, modelId)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    fun refreshAllStates(context: Context): Map<String, FlorenceModelState> {
        val statesMap = mutableMapOf<String, FlorenceModelState>()
        for (spec in SUPPORTED_MODELS) {
            val installed = isInstalled(context, spec.id)
            val size = if (installed) getInstalledSizeBytes(context, spec.id) else 0L
            val dir = getModelDir(context, spec.id)
            val state = if (installed) {
                FlorenceModelState(
                    status = FlorenceStatus.INSTALLED,
                    progress = 1.0f,
                    bytesDownloaded = size,
                    totalBytes = size,
                    installedSizeBytes = size,
                    installedPath = dir.absolutePath,
                    lastUpdated = System.currentTimeMillis()
                )
            } else {
                FlorenceModelState(
                    status = FlorenceStatus.NOT_INSTALLED,
                    progress = 0f,
                    bytesDownloaded = 0L,
                    totalBytes = spec.artifacts.sumOf { it.estimatedSize },
                    installedSizeBytes = 0L,
                    installedPath = null,
                    lastUpdated = System.currentTimeMillis()
                )
            }
            statesMap[spec.id] = state
        }
        _allModelStates.value = statesMap
        statesMap["florence2"]?.let { _modelState.value = it }
        return statesMap
    }

    fun refreshState(context: Context): FlorenceModelState {
        val all = refreshAllStates(context)
        return all["florence2"] ?: FlorenceModelState()
    }

    fun getModelState(modelId: String): FlorenceModelState {
        return _allModelStates.value[modelId] ?: FlorenceModelState()
    }


    suspend fun downloadAndInstallModel(
        context: Context,
        modelId: String,
        scope: CoroutineScope
    ): Boolean = downloadAndInstallModelImpl(context, modelId, scope)

    suspend fun downloadAndInstall(
        context: Context,
        scope: CoroutineScope
    ): Boolean = downloadAndInstallImpl(context, scope)

    suspend fun uninstallModel(context: Context, modelId: String): Boolean =
        uninstallModelImpl(context, modelId)

    suspend fun uninstall(context: Context): Boolean = uninstallImpl(context)

    fun cancelDownload(context: Context, modelId: String = "florence2") =
        cancelDownloadImpl(context, modelId)
}
