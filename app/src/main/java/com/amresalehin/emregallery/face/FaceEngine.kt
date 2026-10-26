package com.amresalehin.emregallery.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri

data class DetectedFaceEmbedding(
    val faceIndex: Int,
    val bounds: RectF,
    val embedding: FloatArray,
    val score: Float = 1.0f,
    val quality: Float = 1.0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectedFaceEmbedding) return false
        if (faceIndex != other.faceIndex) return false
        if (bounds != other.bounds) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (score != other.score) return false
        if (quality != other.quality) return false
        return true
    }

    override fun hashCode(): Int {
        var result = faceIndex
        result = 31 * result + bounds.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + score.hashCode()
        result = 31 * result + quality.hashCode()
        return result
    }
}

/**
 * Standard interface for face detection and recognition engines.
 * Each engine declares its identity, model version, and embedding dimension.
 */
interface FaceEngine : AutoCloseable {
    val engineId: String
    val modelVersion: String
    val embeddingDimension: Int
    val formatVersion: Int get() = 1

    /**
     * Detects faces in the given media URI and produces normalized embeddings.
     */
    fun embed(uri: Uri): List<DetectedFaceEmbedding>

    /**
     * Direct bitmap embedding for in-memory operations and evaluation benchmarks.
     */
    fun embedBitmap(bitmap: Bitmap): List<DetectedFaceEmbedding> = emptyList()

    override fun close() {}
}

/**
 * Factory for creating and managing models of a specific face recognition engine.
 */
interface FaceEngineFactory {
    val engineId: String
    val displayName: String
    val modelVersion: String
    val embeddingDimension: Int
    val isExperimental: Boolean get() = false

    fun isInstalled(context: Context): Boolean
    fun create(context: Context, detectionThreshold: Float): FaceEngine

    suspend fun downloadModel(
        context: Context,
        force: Boolean = false,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onStage: (String) -> Unit = {},
        awaitIfResumed: suspend () -> Unit = {}
    )
}

/**
 * Registry of available face recognition engines, supporting active production
 * engines and experimental engines running side-by-side without interference.
 */
object FaceEngineRegistry {
    private val factories = mutableMapOf<String, FaceEngineFactory>()

    @Volatile
    private var activeEngineId: String = "buffalo_l"

    fun register(factory: FaceEngineFactory) {
        synchronized(factories) {
            factories[factory.engineId] = factory
        }
    }

    fun getFactory(engineId: String): FaceEngineFactory? {
        return synchronized(factories) { factories[engineId] }
    }

    fun getActiveFactory(): FaceEngineFactory {
        return synchronized(factories) {
            factories[activeEngineId] ?: BuffaloLFaceEngineFactory
        }
    }

    fun setActiveEngine(engineId: String) {
        synchronized(factories) {
            if (factories.containsKey(engineId)) {
                activeEngineId = engineId
            }
        }
    }

    val activeId: String get() = activeEngineId

    fun listEngines(): List<FaceEngineFactory> {
        return synchronized(factories) { factories.values.toList() }
    }
}
