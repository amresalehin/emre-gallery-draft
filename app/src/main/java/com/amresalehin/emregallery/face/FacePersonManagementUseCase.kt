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


// --- People & Identity Management ---
internal fun FaceClusterStore.renamePersonImpl(personId: String, name: String) {
    if (personId.isBlank()) return
    val cleanName = name.trim().take(80)
    val db = writableDatabase
    db.beginTransaction()
    try {
        val now = System.currentTimeMillis()
        var engineId = BuffaloLFaceEngine.ENGINE_ID
        db.rawQuery("SELECT engine_id FROM people WHERE id=?", arrayOf(personId)).use {
            if (it.moveToFirst() && !it.isNull(0)) engineId = it.getString(0)
        }

        val personVals = ContentValues().apply {
            put("name", cleanName)
            put("updated_at", now)
        }
        db.update("people", personVals, "id=?", arrayOf(personId))

        val clusterVals = ContentValues().apply {
            put("display_name", cleanName)
            put("updated_at", now)
        }
        db.update("clusters", clusterVals, "person_id=?", arrayOf(personId))

        recordFeedback(db, "RENAME_PERSON", personId, cleanName, engineId = engineId)
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
        FaceStoreEvents.notifyAllUpdated()
    }
}
internal fun FaceClusterStore.renameClusterImpl(clusterId: String, name: String) {
    val personId = getPersonIdForCluster(clusterId) ?: clusterId
    renamePerson(personId, name)
}
internal fun FaceClusterStore.mergePeopleImpl(sourcePersonId: String, targetPersonId: String): Boolean {
    if (sourcePersonId == targetPersonId) return false
    val db = writableDatabase
    db.beginTransaction()
    try {
        val sourceName = getPersonName(db, sourcePersonId)
        val targetName = getPersonName(db, targetPersonId)
        val finalName = if (targetName.isNotBlank()) targetName else sourceName
        val now = System.currentTimeMillis()

        var engineId = BuffaloLFaceEngine.ENGINE_ID
        db.rawQuery("SELECT engine_id FROM people WHERE id=?", arrayOf(targetPersonId)).use {
            if (it.moveToFirst() && !it.isNull(0)) engineId = it.getString(0)
        }

        val clusterVals = ContentValues().apply {
            put("person_id", targetPersonId)
            put("display_name", finalName)
            put("updated_at", now)
        }
        db.update("clusters", clusterVals, "person_id=?", arrayOf(sourcePersonId))

        val faceVals = ContentValues().apply {
            put("person_id", targetPersonId)
        }
        db.update("faces", faceVals, "person_id=?", arrayOf(sourcePersonId))

        val targetVals = ContentValues().apply {
            put("name", finalName)
            put("updated_at", now)
        }
        db.update("people", targetVals, "id=?", arrayOf(targetPersonId))

        recordFeedback(db, "MERGE_PEOPLE", sourcePersonId, targetPersonId, engineId = engineId)
        db.delete("people", "id=?", arrayOf(sourcePersonId))
        db.delete("review_suggestions", "suggested_person_id=?", arrayOf(sourcePersonId))

        db.setTransactionSuccessful()
        return true
    } finally {
        db.endTransaction()
        FaceStoreEvents.notifyAllUpdated()
    }
}
internal fun FaceClusterStore.splitClusterImpl(clusterId: String, newPersonName: String = ""): String {
    val db = writableDatabase
    db.beginTransaction()
    try {
        val newPersonId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        var engineId = BuffaloLFaceEngine.ENGINE_ID
        db.rawQuery("SELECT engine_id FROM clusters WHERE id=?", arrayOf(clusterId)).use {
            if (it.moveToFirst() && !it.isNull(0)) engineId = it.getString(0)
        }

        val personVals = ContentValues().apply {
            put("id", newPersonId)
            put("name", newPersonName.trim().take(80))
            put("is_hidden", 0)
            put("engine_id", engineId)
            put("created_at", now)
            put("updated_at", now)
        }
        db.insertOrThrow("people", null, personVals)

        val clusterVals = ContentValues().apply {
            put("person_id", newPersonId)
            put("display_name", newPersonName.trim().take(80))
            put("updated_at", now)
        }
        db.update("clusters", clusterVals, "id=?", arrayOf(clusterId))

        val faceVals = ContentValues().apply {
            put("person_id", newPersonId)
        }
        db.update("faces", faceVals, "cluster_id=?", arrayOf(clusterId))

        recordFeedback(db, "SPLIT_CLUSTER", clusterId, newPersonId, engineId = engineId)
        db.setTransactionSuccessful()
        return newPersonId
    } finally {
        db.endTransaction()
        FaceStoreEvents.notifyAllUpdated()
    }
}
internal fun FaceClusterStore.removeFaceFromPersonImpl(
    mediaUri: String,
    faceIndex: Int,
    engineId: String = BuffaloLFaceEngine.ENGINE_ID
): Boolean {
    val db = writableDatabase
    db.beginTransaction()
    try {
        var oldClusterId: String? = null
        var oldPersonId: String? = null

        db.rawQuery(
            "SELECT cluster_id, person_id FROM faces WHERE media_uri=? AND face_index=? AND engine_id=?",
            arrayOf(mediaUri, faceIndex.toString(), engineId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                oldClusterId = cursor.getString(0)
                oldPersonId = if (cursor.isNull(1)) null else cursor.getString(1)
            }
        }

        if (oldClusterId == null) return false

        val faceId = "${engineId}#${mediaUri}#${faceIndex}"
        val newClusterId = UUID.randomUUID().toString()
        val newPersonId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val personVals = ContentValues().apply {
            put("id", newPersonId)
            put("name", "")
            put("cover_media_uri", mediaUri)
            put("is_hidden", 0)
            put("engine_id", engineId)
            put("created_at", now)
            put("updated_at", now)
        }
        db.insertOrThrow("people", null, personVals)

        var embBlob: ByteArray? = null
        var modelVersion = BuffaloLFaceEngine.MODEL_VERSION
        var dim = BuffaloLFaceEngine.EMBEDDING_DIMENSION
        db.rawQuery(
            "SELECT embedding, model_version, embedding_dim FROM faces WHERE media_uri=? AND face_index=? AND engine_id=?",
            arrayOf(mediaUri, faceIndex.toString(), engineId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                embBlob = cursor.getBlob(0)
                modelVersion = cursor.getString(1).orEmpty()
                dim = cursor.getInt(2)
            }
        }

        val clusterVals = ContentValues().apply {
            put("id", newClusterId)
            put("person_id", newPersonId)
            put("display_name", "")
            put("centroid", embBlob ?: ByteArray(0))
            put("embedding_dim", dim)
            put("engine_id", engineId)
            put("model_version", modelVersion)
            put("face_count", 1)
            put("created_at", now)
            put("updated_at", now)
        }
        db.insertOrThrow("clusters", null, clusterVals)

        val faceVals = ContentValues().apply {
            put("cluster_id", newClusterId)
            put("person_id", newPersonId)
        }
        db.update("faces", faceVals, "media_uri=? AND face_index=? AND engine_id=?", arrayOf(mediaUri, faceIndex.toString(), engineId))

        if (oldPersonId != null) {
            recordFeedback(db, "REMOVE_FACE_FROM_PERSON", faceId, oldPersonId, engineId = engineId)
        }
        if (oldClusterId != null) {
            recomputeCentroid(db, oldClusterId, engineId, modelVersion)
        }

        db.setTransactionSuccessful()
        return true
    } finally {
        db.endTransaction()
        FaceStoreEvents.notifyAllUpdated()
    }
}
internal fun FaceClusterStore.setPersonHiddenImpl(personId: String, isHidden: Boolean) {
    val vals = ContentValues().apply {
        put("is_hidden", if (isHidden) 1 else 0)
        put("updated_at", System.currentTimeMillis())
    }
    writableDatabase.update("people", vals, "id=?", arrayOf(personId))
    FaceStoreEvents.notifyAllUpdated()
}
internal fun FaceClusterStore.setPersonCoverImpl(personId: String, mediaUri: String) {
    val db = writableDatabase
    val selectedFaceId = db.rawQuery(
        """SELECT face_id FROM faces
           WHERE person_id=? AND media_uri=?
           ORDER BY quality_score DESC, detection_confidence DESC,
                    ((box_right - box_left) * (box_bottom - box_top)) DESC,
                    face_index ASC
           LIMIT 1""".trimIndent(),
        arrayOf(personId, mediaUri)
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    val vals = ContentValues().apply {
        put("cover_media_uri", mediaUri)
        // A cover photo is not necessarily a detected face. Only replace the stable
        // representative-face ID if that photo contains a face for this person.
        if (selectedFaceId != null) put("cover_face_id", selectedFaceId)
        put("updated_at", System.currentTimeMillis())
    }
    db.update("people", vals, "id=?", arrayOf(personId))
    FaceStoreEvents.notifyAllUpdated()
}
