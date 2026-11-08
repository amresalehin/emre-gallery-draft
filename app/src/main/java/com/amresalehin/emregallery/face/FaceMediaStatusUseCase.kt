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


internal fun FaceClusterStore.getFaceBoundsImpl(mediaUri: String, faceIndex: Int, engineId: String = BuffaloLFaceEngine.ENGINE_ID): RectF? {
    return readableDatabase.rawQuery(
        "SELECT box_left, box_top, box_right, box_bottom FROM faces WHERE media_uri=? AND face_index=? AND engine_id=? LIMIT 1",
        arrayOf(mediaUri, faceIndex.toString(), engineId)
    ).use { cursor ->
        if (cursor.moveToFirst()) {
            val left = cursor.getFloat(0)
            val top = cursor.getFloat(1)
            val right = cursor.getFloat(2)
            val bottom = cursor.getFloat(3)
            if (right > left && bottom > top) RectF(left, top, right, bottom) else null
        } else null
    }
}

// --- Media Indexing Status Partitioned by Engine ---
internal fun FaceClusterStore.isMediaIndexedImpl(uri: String, engineId: String = BuffaloLFaceEngine.ENGINE_ID): Boolean =
    readableDatabase.rawQuery(
        "SELECT 1 FROM indexed_media WHERE media_uri=? AND engine_id=? AND status='done' LIMIT 1",
        arrayOf(uri, engineId)
    ).use { it.moveToFirst() }
internal fun FaceClusterStore.getMediaIndexedInfoImpl(uri: String, engineId: String = BuffaloLFaceEngine.ENGINE_ID): MediaIndexedInfo? {
    return readableDatabase.rawQuery(
        "SELECT media_uri, engine_id, status, face_count, error_message, indexed_at FROM indexed_media WHERE media_uri=? AND engine_id=? LIMIT 1",
        arrayOf(uri, engineId)
    ).use { cursor ->
        if (cursor.moveToFirst()) {
            val errorCol = cursor.getColumnIndex("error_message")
            val err = if (errorCol >= 0 && !cursor.isNull(errorCol)) cursor.getString(errorCol) else null
            MediaIndexedInfo(
                mediaUri = cursor.getString(0),
                engineId = cursor.getString(1),
                status = cursor.getString(2),
                faceCount = cursor.getInt(3),
                errorMessage = err,
                indexedAt = cursor.getLong(5)
            )
        } else null
    }
}
internal fun FaceClusterStore.clearMediaFacesImpl(mediaUri: String, engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        db.delete("faces", "media_uri=? AND engine_id=?", arrayOf(mediaUri, engineId))
        db.delete("indexed_media", "media_uri=? AND engine_id=?", arrayOf(mediaUri, engineId))
        db.delete("review_suggestions", "media_uri=? AND engine_id=?", arrayOf(mediaUri, engineId))
        db.delete("face_metrics_v2", "media_uri=? AND engine_id=?", arrayOf(mediaUri, engineId))
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
    FaceStoreEvents.notifyMediaUpdated(mediaUri)
}
internal fun FaceClusterStore.getFailedMediaImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID): List<Pair<String, String?>> {
    val list = mutableListOf<Pair<String, String?>>()
    readableDatabase.rawQuery(
        "SELECT media_uri, error_message FROM indexed_media WHERE engine_id=? AND status='failed'",
        arrayOf(engineId)
    ).use { cursor ->
        val errCol = cursor.getColumnIndex("error_message")
        while (cursor.moveToNext()) {
            val uri = cursor.getString(0)
            val err = if (errCol >= 0 && !cursor.isNull(errCol)) cursor.getString(errCol) else null
            list.add(uri to err)
        }
    }
    return list
}

/**
 * Returns failures scoped to the current media library, matching failedMediaCount(mediaUris, ...).
 */
internal fun FaceClusterStore.getFailedMediaImpl(
    mediaUris: List<String>,
    engineId: String = BuffaloLFaceEngine.ENGINE_ID
): List<Pair<String, String?>> {
    val uniqueUris = mediaUris.distinct()
    if (uniqueUris.isEmpty()) return emptyList()
    val list = mutableListOf<Pair<String, String?>>()
    for (batch in uniqueUris.chunked(800)) {
        val placeholders = batch.joinToString(",") { "?" }
        val args = arrayOf(engineId, *batch.toTypedArray())
        readableDatabase.rawQuery(
            "SELECT media_uri, error_message FROM indexed_media WHERE engine_id=? AND status='failed' AND media_uri IN ($placeholders)",
            args
        ).use { cursor ->
            val errorColumn = cursor.getColumnIndex("error_message")
            while (cursor.moveToNext()) {
                val uri = cursor.getString(0)
                val error = if (errorColumn >= 0 && !cursor.isNull(errorColumn)) cursor.getString(errorColumn) else null
                list.add(uri to error)
            }
        }
    }
    return list
}
internal fun FaceClusterStore.markMediaFailedImpl(uri: String, engineId: String = BuffaloLFaceEngine.ENGINE_ID, errorMessage: String? = null) {
    val values = ContentValues().apply {
        put("media_uri", uri)
        put("engine_id", engineId)
        put("indexed_at", System.currentTimeMillis())
        put("face_count", 0)
        put("status", "failed")
        if (errorMessage != null) {
            put("error_message", errorMessage)
        }
    }
    writableDatabase.insertWithOnConflict("indexed_media", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    FaceStoreEvents.notifyMediaUpdated(uri)
}
internal fun FaceClusterStore.markMediaIndexedImpl(uri: String, detectedFaces: Int, engineId: String = BuffaloLFaceEngine.ENGINE_ID) {
    val values = ContentValues().apply {
        put("media_uri", uri)
        put("engine_id", engineId)
        put("indexed_at", System.currentTimeMillis())
        put("face_count", detectedFaces)
        put("status", "done")
        putNull("error_message")
    }
    writableDatabase.insertWithOnConflict("indexed_media", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    FaceStoreEvents.notifyMediaUpdated(uri)
}
internal fun FaceClusterStore.indexedMediaCountImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
    readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM indexed_media WHERE engine_id=? AND status='done'",
        arrayOf(engineId)
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
internal fun FaceClusterStore.indexedMediaCountImpl(mediaUris: List<String>, engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int {
    val uniqueUris = mediaUris.distinct()
    if (uniqueUris.isEmpty()) return 0
    return uniqueUris.chunked(800).sumOf { batch ->
        val placeholders = batch.joinToString(",") { "?" }
        val args = arrayOf(engineId, *batch.toTypedArray())
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM indexed_media WHERE engine_id=? AND status='done' AND media_uri IN ($placeholders)",
            args
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }
}
internal fun FaceClusterStore.mediaWithFacesCountImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
    readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM indexed_media WHERE engine_id=? AND status='done' AND face_count > 0",
        arrayOf(engineId)
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
internal fun FaceClusterStore.mediaWithFacesCountImpl(mediaUris: List<String>, engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int {
    val uniqueUris = mediaUris.distinct()
    if (uniqueUris.isEmpty()) return 0
    return uniqueUris.chunked(800).sumOf { batch ->
        val placeholders = batch.joinToString(",") { "?" }
        val args = arrayOf(engineId, *batch.toTypedArray())
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM indexed_media WHERE engine_id=? AND status='done' AND face_count > 0 AND media_uri IN ($placeholders)",
            args
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }
}
internal fun FaceClusterStore.indexedFaceCountImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
    readableDatabase.rawQuery("SELECT COUNT(*) FROM faces WHERE engine_id=?", arrayOf(engineId))
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }
internal fun FaceClusterStore.getMediaUrisWithFacesImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID): Set<String> {
    val result = mutableSetOf<String>()
    runCatching {
        readableDatabase.rawQuery(
            "SELECT DISTINCT media_uri FROM faces WHERE engine_id=?",
            arrayOf(engineId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0))
            }
        }
        readableDatabase.rawQuery(
            "SELECT DISTINCT media_uri FROM indexed_media WHERE engine_id=? AND status='done' AND face_count > 0",
            arrayOf(engineId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0))
            }
        }
    }
    return result
}
internal fun FaceClusterStore.failedMediaCountImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
    readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM indexed_media WHERE engine_id=? AND status='failed'",
        arrayOf(engineId)
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
internal fun FaceClusterStore.failedMediaCountImpl(mediaUris: List<String>, engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int {
    val uniqueUris = mediaUris.distinct()
    if (uniqueUris.isEmpty()) return 0
    return uniqueUris.chunked(800).sumOf { batch ->
        val placeholders = batch.joinToString(",") { "?" }
        val args = arrayOf(engineId, *batch.toTypedArray())
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM indexed_media WHERE engine_id=? AND status='failed' AND media_uri IN ($placeholders)",
            args
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }
}
internal fun FaceClusterStore.clusterCountImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int =
    readableDatabase.rawQuery("SELECT COUNT(*) FROM clusters WHERE engine_id=?", arrayOf(engineId))
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }
internal fun FaceClusterStore.personCountImpl(includeHidden: Boolean = false, engineId: String = BuffaloLFaceEngine.ENGINE_ID): Int {
    val clause = if (includeHidden) "WHERE engine_id=?" else "WHERE engine_id=? AND is_hidden = 0"
    return readableDatabase.rawQuery("SELECT COUNT(*) FROM people $clause", arrayOf(engineId))
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }
}
