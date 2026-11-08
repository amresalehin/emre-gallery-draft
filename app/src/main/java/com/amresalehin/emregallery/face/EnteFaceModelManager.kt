package com.amresalehin.emregallery.face

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.CancellationException
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

enum class EnteModelState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    INSTALLED_AND_VALID,
    CORRUPT_OR_INCOMPLETE,
    UNSUPPORTED_OR_RUNTIME_FAILURE,
    LICENSE_OR_SOURCE_BLOCKED
}

data class EnteModelArtifact(
    val filename: String,
    val expectedMinBytes: Long,
    val sha256Hex: String? = null,
    val downloadUrl: String? = null
)

/**
 * Isolated model manager for the Ente-style YOLO5Face + MobileFaceNet pipeline.
 * All files are managed in a private directory separate from Buffalo_L.
 */
object EnteFaceModelManager {

    val REQUIRED_ARTIFACTS = listOf(
        EnteModelArtifact(
            filename = EnteFaceConstants.DETECTOR_FILENAME,
            expectedMinBytes = 10_000_000L, // ~11.0MB for yolov5n_face
            sha256Hex = "eb244a06e36999db732b317c2b30fa113cd6cfc1a397eaf738f2d6f33c01f640",
            downloadUrl = "https://github.com/yakhyo/yolov5-face-onnx-inference/releases/download/weights/yolov5n_face.onnx"
        ),
        EnteModelArtifact(
            filename = EnteFaceConstants.EMBEDDER_FILENAME,
            expectedMinBytes = 10_000_000L, // ~13.6MB for MobileFaceNet ONNX
            sha256Hex = "9cc6e4a75f0e2bf0b1aed94578f144d15175f357bdc05e815e5c4a02b319eb4f",
            downloadUrl = "https://huggingface.co/deepghs/insightface/resolve/main/buffalo_s/w600k_mbf.onnx"
        )
    )

    fun getModelDir(context: Context): File {
        return File(context.filesDir, EnteFaceConstants.MODEL_SUBDIR)
    }

    fun getModelState(context: Context): EnteModelState {
        val dir = getModelDir(context)
        if (!dir.exists()) return EnteModelState.NOT_DOWNLOADED

        var hasAllFiles = true
        var isCorrupted = false

        for (artifact in REQUIRED_ARTIFACTS) {
            val file = File(dir, artifact.filename)
            if (!file.exists()) {
                hasAllFiles = false
            } else if (file.length() < artifact.expectedMinBytes) {
                isCorrupted = true
            } else if (artifact.sha256Hex != null) {
                val fileHash = computeSha256(file)
                if (!fileHash.equals(artifact.sha256Hex, ignoreCase = true)) {
                    isCorrupted = true
                }
            }
        }

        return when {
            isCorrupted -> EnteModelState.CORRUPT_OR_INCOMPLETE
            hasAllFiles -> EnteModelState.INSTALLED_AND_VALID
            else -> EnteModelState.NOT_DOWNLOADED
        }
    }

    fun isInstalled(context: Context): Boolean {
        return getModelState(context) == EnteModelState.INSTALLED_AND_VALID
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    fun installFromAssetsIfPresent(context: Context): Boolean {
        val dir = getModelDir(context)
        if (!dir.exists() && !dir.mkdirs()) return false

        var allInstalled = true
        for (artifact in REQUIRED_ARTIFACTS) {
            val target = File(dir, artifact.filename)
            if (target.isFile && target.length() >= artifact.expectedMinBytes) continue

            val assetPath = "${EnteFaceConstants.MODEL_SUBDIR}/${artifact.filename}"
            val copied = runCatching {
                context.assets.open(assetPath).use { input ->
                    val temp = File(dir, "${artifact.filename}.installing")
                    try {
                        temp.outputStream().buffered().use { output -> input.copyTo(output) }
                        if (temp.length() >= artifact.expectedMinBytes) {
                            if (target.exists()) target.delete()
                            val renamed = temp.renameTo(target)
                            if (!renamed) {
                                temp.copyTo(target, overwrite = true)
                            }
                            true
                        } else {
                            temp.delete()
                            false
                        }
                    } finally {
                        temp.delete()
                    }
                }
            }.getOrDefault(false)

            if (!copied) allInstalled = false
        }
        return allInstalled && isInstalled(context)
    }


    suspend fun downloadOrInstallModel(
        context: Context,
        force: Boolean = false,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onStage: (String) -> Unit = {},
        awaitIfResumed: suspend () -> Unit = {},
        onDetailedProgress: (DetailedDownloadProgress) -> Unit = {}
    ) = downloadOrInstallModelImpl(context, force, onProgress, onStage, awaitIfResumed, onDetailedProgress)
}
