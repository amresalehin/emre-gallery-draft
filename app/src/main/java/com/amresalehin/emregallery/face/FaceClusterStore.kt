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


class FaceClusterStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        FaceDbSchema.createAll(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        FaceDbSchema.upgrade(db, oldVersion, newVersion)
    }

    data class MediaIndexedInfo(
        val mediaUri: String,
        val engineId: String,
        val indexedAt: Long,
        val detectedFaces: Int,
        val status: String,
        val errorMessage: String? = null
    )

    @Synchronized fun getFaceBounds(mediaUri: String, faceIndex: Int, engineId: String = BuffaloLFaceEngine.ENGINE_ID): RectF? =
        getFaceBoundsImpl(mediaUri, faceIndex, engineId)

    @Synchronized fun isMediaIndexed(uri: String, engineId: String = BuffaloLFaceEngine.ENGINE_ID): Boolean =
        isMediaIndexedImpl(uri, engineId)

    @Synchronized fun getMediaIndexedInfo(uri: String, engineId: String = BuffaloLFaceEngine.ENGINE_ID): MediaIndexedInfo? =
        getMediaIndexedInfoImpl(uri, engineId)

    @Synchronized fun clearMediaFaces(mediaUri: String, engineId: String = BuffaloLFaceEngine.ENGINE_ID) =
        clearMediaFacesImpl(mediaUri, engineId)

    @Synchronized fun getFailedMedia(engineId: String = BuffaloLFaceEngine.ENGINE_ID): List<Pair<String, String?>> =
        getFailedMediaImpl(engineId)

    @Synchronized fun getFailedMedia(mediaUris: Collection<String>, engineId: String = BuffaloLFaceEngine.ENGINE_ID): List<Pair<String, String?>> =
        getFailedMediaImpl(mediaUris, engineId)

    @Synchronized fun markMediaFailed(uri: String, engineId: String = BuffaloLFaceEngine.ENGINE_ID, errorMessage: String? = null) =
        markMediaFailedImpl(uri, engineId, errorMessage)

    @Synchronized fun markMediaIndexed(uri: String, detectedFaces: Int, engineId: String = BuffaloLFaceEngine.ENGINE_ID) =
        markMediaIndexedImpl(uri, detectedFaces, engineId)

    @Synchronized fun indexedMediaCount(engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
        indexedMediaCountImpl(engineId)

    @Synchronized fun indexedMediaCount(mediaUris: List<String>, engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
        indexedMediaCountImpl(mediaUris, engineId)

    @Synchronized fun mediaWithFacesCount(engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
        mediaWithFacesCountImpl(engineId)

    @Synchronized fun mediaWithFacesCount(mediaUris: List<String>, engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
        mediaWithFacesCountImpl(mediaUris, engineId)

    @Synchronized fun indexedFaceCount(engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
        indexedFaceCountImpl(engineId)

    @Synchronized fun getMediaUrisWithFaces(engineId: String = BuffaloLFaceEngine.ENGINE_ID): Set<String> =
        getMediaUrisWithFacesImpl(engineId)

    @Synchronized fun failedMediaCount(engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
        failedMediaCountImpl(engineId)

    @Synchronized fun failedMediaCount(mediaUris: List<String>, engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
        failedMediaCountImpl(mediaUris, engineId)

    @Synchronized fun clusterCount(engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
        clusterCountImpl(engineId)

    @Synchronized fun personCount(includeHidden: Boolean = false, engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
        personCountImpl(includeHidden, engineId)

    @Synchronized fun addFace(
        mediaUri: String,
        faceIndex: Int,
        rawEmbedding: FloatArray,
        maxDistance: Float = 0.60f,
        bounds: RectF? = null,
        confidence: Float = 1.0f,
        quality: Float = 1.0f,
        provenance: EmbeddingProvenance = EmbeddingProvenance.BUFFALO_L,
        qualityMetrics: FaceQualityMetrics? = null
    ): String = addFaceImpl(mediaUri, faceIndex, rawEmbedding, maxDistance, bounds, confidence, quality, provenance, qualityMetrics)

    @Synchronized fun addRawFaceObservation(
        mediaUri: String,
        faceIndex: Int,
        rawEmbedding: FloatArray,
        bounds: RectF? = null,
        confidence: Float = 1.0f,
        quality: Float = 1.0f,
        provenance: EmbeddingProvenance = EmbeddingProvenance.ENTE,
        qualityMetrics: FaceQualityMetrics? = null
    ): String = addRawFaceObservationImpl(mediaUri, faceIndex, rawEmbedding, bounds, confidence, quality, provenance, qualityMetrics)

    @Synchronized fun saveFaceMetrics(mediaUri: String, faceIndex: Int, engineId: String, metrics: FaceQualityMetrics) =
        saveFaceMetricsImpl(mediaUri, faceIndex, engineId, metrics)

    @Synchronized fun getFaceMetrics(mediaUri: String, faceIndex: Int, engineId: String = BuffaloLFaceEngine.ENGINE_ID): FaceQualityMetrics? =
        getFaceMetricsImpl(mediaUri, faceIndex, engineId)

    @Synchronized fun renamePerson(personId: String, name: String) = renamePersonImpl(personId, name)

    @Synchronized fun renameCluster(clusterId: String, name: String) = renameClusterImpl(clusterId, name)

    @Synchronized fun mergePeople(sourcePersonId: String, targetPersonId: String): Boolean =
        mergePeopleImpl(sourcePersonId, targetPersonId)

    @Synchronized fun splitCluster(clusterId: String, newPersonName: String = ""): String =
        splitClusterImpl(clusterId, newPersonName)

    @Synchronized fun removeFaceFromPerson(
        mediaUri: String,
        faceIndex: Int,
        engineId: String = BuffaloLFaceEngine.ENGINE_ID
    ): Boolean = removeFaceFromPersonImpl(mediaUri, faceIndex, engineId)

    @Synchronized fun setPersonHidden(personId: String, isHidden: Boolean) = setPersonHiddenImpl(personId, isHidden)

    @Synchronized fun setPersonCover(personId: String, mediaUri: String) = setPersonCoverImpl(personId, mediaUri)

    @Synchronized fun unassignedFaceCount(engineId: String, modelVersion: String): Int =
        unassignedFaceCountImpl(engineId, modelVersion)

    @Synchronized fun faceEmbeddingCount(engineId: String, modelVersion: String): Int =
        faceEmbeddingCountImpl(engineId, modelVersion)

    @Synchronized fun isGroupingRequired(engineId: String, modelVersion: String, strategyVersion: Int): Boolean =
        isGroupingRequiredImpl(engineId, modelVersion, strategyVersion)

    @Synchronized fun groupingFailure(engineId: String, modelVersion: String, strategyVersion: Int): String? =
        groupingFailureImpl(engineId, modelVersion, strategyVersion)

    @Synchronized fun markGroupingPending(engineId: String, modelVersion: String, strategyVersion: Int, faceCount: Int) =
        markGroupingPendingImpl(engineId, modelVersion, strategyVersion, faceCount)

    @Synchronized fun markGroupingCompleted(engineId: String, modelVersion: String, strategyVersion: Int, groupedPeople: Int, faceCount: Int) =
        markGroupingCompletedImpl(engineId, modelVersion, strategyVersion, groupedPeople, faceCount)

    @Synchronized fun markGroupingFailed(engineId: String, modelVersion: String, strategyVersion: Int, errorMessage: String, faceCount: Int) =
        markGroupingFailedImpl(engineId, modelVersion, strategyVersion, errorMessage, faceCount)

    internal fun pruneEmptyAutoPeople(db: SQLiteDatabase, engineId: String): Int =
        pruneEmptyAutoPeopleImpl(db, engineId)

    @Synchronized fun ensurePersonCovers(engineId: String) = ensurePersonCoversImpl(engineId)

    @Synchronized fun personSummaries(includeHidden: Boolean = false, engineId: String = BuffaloLFaceEngine.ENGINE_ID): List<PersonSummary> =
        personSummariesImpl(includeHidden, engineId)

    @Synchronized fun summaries(engineId: String = BuffaloLFaceEngine.ENGINE_ID): List<FaceClusterSummary> =
        summariesImpl(engineId)

    @Synchronized fun personMediaIndex(engineId: String, mediaUris: Collection<String>): Map<String, PersonMediaIndex> =
        personMediaIndexImpl(engineId, mediaUris)

    @Synchronized fun facesForCluster(clusterOrPersonId: String, engineId: String? = null): List<StoredFace> =
        facesForClusterImpl(clusterOrPersonId, engineId)

    @Synchronized fun facesForMedia(mediaUri: String, engineId: String = BuffaloLFaceEngine.ENGINE_ID): List<DetectedMediaFace> =
        facesForMediaImpl(mediaUri, engineId)

    @Synchronized fun findCommonIndexedMediaUri(engineA: String = BuffaloLFaceEngine.ENGINE_ID, engineB: String = EnteFaceConstants.ENGINE_ID): String? =
        findCommonIndexedMediaUriImpl(engineA, engineB)

    @Synchronized fun pruneStaleFacesForMedia(mediaUri: String, engineId: String, currentFaceCount: Int) =
        pruneStaleFacesForMediaImpl(mediaUri, engineId, currentFaceCount)

    @Synchronized fun pruneStaleFacesForMedia(mediaUri: String, engineId: String, keptIndices: Collection<Int>) =
        pruneStaleFacesForMediaImpl(mediaUri, engineId, keptIndices)

    @Synchronized fun deleteMediaCompletely(mediaUris: Collection<String>) = deleteMediaCompletelyImpl(mediaUris)

    @Synchronized fun compareEnginesOnMedia(
        mediaUri: String,
        engineA: String = BuffaloLFaceEngine.ENGINE_ID,
        engineB: String = EnteFaceConstants.ENGINE_ID,
        iouThreshold: Float = 0.40f,
        context: Context? = null
    ): EngineComparisonResult = compareEnginesOnMediaImpl(mediaUri, engineA, engineB, iouThreshold, context)

    @Synchronized fun getPendingSuggestions(engineId: String = BuffaloLFaceEngine.ENGINE_ID): List<ReviewSuggestion> =
        getPendingSuggestionsImpl(engineId)

    @Synchronized fun acceptSuggestion(suggestionId: String): Boolean = acceptSuggestionImpl(suggestionId)

    @Synchronized fun rejectSuggestion(suggestionId: String): Boolean = rejectSuggestionImpl(suggestionId)

    @Synchronized fun reclusterAllSafely(
        maxDistance: Float = 0.60f,
        engineId: String = BuffaloLFaceEngine.ENGINE_ID,
        modelVersion: String = BuffaloLFaceEngine.MODEL_VERSION
    ): Int = reclusterAllSafelyImpl(maxDistance, engineId, modelVersion)

    @Synchronized fun reclusterAll(maxDistance: Float = 0.60f): Int = reclusterAllSafely(maxDistance)

    @Synchronized fun resetClustersOnly(engineId: String = BuffaloLFaceEngine.ENGINE_ID) = resetClustersOnlyImpl(engineId)

    @Synchronized fun clearAllFaceData() = clearAllFaceDataImpl()

    @Synchronized fun getPersonName(personId: String): String = getPersonNameImpl(personId)

    @Synchronized fun ensurePersonForFace(mediaUri: String, faceIndex: Int, engineId: String): String =
        ensurePersonForFaceImpl(mediaUri, faceIndex, engineId)

    fun normalize(values: FloatArray): FloatArray = FaceVectorMath.normalize(values)
    fun cosineDistance(a: FloatArray, b: FloatArray): Float = FaceVectorMath.cosineDistance(a, b)
    fun floatsToBytes(values: FloatArray): ByteArray = FaceVectorMath.floatsToBytes(values)
    fun bytesToFloats(bytes: ByteArray): FloatArray = FaceVectorMath.bytesToFloats(bytes)

    companion object {
        const val DATABASE_NAME = FaceDbSchema.DATABASE_NAME
        const val DATABASE_VERSION = FaceDbSchema.DATABASE_VERSION

        fun normalize(values: FloatArray): FloatArray = FaceVectorMath.normalize(values)
        fun cosineDistance(a: FloatArray, b: FloatArray): Float = FaceVectorMath.cosineDistance(a, b)
        fun floatsToBytes(values: FloatArray): ByteArray = FaceVectorMath.floatsToBytes(values)
        fun bytesToFloats(bytes: ByteArray): FloatArray = FaceVectorMath.bytesToFloats(bytes)
    }
}
