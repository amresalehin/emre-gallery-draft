package com.amresalehin.emregallery

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.amresalehin.emregallery.data.MediaStoreThumbnailFetcher

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
                    .directory(cacheDir.resolve("thumbnail_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .components {
                add(MediaStoreThumbnailFetcher.Factory(applicationContext))
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .allowHardware(true)
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
