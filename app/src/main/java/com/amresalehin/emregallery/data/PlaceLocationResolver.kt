package com.amresalehin.emregallery.data

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache and helper for geocoded place names associated with MediaItem URIs.
 */
object PlaceLocationResolver {
    private val uriToPlaceCache = ConcurrentHashMap<String, String>()

    suspend fun resolvePlace(context: Context, uri: String, lat: Double, lon: Double): String? {
        uriToPlaceCache[uri]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    val addr = addresses?.firstOrNull()
                    if (addr != null) {
                        val placeName = addr.locality
                            ?: addr.subAdminArea
                            ?: addr.adminArea
                            ?: addr.featureName
                            ?: addr.countryName
                        if (!placeName.isNullOrBlank()) {
                            uriToPlaceCache[uri] = placeName
                            return@withContext placeName
                        }
                    }
                }
                null
            }.getOrNull()
        }
    }

    fun getCachedPlace(uri: String): String? = uriToPlaceCache[uri]
}
