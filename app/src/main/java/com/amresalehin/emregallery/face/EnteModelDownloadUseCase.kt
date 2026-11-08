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


internal suspend fun EnteFaceModelManager.downloadOrInstallModelImpl(
    context: Context,
    force: Boolean = false,
    onProgress: (Long, Long) -> Unit = { _, _ -> },
    onStage: (String) -> Unit = {},
    awaitIfResumed: suspend () -> Unit = {},
    onDetailedProgress: (DetailedDownloadProgress) -> Unit = {}
) {
    val modelDir = getModelDir(context)
    if (!modelDir.exists() && !modelDir.mkdirs()) {
        error("Cannot create model directory: ${modelDir.absolutePath}")
    }

    if (!force && isInstalled(context)) {
        onStage("ready")
        onDetailedProgress(DetailedDownloadProgress(25_000_000L, 25_000_000L, 0L, 0L, "ready"))
        return
    }

    // Check if bundled assets can be installed first
    if (installFromAssetsIfPresent(context) && !force) {
        onStage("ready")
        onDetailedProgress(DetailedDownloadProgress(25_000_000L, 25_000_000L, 0L, 0L, "ready"))
        return
    }

    val availableBytes = StatFs(modelDir.absolutePath).availableBytes
    check(availableBytes >= EnteFaceConstants.MIN_FREE_SPACE_BYTES) {
        "Insufficient disk space. At least 35MB required for Ente model installation."
    }

    val stagingDir = File(modelDir, "staging").apply {
        if (!exists()) mkdirs()
    }

    val totalBytesEstimated = 25L * 1024 * 1024 // ~25MB total across both models
    var cumulativeBytes = 0L

    try {
        onStage("connecting")
        onDetailedProgress(DetailedDownloadProgress(0L, totalBytesEstimated, 0L, 0L, "connecting"))

        for (artifact in REQUIRED_ARTIFACTS) {
            awaitIfResumed()
            if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled.")

            val target = File(modelDir, artifact.filename)
            if (!force && target.isFile && target.length() >= artifact.expectedMinBytes) {
                cumulativeBytes += target.length()
                onProgress(cumulativeBytes, totalBytesEstimated)
                onDetailedProgress(
                    DetailedDownloadProgress(cumulativeBytes, totalBytesEstimated, 0L, 0L, "downloading")
                )
                continue
            }

            val downloadUrl = artifact.downloadUrl
                ?: error("No download URL configured for artifact ${artifact.filename}")

            val stagingFile = File(stagingDir, "${artifact.filename}.part")
            val downloadStartTime = System.currentTimeMillis()
            var sessionDownloadedBytes = 0L

            downloadFile(
                url = downloadUrl,
                destination = stagingFile,
                onBytes = { readChunk ->
                    cumulativeBytes += readChunk
                    sessionDownloadedBytes += readChunk
                    val elapsedSec = ((System.currentTimeMillis() - downloadStartTime) / 1000.0).coerceAtLeast(0.1)
                    val speed = (sessionDownloadedBytes / elapsedSec).toLong()
                    val remainingBytes = (totalBytesEstimated - cumulativeBytes).coerceAtLeast(0L)
                    val etaSec = if (speed > 0) remainingBytes / speed else 0L

                    onProgress(cumulativeBytes, totalBytesEstimated)
                    onDetailedProgress(
                        DetailedDownloadProgress(
                            bytesReceived = cumulativeBytes,
                            totalBytes = totalBytesEstimated,
                            speedBytesPerSec = speed,
                            etaSeconds = etaSec,
                            stage = "downloading"
                        )
                    )
                },
                onConnecting = {
                    onStage("connecting")
                    onDetailedProgress(
                        DetailedDownloadProgress(cumulativeBytes, totalBytesEstimated, 0L, 0L, "connecting")
                    )
                },
                onDownloading = {
                    onStage("downloading")
                },
                awaitIfResumed = awaitIfResumed
            )

            // Validate artifact size and SHA-256 checksum
            onStage("verifying")
            onDetailedProgress(
                DetailedDownloadProgress(cumulativeBytes, totalBytesEstimated, 0L, 0L, "verifying")
            )

            if (stagingFile.length() < artifact.expectedMinBytes) {
                stagingFile.delete()
                error("Downloaded artifact ${artifact.filename} is incomplete (${stagingFile.length()} bytes).")
            }

            if (artifact.sha256Hex != null) {
                val computedHash = computeSha256(stagingFile)
                if (!computedHash.equals(artifact.sha256Hex, ignoreCase = true)) {
                    stagingFile.delete()
                    error("Cryptographic checksum mismatch for ${artifact.filename}: expected ${artifact.sha256Hex}, got $computedHash")
                }
            }
        }

        onStage("installing")
        onDetailedProgress(
            DetailedDownloadProgress(totalBytesEstimated, totalBytesEstimated, 0L, 0L, "installing")
        )

        for (artifact in REQUIRED_ARTIFACTS) {
            val stagingFile = File(stagingDir, "${artifact.filename}.part")
            if (stagingFile.exists()) {
                val target = File(modelDir, artifact.filename)
                if (target.exists()) target.delete()
                if (!stagingFile.renameTo(target)) {
                    stagingFile.copyTo(target, overwrite = true)
                    stagingFile.delete()
                }
            }
        }

        check(isInstalled(context)) { "Ente face model verification failed after installation." }
        onProgress(totalBytesEstimated, totalBytesEstimated)
        onStage("ready")
        onDetailedProgress(
            DetailedDownloadProgress(totalBytesEstimated, totalBytesEstimated, 0L, 0L, "ready")
        )
    } finally {
        stagingDir.deleteRecursively()
    }
}

internal suspend fun EnteFaceModelManager.downloadFile(
    url: String,
    destination: File,
    onBytes: (Int) -> Unit,
    onConnecting: () -> Unit = {},
    onDownloading: () -> Unit = {},
    awaitIfResumed: suspend () -> Unit
) {
    var currentUrl = url
    var redirects = 0
    var connection: HttpURLConnection? = null
    val existingBytes = if (destination.exists()) destination.length() else 0L

    onConnecting()
    while (redirects < 6) {
        val conn = URL(currentUrl).openConnection() as HttpURLConnection
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
                currentUrl = location
                redirects++
                continue
            }
        }
        connection = conn
        break
    }

    val activeConn = connection ?: error("Failed to establish connection to $url")
    try {
        val responseCode = activeConn.responseCode
        val isPartial = responseCode == 206
        val isOk = responseCode in 200..299

        if (!isOk && !isPartial) {
            if (responseCode == 416) {
                // Range not satisfiable: destination may already be completely downloaded
                return
            }
            error("HTTP error $responseCode while downloading $url")
        }

        onDownloading()
        val appendMode = isPartial && existingBytes > 0L
        val initialCredit = if (appendMode) existingBytes else 0L
        if (initialCredit > 0L) {
            onBytes(0) // signal resume
        }

        BufferedInputStream(activeConn.inputStream).use { input ->
            java.io.FileOutputStream(destination, appendMode).buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    awaitIfResumed()
                    if (Thread.currentThread().isInterrupted) throw CancellationException("Download cancelled.")
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    onBytes(count)
                }
            }
        }
    } finally {
        activeConn.disconnect()
    }
}
