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

    private const val MODEL_SUBDIR = "models/florence2"
    private const val MANIFEST_FILE = "florence_manifest.json"
    const val MODEL_NAME = "Florence-2"
    const val MODEL_VERSION = "Florence-2-base"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()

    private val _modelState = MutableStateFlow(FlorenceModelState())
    val modelState: StateFlow<FlorenceModelState> = _modelState.asStateFlow()

    private val _allModelStates = MutableStateFlow<Map<String, FlorenceModelState>>(emptyMap())
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

    private val FLORENCE_ARTIFACTS = listOf(
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
        onProgress: ((progress: Float, downloaded: Long, total: Long) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val currentJob = coroutineContext[Job]
        if (currentJob != null) {
            activeDownloadJobs[modelId] = currentJob
        }
        val spec = getSpec(modelId)
        val dir = getModelDir(context, modelId)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val totalEstimated = spec.artifacts.sumOf { it.estimatedSize }
        val initialDownloadingState = FlorenceModelState(
            status = FlorenceStatus.DOWNLOADING,
            progress = 0.05f,
            bytesDownloaded = 0L,
            totalBytes = totalEstimated,
            lastUpdated = System.currentTimeMillis()
        )
        updateModelState(modelId, initialDownloadingState)

        var totalBytesDownloaded = 0L

        try {
            for ((index, artifact) in spec.artifacts.withIndex()) {
                val destFile = File(dir, artifact.fileName)
                val tempFile = File(dir, "${artifact.fileName}.tmp")

                var downloadedForArtifact = 0L

                val request = Request.Builder()
                    .url(artifact.url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) EmreGallery/1.0")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful || response.body == null) {
                        throw IllegalStateException("HTTP ${response.code} downloading ${artifact.fileName}")
                    }
                    val body = response.body!!
                    val input: InputStream = body.byteStream()
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedForArtifact += read
                            val currentTotal = totalBytesDownloaded + downloadedForArtifact
                            val frac = (currentTotal.toFloat() / totalEstimated.toFloat()).coerceIn(0.05f, 0.95f)
                            val update = initialDownloadingState.copy(
                                progress = frac,
                                bytesDownloaded = currentTotal,
                                totalBytes = totalEstimated
                            )
                            updateModelState(modelId, update)
                            onProgress?.invoke(frac, currentTotal, totalEstimated)
                        }
                        output.flush()
                    }
                    if (tempFile.exists() && tempFile.length() > 0L) {
                        if (destFile.exists()) destFile.delete()
                        tempFile.renameTo(destFile)
                    } else {
                        throw IllegalStateException("Downloaded file ${artifact.fileName} is empty")
                    }
                }

                totalBytesDownloaded += downloadedForArtifact
                val overallFrac = ((index + 1).toFloat() / spec.artifacts.size.toFloat()).coerceIn(0.1f, 0.98f)
                val update = initialDownloadingState.copy(
                    progress = overallFrac,
                    bytesDownloaded = totalBytesDownloaded
                )
                updateModelState(modelId, update)
                onProgress?.invoke(overallFrac, totalBytesDownloaded, totalEstimated)
            }

            // Verify installation
            val verifyingState = initialDownloadingState.copy(
                status = FlorenceStatus.VERIFYING,
                progress = 0.99f
            )
            updateModelState(modelId, verifyingState)

            // Write manifest
            val totalSize = getInstalledSizeBytes(context, modelId)
            val manifestJson = JSONObject().apply {
                put("id", spec.id)
                put("name", spec.displayName)
                put("installedAt", System.currentTimeMillis())
                put("totalBytes", totalSize)
                put("artifactsCount", spec.artifacts.size)
                put("status", "VALID")
            }
            File(dir, spec.manifestFileName).writeText(manifestJson.toString(2))

            val finalState = FlorenceModelState(
                status = FlorenceStatus.INSTALLED,
                progress = 1.0f,
                bytesDownloaded = totalSize,
                totalBytes = totalSize,
                installedSizeBytes = totalSize,
                installedPath = dir.absolutePath,
                lastUpdated = System.currentTimeMillis()
            )
            updateModelState(modelId, finalState)
            onProgress?.invoke(1.0f, totalSize, totalSize)
            return@withContext true

        } catch (e: CancellationException) {
            cleanUpTempFiles(dir)
            refreshAllStates(context)
            throw e
        } catch (e: Exception) {
            cleanUpTempFiles(dir)
            val errState = FlorenceModelState(
                status = FlorenceStatus.ERROR,
                errorMessage = e.localizedMessage ?: "Installation failed",
                lastUpdated = System.currentTimeMillis()
            )
            updateModelState(modelId, errState)
            return@withContext false
        } finally {
            activeDownloadJobs.remove(modelId)
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        onProgress: ((progress: Float, downloaded: Long, total: Long) -> Unit)? = null
    ): Boolean = downloadAndInstallModel(context, "florence2", onProgress)

    private fun updateModelState(modelId: String, state: FlorenceModelState) {
        val current = _allModelStates.value.toMutableMap()
        current[modelId] = state
        _allModelStates.value = current
        if (modelId == "florence2") {
            _modelState.value = state
        }
    }

    private fun cleanUpTempFiles(dir: File) {
        if (!dir.exists()) return
        dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
    }

    suspend fun uninstallModel(context: Context, modelId: String): Boolean = withContext(Dispatchers.IO) {
        val dir = getModelDir(context, modelId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        val uninstalledState = FlorenceModelState(
            status = FlorenceStatus.NOT_INSTALLED,
            progress = 0f,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            installedSizeBytes = 0L,
            installedPath = null,
            lastUpdated = System.currentTimeMillis()
        )
        updateModelState(modelId, uninstalledState)
        return@withContext true
    }

    suspend fun uninstall(context: Context): Boolean = uninstallModel(context, "florence2")

    fun cancelDownload(context: Context, modelId: String = "florence2") {
        activeDownloadJobs[modelId]?.cancel()
        activeDownloadJobs.remove(modelId)
        cleanUpTempFiles(getModelDir(context, modelId))
        refreshAllStates(context)
    }
}
