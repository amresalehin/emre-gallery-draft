package com.amresalehin.emregallery

import android.app.Application
import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.amresalehin.emregallery.data.MediaStoreThumbnailFetcher
import kotlinx.coroutines.Dispatchers
import java.io.File

class GalleryApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val isLowRam = activityManager?.isLowRamDevice ?: false
        // Use one larger cache budget across devices; retain a bound to avoid OOM process kills.
        val cachePercent = 0.35

        // Bounded CPU concurrency prevents thumbnail fetching/decoding from saturating all CPU cores
        // during fast scrolling, keeping UI and RenderThread at 60/120Hz without processor throttling.
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val workerParallelism = (cpuCores / 2).coerceIn(2, 4)
        val imageIoDispatcher = Dispatchers.IO.limitedParallelism(workerParallelism)
        val imageComputeDispatcher = Dispatchers.Default.limitedParallelism(workerParallelism)

        val imageLoader = ImageLoader.Builder(this)
            .fetcherDispatcher(imageIoDispatcher)
            .decoderDispatcher(imageComputeDispatcher)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(cachePercent)
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
            .bitmapConfig(if (isLowRam) android.graphics.Bitmap.Config.RGB_565 else android.graphics.Bitmap.Config.ARGB_8888)
            .allowRgb565(true)
            .allowHardware(true)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(imageLoader)

        // Deterministic registration of both face recognition engines
        com.amresalehin.emregallery.face.FaceEngineRegistry.register(com.amresalehin.emregallery.face.BuffaloLFaceEngineFactory)
        com.amresalehin.emregallery.face.FaceEngineRegistry.register(com.amresalehin.emregallery.face.EnteFaceEngineFactory)
        com.amresalehin.emregallery.face.FaceProcessingCoordinator.init(this)
    }
}

