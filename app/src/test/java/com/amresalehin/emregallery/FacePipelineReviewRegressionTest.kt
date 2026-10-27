package com.amresalehin.emregallery

import android.content.ContentValues
import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emregallery.face.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FacePipelineReviewRegressionTest {
    private lateinit var context: Context
    private lateinit var store: FaceClusterStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(FaceClusterStore.DATABASE_NAME)
        store = FaceClusterStore(context)
    }

    private fun embedding(seed: Int, dim: Int = 512) = FloatArray(dim) { index ->
        if (index / 64 == seed % (dim / 64)) 1f + (index % 5) * 0.001f else 0f
    }

    private fun replacement(index: Int, seed: Int, left: Float) = FaceObservationReplacement(
        faceIndex = index, embedding = embedding(seed), bounds = RectF(left, 10f, left + 40f, 50f),
        confidence = 0.9f, quality = 0.8f, qualityMetrics = null
    )

    @Test
    fun lateHighScoreCandidateSurvivesBoundedSelection() {
        val rows = 600
        val stride = 16
        val tensor = FloatArray(rows * stride)
        for (row in 0 until rows) {
            val offset = row * stride
            tensor[offset] = 10f + (row % 25) * 20f
            tensor[offset + 1] = 10f + (row / 25) * 20f
            tensor[offset + 2] = 8f; tensor[offset + 3] = 8f; tensor[offset + 4] = 1f
            tensor[offset + 15] = if (row == rows - 1) 0.99f else 0.60f
        }
        val result = EnteFaceDetector.decodeTensorOutput(tensor, rows, stride, 640, 640, 1f, 0f, 0f, 0.5f, 0.4f)
        assertEquals(0.99f, result.maxOf { it.score }, 0.0001f)
    }

    @Test
    fun groupingRevisionIncludesRecognitionDistance() {
        val base = EnteFaceClusteringStrategy.strategyVersion
        assertNotEquals(groupingRevision(base, 0.40f), groupingRevision(base, 0.45f))
        assertEquals(groupingRevision(base, 0.40f), groupingRevision(base, 0.40f))
    }

    @Test
    fun changingRecognitionDistanceInvalidatesCompletedGrouping() {
        val engine = EnteFaceConstants.ENGINE_ID
        val model = EnteFaceConstants.MODEL_VERSION
        val revision = groupingRevision(EnteFaceClusteringStrategy.strategyVersion, 0.60f)
        FaceRecognitionSettings.setRecognitionDistance(context, engine, 0.60f)
        store.markGroupingCompleted(engine, model, revision, 1, 1)
        assertFalse(store.isGroupingRequired(engine, model, revision))

        FaceRecognitionSettings.setRecognitionDistance(context, engine, 0.45f)

        assertTrue(store.isGroupingRequired(
            engine,
            model,
            groupingRevision(EnteFaceClusteringStrategy.strategyVersion, 0.45f)
        ))
    }

    @Test
    fun atomicReplacementRetainsExplicitNonContiguousKeysAndRefreshesMetrics() {
        val uri = "content://media/non-contiguous"
        val provenance = EmbeddingProvenance.BUFFALO_L
        store.replaceMediaFacesAtomically(uri, listOf(replacement(0, 0, 10f), replacement(1, 1, 80f), replacement(2, 2, 150f)), provenance, 0.6f, false)
        store.replaceMediaFacesAtomically(uri, listOf(replacement(0, 3, 12f), replacement(2, 4, 152f)), provenance, 0.6f, false)
        val faces = store.facesForMedia(uri)
        assertEquals(listOf(0, 2), faces.map { it.faceIndex })
        assertEquals(12f, faces.first().boxLeft, 0.001f)
        assertNotNull(store.getFaceMetrics(uri, 0)); assertNotNull(store.getFaceMetrics(uri, 2)); assertNull(store.getFaceMetrics(uri, 1))
        assertEquals("done", store.getMediaIndexedInfo(uri)?.status)
        assertEquals(2, store.getMediaIndexedInfo(uri)?.faceCount)
    }

    @Test
    fun failedReplacementPreservesPreviousCommittedGeneration() {
        val uri = "content://media/rollback"
        val provenance = EmbeddingProvenance.BUFFALO_L
        store.replaceMediaFacesAtomically(uri, listOf(replacement(0, 0, 10f)), provenance, 0.6f, false)
        val before = store.facesForMedia(uri).single()
        assertThrows(IllegalArgumentException::class.java) {
            store.replaceMediaFacesAtomically(uri, listOf(replacement(0, 0, 10f), replacement(0, 1, 80f)), provenance, 0.6f, false)
        }
        assertEquals(before.boxLeft, store.facesForMedia(uri).single().boxLeft, 0.001f)
        assertEquals(1, store.getMediaIndexedInfo(uri)?.faceCount)
    }

    @Test
    fun zeroFaceReplacementRemovesDependentRowsAndCompletesCheckpoint() {
        val uri = "content://media/zero-faces"
        val provenance = EmbeddingProvenance.BUFFALO_L
        store.replaceMediaFacesAtomically(uri, listOf(replacement(0, 0, 10f)), provenance, 0.6f, false)
        store.replaceMediaFacesAtomically(uri, emptyList(), provenance, 0.6f, false)
        assertTrue(store.facesForMedia(uri).isEmpty()); assertNull(store.getFaceMetrics(uri, 0))
        assertEquals("done", store.getMediaIndexedInfo(uri)?.status)
        assertEquals(0, store.getMediaIndexedInfo(uri)?.faceCount)
    }

    @Test
    fun suggestionAcceptanceUsesStoredUriAndRepairsClusters() {
        val sourceUri = "content://media/photo#fragment"
        val targetUri = "content://media/target"
        val provenance = EmbeddingProvenance.BUFFALO_L
        store.replaceMediaFacesAtomically(sourceUri, listOf(replacement(0, 0, 10f)), provenance, 0.2f, false)
        store.replaceMediaFacesAtomically(targetUri, listOf(replacement(0, 1, 10f)), provenance, 0.2f, false)
        val targetPerson = store.facesForMedia(targetUri).single().personId!!
        val targetCluster = store.facesForMedia(targetUri).single().clusterId
        val suggestionId = "uri-safe-suggestion"
        store.writableDatabase.insertOrThrow("review_suggestions", null, ContentValues().apply {
            put("id", suggestionId); put("face_id", "buffalo_l#$sourceUri#0"); put("media_uri", sourceUri)
            put("face_index", 0); put("engine_id", provenance.engineId); put("suggested_person_id", targetPerson)
            put("distance", 0.3f); put("status", "pending"); put("created_at", System.currentTimeMillis())
        })
        assertTrue(store.acceptSuggestionAtomically(suggestionId))
        val accepted = store.facesForMedia(sourceUri).single()
        assertEquals(targetPerson, accepted.personId); assertEquals(targetCluster, accepted.clusterId)
        store.readableDatabase.rawQuery("SELECT face_count FROM clusters WHERE id=?", arrayOf(targetCluster)).use {
            assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0))
        }
    }

    @Test
    fun completeResetClearsMetricsAndGroupingState() {
        val uri = "content://media/reset"
        store.replaceMediaFacesAtomically(uri, listOf(replacement(0, 0, 10f)), EmbeddingProvenance.BUFFALO_L, 0.6f, false)
        store.markGroupingCompleted(BuffaloLFaceEngine.ENGINE_ID, BuffaloLFaceEngine.MODEL_VERSION, 1, 1, 1)
        store.clearAllFaceDataCompletely()
        assertEquals(0, store.indexedFaceCount()); assertEquals(0, store.indexedMediaCount())
        store.readableDatabase.rawQuery("SELECT COUNT(*) FROM grouping_state", null).use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        store.readableDatabase.rawQuery("SELECT COUNT(*) FROM face_metrics_v2", null).use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
    }
}
