package com.amresalehin.emregallery.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File
import java.security.MessageDigest
import java.util.Locale

internal data class DownloadTargets(
    val finalFile: File,
    val tempFile: File,
    val metaFile: File
)

internal data class ResumeMetadata(
    val etag: String? = null,
    val lastModified: String? = null
)

/**
 * Storage path resolution, deterministic naming, resume metadata persistence, and media scanning.
 */
internal object MediaDownloadStorage {

    private val invalidFilenameChars = Regex("[^a-zA-Z0-9._-]")
    private val invalidBaseChars = Regex("[^a-zA-Z0-9_-]")

    fun sanitizeFileName(name: String): String = name.replace(invalidFilenameChars, "_")

    fun generateDeterministicId(mediaUrl: String, fileName: String): String {
        val hash = runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(mediaUrl.toByteArray(Charsets.UTF_8))
            bytes.take(8).joinToString("") { "%02x".format(it) }
        }.getOrDefault(Integer.toHexString(mediaUrl.hashCode()))
        val base = fileName.substringBeforeLast('.').replace(invalidBaseChars, "_").take(32)
        val ext = fileName.substringAfterLast('.', "")
        return if (ext.isNotEmpty()) "${base}_$hash.$ext" else "${base}_$hash"
    }

    fun getTargetStorageDir(context: Context): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val emreGalleryDir = File(publicDownloads, "Emre Gallery")
        if (!emreGalleryDir.exists()) {
            emreGalleryDir.mkdirs()
        }
        return if (emreGalleryDir.exists() && emreGalleryDir.canWrite()) {
            emreGalleryDir
        } else {
            val appExternal = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val fallbackFolder = File(appExternal ?: context.filesDir, "Emre Gallery")
            if (!fallbackFolder.exists()) fallbackFolder.mkdirs()
            fallbackFolder
        }
    }

    fun resolveDownloadTargets(
        context: Context,
        mediaUrl: String,
        fileName: String,
        downloadId: String?
    ): DownloadTargets {
        val targetDir = getTargetStorageDir(context)
        val sanitizedName = sanitizeFileName(fileName)
        val deterministicBase = if (!downloadId.isNullOrBlank()) {
            val cleanId = sanitizeFileName(downloadId)
            val ext = sanitizedName.substringAfterLast('.', "")
            if (ext.isNotEmpty() && !cleanId.endsWith(".$ext", ignoreCase = true)) {
                "$cleanId.$ext"
            } else {
                cleanId
            }
        } else {
            generateDeterministicId(mediaUrl, sanitizedName)
        }

        return DownloadTargets(
            finalFile = File(targetDir, deterministicBase),
            tempFile = File(targetDir, "$deterministicBase.downloading"),
            metaFile = File(targetDir, "$deterministicBase.meta")
        )
    }

    fun readResumeMetadata(metaFile: File): ResumeMetadata {
        if (!metaFile.exists()) return ResumeMetadata()
        var savedEtag: String? = null
        var savedLastModified: String? = null
        runCatching {
            metaFile.readLines().forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    when (parts[0].trim().lowercase(Locale.ROOT)) {
                        "etag" -> savedEtag = parts[1].trim().takeIf { it.isNotEmpty() }
                        "last_modified" -> savedLastModified = parts[1].trim().takeIf { it.isNotEmpty() }
                    }
                }
            }
        }
        return ResumeMetadata(etag = savedEtag, lastModified = savedLastModified)
    }

    fun writeResumeMetadata(metaFile: File, etag: String?, lastModified: String?) {
        if (!etag.isNullOrBlank() || !lastModified.isNullOrBlank()) {
            runCatching {
                metaFile.writeText("etag=${etag.orEmpty()}\nlast_modified=${lastModified.orEmpty()}")
            }
        }
    }

    fun scanMediaFile(context: Context, file: File, mimeType: String?) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mimeType),
            null
        )
    }
}
