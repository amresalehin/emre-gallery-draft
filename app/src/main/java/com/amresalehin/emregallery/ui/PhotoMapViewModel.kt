package com.amresalehin.emregallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.PhotoMapLocationCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.osmdroid.util.BoundingBox

/**
 * ViewModel managing the photo GPS indexing, location cache, and UI state for the Photo Map screen.
 */
class PhotoMapViewModel(application: Application) : AndroidViewModel(application) {

    private val locationCache = PhotoMapLocationCache(application.applicationContext)
    private var indexingJob: Job? = null

    private val _uiState = MutableStateFlow(PhotoMapUiState())
    val uiState: StateFlow<PhotoMapUiState> = _uiState.asStateFlow()

    fun loadAndScanMedia(media: List<MediaItem>, forceRescan: Boolean = false) {
        indexingJob?.cancel()
        indexingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true,
                    scanProgress = 0,
                    scanTotal = 0
                )
            }

            if (forceRescan) {
                withContext(Dispatchers.IO) { locationCache.clearAll() }
            }

            val photos = media.filterNot { it.isVideo }
            val cached = withContext(Dispatchers.IO) { locationCache.loadAll() }
            val cachedLocated = photos.mapNotNull { item ->
                cached[item.uri]?.point?.let { item to it }
            }

            val uncachedPhotos = if (forceRescan) photos else photos.filterNot { cached.containsKey(it.uri) }
            _uiState.update {
                it.copy(
                    locatedMedia = cachedLocated,
                    scanTotal = uncachedPhotos.size,
                    loading = uncachedPhotos.isNotEmpty()
                )
            }

            if (uncachedPhotos.isEmpty()) {
                _uiState.update { it.copy(loading = false) }
                return@launch
            }

            val context = getApplication<Application>()
            val result = cachedLocated.toMutableList()
            var scanned = 0

            for (item in uncachedPhotos) {
                if (!isActive) break

                val point = withContext(Dispatchers.IO) {
                    PhotoMapLocationCache.extractLocation(context, item.uri)
                }

                withContext(Dispatchers.IO) {
                    locationCache.save(item.uri, point)
                }

                if (point != null) {
                    result.add(item to point)
                }
                scanned++

                if (scanned % 12 == 0 || scanned == uncachedPhotos.size) {
                    val currentList = result.toList()
                    val currentScanned = scanned
                    _uiState.update {
                        it.copy(
                            locatedMedia = currentList,
                            scanProgress = currentScanned
                        )
                    }
                    yield()
                }
            }

            _uiState.update {
                it.copy(
                    locatedMedia = result.toList(),
                    scanProgress = uncachedPhotos.size,
                    loading = false
                )
            }
        }
    }

    fun rescanMedia(media: List<MediaItem>) {
        loadAndScanMedia(media, forceRescan = true)
    }

    fun updateVisibleMapBounds(bounds: BoundingBox?) {
        _uiState.update { it.copy(visibleBounds = bounds) }
    }

    fun setMapLayer(layer: String) {
        _uiState.update { it.copy(mapLayer = layer, showMapLayerMenu = false) }
    }

    fun setLayerMenuExpanded(expanded: Boolean) {
        _uiState.update { it.copy(showMapLayerMenu = expanded) }
    }
}
