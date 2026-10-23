package com.amresalehin.emregallery

import android.app.Application
import coil.Coil
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

class GalleryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
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
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .allowHardware(true)
            .build()
        Coil.setImageLoader(imageLoader)

        // The buffalo_l weights are bundled with the APK. Copy them into app-private storage
        // off the main thread so face recognition is ready without a separate download action.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { BuffaloLFaceEngine.installBundledModel(this@GalleryApplication) }
        }
    }
}
