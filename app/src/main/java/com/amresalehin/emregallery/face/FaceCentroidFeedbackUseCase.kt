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


// --- Internal Helpers ---

internal fun FaceClusterStore.updateCentroidIncremental(
    db: SQLiteDatabase,
    clusterId: String,
    newEmbedding: FloatArray,
    engineId: String,
    modelVersion: String
) {
    var updated = false
    db.rawQuery("SELECT centroid, face_count FROM clusters WHERE id=?", arrayOf(clusterId)).use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) {
            val oldCentroid = bytesToFloats(cursor.getBlob(0))
            val oldCount = cursor.getInt(1)
            if (oldCount > 0 && oldCentroid.size == newEmbedding.size) {
                val newCount = oldCount + 1
                val newCentroidRaw = FloatArray(newEmbedding.size)
                for (i in newCentroidRaw.indices) {
                    newCentroidRaw[i] = oldCentroid[i] * oldCount + newEmbedding[i]
                }
                val updatedCentroid = normalize(newCentroidRaw)
                val vals = ContentValues().apply {
                    put("centroid", floatsToBytes(updatedCentroid))
                    put("face_count", newCount)
                    put("updated_at", System.currentTimeMillis())
                }
                db.update("clusters", vals, "id=?", arrayOf(clusterId))
                updated = true
            }
        }
    }
    if (!updated) {
        recomputeCentroid(db, clusterId, engineId, modelVersion)
    }
}

internal fun FaceClusterStore.recomputeCentroid(
    db: SQLiteDatabase,
    clusterId: String,
    engineId: String,
    modelVersion: String
) {
    val vectors = mutableListOf<FloatArray>()
    db.rawQuery(
        "SELECT embedding FROM faces WHERE cluster_id=? AND engine_id=? AND model_version=?",
        arrayOf(clusterId, engineId, modelVersion)
    ).use { cursor ->
        while (cursor.moveToNext()) vectors += bytesToFloats(cursor.getBlob(0))
    }

    if (vectors.isEmpty()) {
        db.delete("clusters", "id=?", arrayOf(clusterId))
        return
    }

    val dimensions = vectors.first().size
    val mean = FloatArray(dimensions)
    vectors.forEach { vector ->
        if (vector.size == dimensions) {
            for (i in vector.indices) mean[i] += vector[i]
        }
    }
    val centroid = normalize(mean)
    val vals = ContentValues().apply {
        put("centroid", floatsToBytes(centroid))
        put("face_count", vectors.size)
        put("updated_at", System.currentTimeMillis())
    }
    db.update("clusters", vals, "id=?", arrayOf(clusterId))
}

internal fun FaceClusterStore.recordFeedback(
    db: SQLiteDatabase,
    type: String,
    sourceId: String,
    targetId: String,
    notes: String? = null,
    engineId: String = BuffaloLFaceEngine.ENGINE_ID
) {
    val vals = ContentValues().apply {
        put("id", UUID.randomUUID().toString())
        put("feedback_type", type)
        put("source_id", sourceId)
        put("target_id", targetId)
        put("engine_id", engineId)
        put("notes", notes)
        put("created_at", System.currentTimeMillis())
    }
    db.insertWithOnConflict("face_feedback", null, vals, SQLiteDatabase.CONFLICT_REPLACE)
}

internal fun FaceClusterStore.getPersonIdForCluster(clusterId: String): String? {
    return readableDatabase.rawQuery("SELECT person_id FROM clusters WHERE id=?", arrayOf(clusterId))
        .use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }
}
internal fun FaceClusterStore.getPersonNameImpl(personId: String): String {
    return getPersonName(readableDatabase, personId)
}
internal fun FaceClusterStore.ensurePersonForFaceImpl(mediaUri: String, faceIndex: Int, engineId: String): String {
    if (FaceProcessingCoordinator.isBusy()) {
        val db = readableDatabase
        val existingPersonId = db.rawQuery(
            "SELECT person_id FROM faces WHERE media_uri=? AND face_index=? AND engine_id=?",
            arrayOf(mediaUri, faceIndex.toString(), engineId)
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
        if (!existingPersonId.isNullOrBlank()) return existingPersonId
        return ""
    }
    val db = writableDatabase
    db.rawQuery(
        "SELECT person_id, cluster_id FROM faces WHERE media_uri=? AND face_index=? AND engine_id=?",
        arrayOf(mediaUri, faceIndex.toString(), engineId)
    ).use { cursor ->
        if (cursor.moveToFirst()) {
            val pId = if (!cursor.isNull(0)) cursor.getString(0) else null
            if (!pId.isNullOrBlank()) return pId
            val cId = if (!cursor.isNull(1)) cursor.getString(1) else null
            if (!cId.isNullOrBlank()) {
                val clusterPerson = getPersonIdForCluster(cId)
                if (!clusterPerson.isNullOrBlank()) {
                    db.execSQL(
                        "UPDATE faces SET person_id=? WHERE media_uri=? AND face_index=? AND engine_id=?",
                        arrayOf(clusterPerson, mediaUri, faceIndex.toString(), engineId)
                    )
                    return clusterPerson
                }
                val now = System.currentTimeMillis()
                val pVals = ContentValues().apply {
                    put("id", cId)
                    put("name", "")
                    put("engine_id", engineId)
                    put("created_at", now)
                    put("updated_at", now)
                }
                db.insertWithOnConflict("people", null, pVals, SQLiteDatabase.CONFLICT_IGNORE)
                db.execSQL(
                    "UPDATE faces SET person_id=? WHERE media_uri=? AND face_index=? AND engine_id=?",
                    arrayOf(cId, mediaUri, faceIndex.toString(), engineId)
                )
                db.execSQL("UPDATE clusters SET person_id=? WHERE id=? AND engine_id=?", arrayOf(cId, cId, engineId))
                FaceStoreEvents.notifyAllUpdated()
                return cId
            }
        }
    }
    val newPersonId = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()
    val pVals = ContentValues().apply {
        put("id", newPersonId)
        put("name", "")
        put("engine_id", engineId)
        put("created_at", now)
        put("updated_at", now)
    }
    db.insertWithOnConflict("people", null, pVals, SQLiteDatabase.CONFLICT_IGNORE)
    val fVals = ContentValues().apply {
        put("person_id", newPersonId)
    }
    db.update(
        "faces",
        fVals,
        "media_uri=? AND face_index=? AND engine_id=?",
        arrayOf(mediaUri, faceIndex.toString(), engineId)
    )
    FaceStoreEvents.notifyAllUpdated()
    return newPersonId
}

internal fun FaceClusterStore.getPersonName(db: SQLiteDatabase, personId: String): String {
    return db.rawQuery("SELECT name FROM people WHERE id=?", arrayOf(personId))
        .use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else "" }
}
