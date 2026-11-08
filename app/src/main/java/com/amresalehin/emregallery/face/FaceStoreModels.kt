package com.amresalehin.emregallery.face

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.RectF
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow


/**
 * Event bus for notifying UI observers when media face records or clusters change.
 */
object FaceStoreEvents {
    private val _mediaUpdates = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val mediaUpdates = _mediaUpdates.asSharedFlow()

    fun notifyMediaUpdated(uri: String) {
        _mediaUpdates.tryEmit(uri)
    }

    private val _peopleUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val peopleUpdates = _peopleUpdates.asSharedFlow()

    fun notifyPeopleUpdated() { _peopleUpdates.tryEmit(Unit) }

    fun notifyAllUpdated() {
        _mediaUpdates.tryEmit("")
        notifyPeopleUpdated()
    }
}

data class EmbeddingProvenance(
    val engineId: String = BuffaloLFaceEngine.ENGINE_ID,
    val modelVersion: String = BuffaloLFaceEngine.MODEL_VERSION,
    val dimension: Int = BuffaloLFaceEngine.EMBEDDING_DIMENSION,
    val formatVersion: Int = BuffaloLFaceEngine.FORMAT_VERSION
) {
    fun isCompatibleWith(other: EmbeddingProvenance): Boolean {
        return engineId == other.engineId &&
               modelVersion == other.modelVersion &&
               dimension == other.dimension &&
               formatVersion == other.formatVersion
    }

    companion object {
        val BUFFALO_L = EmbeddingProvenance(
            engineId = BuffaloLFaceEngine.ENGINE_ID,
            modelVersion = BuffaloLFaceEngine.MODEL_VERSION,
            dimension = BuffaloLFaceEngine.EMBEDDING_DIMENSION,
            formatVersion = BuffaloLFaceEngine.FORMAT_VERSION
        )
        val BUFFALO_L_512 = BUFFALO_L
    }
}

data class PersonSummary(
    val id: String,
    val displayName: String,
    val faceCount: Int,
    val sampleUri: String?,
    val isHidden: Boolean = false,
    val isNamed: Boolean = false,
    val clusterCount: Int = 1,
    val updatedAt: Long = 0L,
    val engineId: String = BuffaloLFaceEngine.ENGINE_ID,
    val portraitUri: String? = null,
    val portraitFaceIndex: Int? = null
)

data class PersonMediaIndex(
    val faceCount: Int,
    val mediaUris: Set<String>
)

data class FaceClusterSummary(
    val id: String,
    val displayName: String,
    val faceCount: Int,
    val sampleUri: String?
)

data class StoredFace(
    val mediaUri: String,
    val faceIndex: Int,
    val clusterId: String,
    val embedding: FloatArray,
    val personId: String? = null,
    val boxLeft: Float = 0f,
    val boxTop: Float = 0f,
    val boxRight: Float = 0f,
    val boxBottom: Float = 0f,
    val confidence: Float = 1f,
    val quality: Float = 1f,
    val engineId: String = BuffaloLFaceEngine.ENGINE_ID,
    val modelVersion: String = BuffaloLFaceEngine.MODEL_VERSION
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredFace) return false
        if (mediaUri != other.mediaUri) return false
        if (faceIndex != other.faceIndex) return false
        if (clusterId != other.clusterId) return false
        if (personId != other.personId) return false
        if (engineId != other.engineId) return false
        if (!embedding.contentEquals(other.embedding)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = mediaUri.hashCode()
        result = 31 * result + faceIndex
        result = 31 * result + clusterId.hashCode()
        result = 31 * result + (personId?.hashCode() ?: 0)
        result = 31 * result + engineId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

data class DetectedMediaFace(
    val mediaUri: String,
    val faceIndex: Int,
    val clusterId: String,
    val displayName: String,
    val personId: String? = null,
    val boxLeft: Float = 0f,
    val boxTop: Float = 0f,
    val boxRight: Float = 0f,
    val boxBottom: Float = 0f,
    val engineId: String = BuffaloLFaceEngine.ENGINE_ID,
    val portraitMediaUri: String? = null,
    val portraitFaceIndex: Int? = null
)

data class ReviewSuggestion(
    val id: String,
    val faceId: String,
    val mediaUri: String,
    val faceIndex: Int,
    val suggestedPersonId: String,
    val suggestedPersonName: String,
    val distance: Float,
    val status: String = "pending",
    val engineId: String = BuffaloLFaceEngine.ENGINE_ID
)

data class EngineComparisonFace(
    val faceIndex: Int,
    val bounds: RectF,
    val confidence: Float,
    val quality: Float
)

data class EngineComparisonResult(
    val mediaUri: String,
    val engineA: String,
    val facesA: List<EngineComparisonFace>,
    val engineB: String,
    val facesB: List<EngineComparisonFace>,
    val matchingFaceCount: Int
)

/**
 * Modern, multi-engine face clustering and identity management store.
 *
 * Engines (production and experimental) operate with complete isolation:
 * - Engine-specific embeddings and model versions.
 * - Engine-partitioned face indexing status (indexing under one engine never causes another engine to skip a photo).
 * - Isolated clustering and people identities preventing experimental engines from corrupting production identities.
 * - Direct cross-engine comparison mechanism on shared photograph sets.
 */
