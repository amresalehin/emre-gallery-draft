package com.amresalehin.emregallery.ffmpeg

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import kotlin.math.abs


data class FfmpegEngineStatus(
    val isAvailable: Boolean,
    val version: String,
    val buildDate: String,
    val packageName: String,
    val supportedCodecsSummary: String,
    val isFfprobeAvailable: Boolean
)

data class FfprobeMediaInfo(
    val format: String,
    val durationSeconds: Double,
    val sizeBytes: Long,
    val bitrateKbps: Long,
    val videoCodec: String?,
    val resolution: String?,
    val frameRate: Double?,
    val audioCodec: String?,
    val audioChannels: Int?,
    val sampleRateHz: Int?,
    val streamCount: Int,
    val rawMetadata: Map<String, String>
)

data class FfmpegExecutionResult(
    val isSuccess: Boolean,
    val returnCode: Int,
    val outputLogs: String,
    val executionTimeMs: Long,
    val outputFile: File? = null
)


/**
 * Uses FFprobe to inspect media streams, codecs, bitrates, audio tracks and duration.
 */
internal suspend fun FfmpegKitEngine.probeMediaImpl(filePathOrUri: String, context: Context? = null): FfprobeMediaInfo? = withContext(Dispatchers.IO) {
    try {
        val localPath = if (filePathOrUri.startsWith("content://") && context != null) {
            // If content uri, resolve to real file or copy temporarily if needed
            resolveUriToPath(context, Uri.parse(filePathOrUri)) ?: filePathOrUri
        } else {
            filePathOrUri
        }

        val session = FFprobeKit.getMediaInformation(localPath)
        val info = session.mediaInformation ?: return@withContext null

        val format = info.format ?: "unknown"
        val durationSec = info.duration?.toDoubleOrNull() ?: 0.0
        val sizeBytes = info.size?.toLongOrNull() ?: 0L
        val bitrateKbps = (info.bitrate?.toLongOrNull() ?: 0L) / 1000L

        var videoCodec: String? = null
        var resolution: String? = null
        var frameRate: Double? = null
        var audioCodec: String? = null
        var audioChannels: Int? = null
        var sampleRateHz: Int? = null

        val streams = info.streams ?: emptyList()
        for (stream in streams) {
            when (stream.type?.lowercase()) {
                "video" -> {
                    if (videoCodec == null) {
                        videoCodec = stream.codec?.uppercase()
                        val width = stream.width
                        val height = stream.height
                        if (width != null && height != null && width > 0 && height > 0) {
                            resolution = "${width}x${height}"
                        }
                        frameRate = stream.averageFrameRate?.toDoubleOrNull()
                            ?: stream.realFrameRate?.toDoubleOrNull()
                    }
                }
                "audio" -> {
                    if (audioCodec == null) {
                        audioCodec = stream.codec?.uppercase()
                        audioChannels = stream.getStringProperty("channels")?.toIntOrNull()
                            ?: stream.allProperties?.optInt("channels")
                        sampleRateHz = stream.sampleRate?.toIntOrNull()
                    }
                }
            }
        }

        val tagsMap = mutableMapOf<String, String>()
        val tagsObj = info.tags
        if (tagsObj != null) {
            val keys = tagsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = tagsObj.optString(k, "")
                if (k.isNotBlank() && v.isNotBlank()) {
                    tagsMap[k] = v
                }
            }
        }

        FfprobeMediaInfo(
            format = format,
            durationSeconds = durationSec,
            sizeBytes = sizeBytes,
            bitrateKbps = bitrateKbps,
            videoCodec = videoCodec,
            resolution = resolution,
            frameRate = frameRate,
            audioCodec = audioCodec,
            audioChannels = audioChannels,
            sampleRateHz = sampleRateHz,
            streamCount = streams.size,
            rawMetadata = tagsMap
        )
    } catch (e: Throwable) {
        Log.e(TAG, "FFprobe inspection failed for $filePathOrUri", e)
        null
    }
}

internal fun FfmpegKitEngine.getExportDirectory(context: Context?): File {
    val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val emreGalleryDir = File(publicDownloads, "Emre Gallery")
    if (!emreGalleryDir.exists()) {
        emreGalleryDir.mkdirs()
    }
    if (emreGalleryDir.exists() && emreGalleryDir.canWrite()) {
        return emreGalleryDir
    }
    val appExternal = context?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    val fallbackFolder = File(appExternal ?: context?.filesDir, "Emre Gallery")
    if (!fallbackFolder.exists()) fallbackFolder.mkdirs()
    return fallbackFolder
}

internal fun FfmpegKitEngine.resolveUriToPath(context: Context, uri: Uri): String? {
    if (uri.scheme == "file") return uri.path
    if (uri.scheme == "content") {
        val proj = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
        try {
            context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                    if (idx != -1) {
                        return cursor.getString(idx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed resolving content URI to file path: ${e.message}")
        }
    }
    return null
}

internal fun FfmpegKitEngine.resolveUriToFileImpl(context: Context, uriString: String, fallbackName: String = "media"): File? {
    if (uriString.startsWith("/")) {
        val f = File(uriString)
        if (f.exists()) return f
    }
    if (uriString.startsWith("file://")) {
        val f = File(Uri.parse(uriString).path ?: "")
        if (f.exists()) return f
    }
    val uri = Uri.parse(uriString)
    val directPath = resolveUriToPath(context, uri)
    if (!directPath.isNullOrBlank()) {
        val f = File(directPath)
        if (f.exists()) return f
    }
    // Stream copy fallback for SAF content uris
    return try {
        val safeName = fallbackName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val cacheFile = File(context.cacheDir, "ffmpeg_in_${abs(uriString.hashCode())}_$safeName")
        if (cacheFile.exists() && cacheFile.length() > 0L) return cacheFile
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (cacheFile.exists() && cacheFile.length() > 0L) cacheFile else null
    } catch (e: Exception) {
        Log.w(TAG, "resolveUriToFile failed: ${e.message}")
        null
    }
}
