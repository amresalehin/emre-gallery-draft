package com.amresalehin.emregallery

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

import android.content.Context
import android.provider.Settings

import com.amresalehin.emregallery.data.MediaStoreThumbnailFetcher

class GalleryApplication : Application() {
    companion object {
        fun disableTouchPointTaps(context: Context) {
            runCatching {
                Settings.System.putInt(context.contentResolver, "show_touches", 0)
            }
            runCatching {
                Settings.System.putInt(context.contentResolver, "pointer_location", 0)
            }
            runCatching {
                Runtime.getRuntime().exec("settings put system show_touches 0")
            }
            runCatching {
                Runtime.getRuntime().exec("settings put system pointer_location 0")
            }
            runCatching {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", "settings put system show_touches 0; settings put system pointer_location 0"))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        disableTouchPointTaps(this)
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.35)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(262144000L)
                    .build()
            }
            .components {
                add(MediaStoreThumbnailFetcher.Factory(this@GalleryApplication))
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .allowHardware(true)
            .build()
        Coil.setImageLoader(imageLoader)

        // Deterministic registration of both face recognition engines
        com.amresalehin.emregallery.face.FaceEngineRegistry.register(com.amresalehin.emregallery.face.BuffaloLFaceEngineFactory)
        com.amresalehin.emregallery.face.FaceEngineRegistry.register(com.amresalehin.emregallery.face.EnteFaceEngineFactory)
    }
}
