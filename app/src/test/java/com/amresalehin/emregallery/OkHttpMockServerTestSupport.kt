package com.amresalehin.emregallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emregallery.data.OkHttpDownloader
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import java.io.File

/**
 * Shared MockWebServer lifecycle and partial-file seeding helpers for OkHttp downloader tests.
 */
internal object OkHttpMockServerTestSupport {

    fun withServer(block: suspend (server: MockWebServer, context: Context) -> Unit) = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val context: Context = ApplicationProvider.getApplicationContext()
            block(server, context)
        } finally {
            server.shutdown()
        }
    }

    fun seedPartialDownloadingFile(
        context: Context,
        downloadId: String,
        fileName: String,
        byteCount: Int,
        fillByte: Byte
    ): File {
        val targetDir = OkHttpDownloader.getTargetStorageDir(context)
        val tempFile = File(targetDir, "$downloadId.$fileName.downloading")
        tempFile.writeBytes(ByteArray(byteCount) { fillByte })
        return tempFile
    }
}
