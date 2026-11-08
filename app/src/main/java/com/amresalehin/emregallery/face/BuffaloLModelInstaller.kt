package com.amresalehin.emregallery.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.StatFs
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**

internal object BuffaloLModelInstaller {
        fun isModelInstalled(context: Context): Boolean {
            val dir = File(context.filesDir, "face-models/buffalo_l")
            return BuffaloLFaceEngine.REQUIRED_MODELS.all { name ->
                dir.resolve(name).let { it.isFile && it.length() > 1024 * 1024 }
            }
        }

        private fun openConnectionWithRedirects(initialUrl: String, existingBytes: Long = 0L): HttpURLConnection {
            var url = initialUrl
            var redirects = 0
            while (redirects < 6) {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 30_000
                conn.readTimeout = 120_000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) EmreGallery/1.0")
                if (existingBytes > 0L) {
                    conn.setRequestProperty("Range", "bytes=$existingBytes-")
                }
                conn.connect()
                val code = conn.responseCode
                if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!location.isNullOrBlank()) {
                        url = location
                        redirects++
                        continue
                    }
                }
                return conn
            }
            error("Too many redirects connecting to $initialUrl")
        }

        @Synchronized
        fun installBundledModel(context: Context) {
            if (isModelInstalled(context)) return
            val modelDir = File(context.filesDir, "face-models/buffalo_l")
            if (!modelDir.exists() && !modelDir.mkdirs()) {
                error("Cannot create face model directory.")
            }
            val availableBytes = StatFs(modelDir.absolutePath).availableBytes
            check(availableBytes >= BuffaloLFaceEngine.MIN_FREE_SPACE_BYTES) {
                "At least 60 MB of free storage is required to install Buffalo_L model."
            }

            BuffaloLFaceEngine.REQUIRED_MODELS.forEach { name ->
                val target = modelDir.resolve(name)
                if (target.isFile && target.length() > 1024 * 1024) return@forEach
                val temporary = modelDir.resolve("$name.installing")
                try {
                    context.assets.open("face-models/buffalo_l/$name").use { input ->
                        temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                    check(temporary.length() > 1024 * 1024) {
                        "Bundled Buffalo_L model file is incomplete: $name"
                    }
                    if (target.exists()) target.delete()
                    if (!temporary.renameTo(target)) temporary.copyTo(target, overwrite = true)
                } finally {
                    temporary.delete()
                }
            }
        }

        suspend fun downloadModel(
            context: Context,
            force: Boolean = false,
            onProgress: (Long, Long) -> Unit = { _, _ -> },
            onStage: (String) -> Unit = {},
            awaitIfResumed: suspend () -> Unit = {},
            onDetailedProgress: (DetailedDownloadProgress) -> Unit = {}
        ) {
            val modelDir = File(context.filesDir, "face-models/buffalo_l")
            if (!modelDir.exists() && !modelDir.mkdirs()) error("Cannot create face model directory.")
            if (!force && isModelInstalled(context)) {
                onStage("ready")
                onDetailedProgress(DetailedDownloadProgress(288L * 1024 * 1024, 288L * 1024 * 1024, 0L, 0L, "ready"))
                return
            }
            val availableBytes = StatFs(modelDir.absolutePath).availableBytes
            check(availableBytes >= BuffaloLFaceEngine.MIN_FREE_SPACE_BYTES) {
                "At least 60 MB of free storage is required to install Buffalo_L."
            }

            val archive = File(modelDir, "buffalo_l.zip.part")
            val staging = File(modelDir, "installing").apply { deleteRecursively(); mkdirs() }
            val existingBytes = if (archive.exists()) archive.length() else 0L

            onStage("connecting")
            val estimatedTotal = 288L * 1024 * 1024
            onDetailedProgress(DetailedDownloadProgress(existingBytes, estimatedTotal, 0L, 0L, "connecting"))

            val connection = openConnectionWithRedirects(BuffaloLFaceEngine.MODEL_URL, existingBytes)
            var responseTotal = estimatedTotal
            try {
                val code = connection.responseCode
                val isPartial = code == 206
                val isOk = code in 200..299

                if (!isOk && !isPartial) {
                    if (code != 416) {
                        error("Could not download Buffalo_L model pack (HTTP $code).")
                    }
                }

                val appendMode = isPartial && existingBytes > 0L
                val serverContentLength = connection.contentLengthLong.coerceAtLeast(-1L)
                val total = if (appendMode) {
                    existingBytes + (if (serverContentLength > 0) serverContentLength else 0L)
                } else if (serverContentLength > 0) {
                    serverContentLength
                } else {
                    estimatedTotal
                }
                responseTotal = total
                onStage("downloading")

                val downloadStartTime = System.currentTimeMillis()
                var received = if (appendMode) existingBytes else 0L
                var sessionDownloaded = 0L

                if (code != 416) {
                    BufferedInputStream(connection.inputStream).use { input ->
                        java.io.FileOutputStream(archive, appendMode).buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                awaitIfResumed()
                                if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled.")
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                received += count
                                sessionDownloaded += count

                                val elapsedSec = ((System.currentTimeMillis() - downloadStartTime) / 1000.0).coerceAtLeast(0.1)
                                val speed = (sessionDownloaded / elapsedSec).toLong()
                                val remainingBytes = (total - received).coerceAtLeast(0L)
                                val etaSec = if (speed > 0) remainingBytes / speed else 0L

                                onProgress(received, total)
                                onDetailedProgress(
                                    DetailedDownloadProgress(
                                        bytesReceived = received,
                                        totalBytes = total,
                                        speedBytesPerSec = speed,
                                        etaSeconds = etaSec,
                                        stage = "downloading"
                                    )
                                )
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            try {
                onStage("verifying")
                onDetailedProgress(
                    DetailedDownloadProgress(responseTotal, responseTotal, 0L, 0L, "verifying")
                )
                if (!archive.exists() || archive.length() < 10L * 1024 * 1024) {
                    archive.delete()
                    error("Downloaded archive is invalid or incomplete.")
                }

                onStage("extracting")
                onDetailedProgress(
                    DetailedDownloadProgress(responseTotal, responseTotal, 0L, 0L, "extracting")
                )
                ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        awaitIfResumed()
                        if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled.")
                        if (!entry.isDirectory) {
                            val name = entry.name.substringAfterLast('/')
                            if (name.endsWith(".onnx") || name.contains("LICENSE", true)) {
                                val destination = staging.resolve(name)
                                destination.outputStream().buffered().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        awaitIfResumed()
                                        if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled.")
                                        val count = zip.read(buffer)
                                        if (count < 0) break
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                val missing = BuffaloLFaceEngine.REQUIRED_MODELS.filter { name ->
                    staging.resolve(name).let { !it.isFile || it.length() < 1024 * 1024 }
                }
                if (missing.isNotEmpty()) error("Incomplete Buffalo_L archive; missing: " + missing.joinToString())

                onStage("installing")
                onDetailedProgress(
                    DetailedDownloadProgress(responseTotal, responseTotal, 0L, 0L, "installing")
                )
                BuffaloLFaceEngine.REQUIRED_MODELS.forEach { name ->
                    awaitIfResumed()
                    if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled.")
                    val source = staging.resolve(name)
                    val target = modelDir.resolve(name)
                    if (target.exists()) target.delete()
                    if (!source.renameTo(target)) source.copyTo(target, overwrite = true)
                }
                staging.listFiles()?.filter { it.isFile && it.name.contains("LICENSE", true) }?.forEach {
                    it.copyTo(modelDir.resolve(it.name), overwrite = true)
                }
                check(isModelInstalled(context)) { "Buffalo_L model installation failed validation." }
                if (responseTotal > 0L) onProgress(responseTotal, responseTotal)
                onStage("ready")
                onDetailedProgress(
                    DetailedDownloadProgress(responseTotal, responseTotal, 0L, 0L, "ready")
                )
            } finally {
                archive.delete()
                staging.deleteRecursively()
            }
        }

    }