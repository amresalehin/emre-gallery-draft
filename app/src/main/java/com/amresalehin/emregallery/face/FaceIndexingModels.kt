package com.amresalehin.emregallery.face

import android.content.Context
import android.net.Uri
import com.amresalehin.emregallery.data.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap


enum class StageState {
    PENDING,
    RUNNING,
    COMPLETED,
    PAUSED,
    FAILED
}

data class StageProgress(
    val state: StageState = StageState.PENDING,
    val processed: Int = 0,
    val total: Int = 0,
    val percentage: Int = 0
)

data class FaceIndexingProgress(
    val processed: Int = 0,
    val total: Int = 0,
    val facesFound: Int = 0,
    val photosWithFaces: Int = 0,
    val failed: Int = 0,
    val currentItemName: String = "",
    val currentItemStatus: String = "",
    val groupingStatus: String = "",
    val currentItemFaces: Int? = null,
    val engineId: String = BuffaloLFaceEngine.ENGINE_ID,
    val engineName: String = "",
    val detectionStage: StageProgress = StageProgress(),
    val embeddingStage: StageProgress = StageProgress(),
    val clusteringStage: StageProgress = StageProgress(),
    val failedItems: List<Pair<String, String>> = emptyList()
)

