package com.amresalehin.emregallery.data

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Downloads separate adaptive representations and losslessly remuxes them on-device.
 * FFmpegKit runs in-process; no desktop ffmpeg binary or PATH setup is required.
 */
object MediaStreamMerger {
    private const val TAG = "MediaStreamMerger"

    suspend fun downloadAndMerge(
        context: Context,
        videoUrl: String,
        audioUrl: String,
        fileName: String,
        referer: String?
    ): File = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "media-resolver/${UUID.randomUUID()}").apply { mkdirs() }
        val videoFile = File(workDir, "video.stream")
        val audioFile = File(workDir, "audio.stream")
        val outputDir = OkHttpDownloader.getTargetStorageDir(context)
        val safeBase = fileName.substringBeforeLast('.', fileName)
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val output = File(outputDir, "${safeBase}_${System.currentTimeMillis()}_merged.mkv")

        try {
            downloadToFile(videoUrl, videoFile, referer)
            downloadToFile(audioUrl, audioFile, referer)

            val command = arrayOf(
                "-y", "-i", videoFile.absolutePath, "-i", audioFile.absolutePath,
                "-map", "0:v:0", "-map", "1:a:0", "-c", "copy",
                "-max_interleave_delta", "0", output.absolutePath
            )
            val session = FFmpegKit.executeWithArguments(command)
            if (!ReturnCode.isSuccess(session.returnCode) || !output.exists() || output.length() == 0L) {
                throw IllegalStateException(
                    "FFmpeg could not merge video and audio: ${session.failStackTrace ?: session.output}"
                )
            }

            val probe = FFprobeKit.executeWithArguments(
                arrayOf("-v", "error", "-show_entries", "stream=codec_type", "-of", "csv=p=0", output.absolutePath)
            )
            val outputText = probe.output.orEmpty()
            if (!ReturnCode.isSuccess(probe.returnCode) ||
                !outputText.contains("video", true) || !outputText.contains("audio", true)
            ) {
                output.delete()
                throw IllegalStateException("Merged file failed validation: expected both video and audio streams.")
            }

            MediaScannerConnection.scanFile(context, arrayOf(output.absolutePath), arrayOf("video/x-matroska"), null)
            output
        } catch (error: Exception) {
            output.delete()
            Log.e(TAG, "Split-stream download/merge failed", error)
            throw error
        } finally {
            workDir.deleteRecursively()
        }
    }

    suspend fun remuxManifest(
        context: Context,
        manifestUrl: String,
        fileName: String,
        referer: String?
    ): File = withContext(Dispatchers.IO) {
        val outputDir = OkHttpDownloader.getTargetStorageDir(context)
        val safeBase = fileName.substringBeforeLast('.', fileName)
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val output = File(outputDir, "${safeBase}_${System.currentTimeMillis()}_stream.mkv")
        val headers = buildString {
            append("User-Agent: ${OkHttpDownloader.DEFAULT_USER_AGENT}\r\n")
            if (!referer.isNullOrBlank()) append("Referer: $referer\r\n")
            val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(manifestUrl)
            if (cookies.isNotBlank()) append("Cookie: $cookies\r\n")
        }
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-y", "-user_agent", OkHttpDownloader.DEFAULT_USER_AGENT, "-headers", headers,
                "-i", manifestUrl, "-map", "0:v?", "-map", "0:a?", "-c", "copy", output.absolutePath
            )
        )
        if (!ReturnCode.isSuccess(session.returnCode) || !output.exists() || output.length() == 0L) {
            output.delete()
            throw IllegalStateException("Stream remux failed: ${session.failStackTrace ?: session.output}")
        }
        val probe = FFprobeKit.executeWithArguments(
            arrayOf("-v", "error", "-show_entries", "stream=codec_type", "-of", "csv=p=0", output.absolutePath)
        )
        if (!ReturnCode.isSuccess(probe.returnCode) || !probe.output.orEmpty().contains("video", true)) {
            output.delete()
            throw IllegalStateException("Remuxed stream contains no valid video stream.")
        }
        MediaScannerConnection.scanFile(context, arrayOf(output.absolutePath), arrayOf("video/x-matroska"), null)
        output
    }

    fun hasAudio(file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        val probe = FFprobeKit.executeWithArguments(
            arrayOf("-v", "error", "-select_streams", "a:0", "-show_entries", "stream=codec_type", "-of", "csv=p=0", file.absolutePath)
        )
        return ReturnCode.isSuccess(probe.returnCode) && probe.output.orEmpty().contains("audio", true)
    }

    private fun downloadToFile(url: String, target: File, referer: String?) {
        val builder = Request.Builder().url(url).header("User-Agent", OkHttpDownloader.DEFAULT_USER_AGENT)
        if (!referer.isNullOrBlank()) builder.header("Referer", referer)
        OkHttpDownloader.client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Media stream request failed with HTTP ${response.code}.")
            val type = response.header("Content-Type").orEmpty().lowercase()
            if (type.contains("text/html") || type.contains("application/json")) {
                throw IllegalStateException("The server returned a webpage or JSON instead of a media stream.")
            }
            val body = response.body ?: throw IllegalStateException("Empty media stream response.")
            FileOutputStream(target).use { out -> body.byteStream().use { it.copyTo(out, 128 * 1024) } }
            if (target.length() <= 0L) throw IllegalStateException("Downloaded media stream was empty.")
        }
    }
}
