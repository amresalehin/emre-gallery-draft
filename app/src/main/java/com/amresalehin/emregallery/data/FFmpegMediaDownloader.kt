package com.amresalehin.emregallery.data

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Native HLS/DASH downloader. Uses stream copy (no re-encoding) and writes to
 * an app-private temporary file before validating and committing the output.
 */
object FFmpegMediaDownloader {
    private const val TAG = "FFmpegMediaDownloader"

    data class Result(val file: File?, val success: Boolean, val diagnostic: String? = null)

    fun isManifestUrl(url: String): Boolean {
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault(url.substringBefore('?'))
        return path.endsWith(".m3u8", true) || path.endsWith(".mpd", true)
    }

    suspend fun download(
        sourceUrl: String,
        destination: File,
        headers: Map<String, String> = emptyMap()
    ): Result {
        if (!isManifestUrl(sourceUrl)) return Result(null, false, "Source is not an HLS/DASH manifest")
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, destination.nameWithoutExtension + ".ffmpeg.part.mp4")
        temp.delete()

        val args = mutableListOf("-hide_banner", "-nostdin", "-y", "-rw_timeout", "15000000")
        val requestHeaders = headers.toMutableMap()
        if (requestHeaders.keys.none { it.equals("User-Agent", true) }) {
            requestHeaders["User-Agent"] = OkHttpDownloader.DEFAULT_USER_AGENT
        }
        val headerBlock = requestHeaders.entries.joinToString("") { (key, value) -> "$key: $value\r\n" }
        if (headerBlock.isNotBlank()) args += listOf("-headers", headerBlock)
        args += listOf(
            "-i", sourceUrl,
            "-map", "0:v?", "-map", "0:a?",
            "-c", "copy", "-movflags", "+faststart", "-f", "mp4", temp.absolutePath
        )

        return try {
            val (ok, diagnostic) = execute(args)
            if (!ok || !temp.isFile || temp.length() == 0L) {
                temp.delete()
                Result(null, false, diagnostic ?: "FFmpeg produced no output")
            } else {
                if (destination.exists()) destination.delete()
                val committed = temp.renameTo(destination).let { renamed ->
                    if (renamed) true else runCatching {
                        temp.copyTo(destination, overwrite = true)
                        temp.delete()
                        true
                    }.getOrDefault(false)
                }
                if (committed && destination.isFile && destination.length() > 0L) {
                    Result(destination, true)
                } else {
                    temp.delete()
                    destination.delete()
                    Result(null, false, "Could not commit FFmpeg output")
                }
            }
        } catch (cancelled: CancellationException) {
            temp.delete()
            throw cancelled
        } catch (error: Exception) {
            temp.delete()
            Log.e(TAG, "FFmpeg stream download failed", error)
            Result(null, false, error.message ?: "FFmpeg execution failed")
        }
    }

    private suspend fun execute(arguments: List<String>): Pair<Boolean, String?> =
        suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            // Pass the argument vector directly: this avoids command-string parsing and
            // preserves spaces/CRLF in HTTP headers and paths as single arguments.
            val session: FFmpegSession = FFmpegKit.executeWithArgumentsAsync(arguments.toTypedArray()) { completed ->
                if (finished.compareAndSet(false, true) && continuation.isActive) {
                    val success = ReturnCode.isSuccess(completed.returnCode)
                    val diagnostic = if (success) null else
                        (completed.failStackTrace ?: completed.allLogsAsString)
                    continuation.resume(success to diagnostic?.take(4000))
                }
            }
            continuation.invokeOnCancellation {
                if (finished.compareAndSet(false, true)) FFmpegKit.cancel(session.sessionId)
            }
        }
}
