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


internal suspend fun FlorenceModelManager.downloadAndInstallModelImpl(
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

internal suspend fun FlorenceModelManager.downloadAndInstallImpl(
    context: Context,
    onProgress: ((progress: Float, downloaded: Long, total: Long) -> Unit)? = null
): Boolean = downloadAndInstallModel(context, "florence2", onProgress)

internal fun FlorenceModelManager.updateModelState(modelId: String, state: FlorenceModelState) {
    val current = _allModelStates.value.toMutableMap()
    current[modelId] = state
    _allModelStates.value = current
    if (modelId == "florence2") {
        _modelState.value = state
    }
}

internal fun FlorenceModelManager.cleanUpTempFiles(dir: File) {
    if (!dir.exists()) return
    dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
}

internal suspend fun FlorenceModelManager.uninstallModelImpl(context: Context, modelId: String): Boolean = withContext(Dispatchers.IO) {
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

internal suspend fun FlorenceModelManager.uninstallImpl(context: Context): Boolean = uninstallModel(context, "florence2")

internal fun FlorenceModelManager.cancelDownloadImpl(context: Context, modelId: String = "florence2") {
    activeDownloadJobs[modelId]?.cancel()
    activeDownloadJobs.remove(modelId)
    cleanUpTempFiles(getModelDir(context, modelId))
    refreshAllStates(context)
}
