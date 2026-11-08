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


internal fun FaceClusterStore.ensurePersonCoversImpl(engineId: String) {
    val db = writableDatabase
    db.execSQL(
        """UPDATE people
           SET cover_face_id = (
               SELECT best_face.face_id
               FROM faces best_face
               WHERE best_face.person_id = people.id
                 AND best_face.engine_id = people.engine_id
               ORDER BY best_face.quality_score DESC,
                        best_face.detection_confidence DESC,
                        ((best_face.box_right - best_face.box_left) *
                         (best_face.box_bottom - best_face.box_top)) DESC,
                        best_face.media_uri ASC, best_face.face_index ASC
               LIMIT 1
           )
           WHERE people.engine_id = ?
             AND EXISTS (
                SELECT 1 FROM faces f
                WHERE f.person_id = people.id AND f.engine_id = people.engine_id
             )
             AND (
                people.cover_face_id IS NULL OR
                NOT EXISTS (
                    SELECT 1 FROM faces current_face
                    WHERE current_face.face_id = people.cover_face_id
                      AND current_face.person_id = people.id
                      AND current_face.engine_id = people.engine_id
                )
             )""".trimIndent(),
        arrayOf(engineId)
    )
}

// --- Query APIs ---
internal fun FaceClusterStore.personSummariesImpl(
    includeHidden: Boolean = false,
    engineId: String = BuffaloLFaceEngine.ENGINE_ID
): List<PersonSummary> {
    ensurePersonCovers(engineId)
    val result = mutableListOf<PersonSummary>()
    val hiddenClause = if (includeHidden) "WHERE p.engine_id = ?" else "WHERE p.engine_id = ? AND p.is_hidden = 0"
    readableDatabase.rawQuery(
        """SELECT p.id, p.name,
                  COUNT(DISTINCT f.media_uri || '#' || f.face_index) AS face_count,
                  COALESCE(NULLIF(p.cover_media_uri, ''),
                      (SELECT cover.media_uri FROM faces cover
                       WHERE cover.face_id = p.cover_face_id AND cover.person_id = p.id
                         AND cover.engine_id = p.engine_id LIMIT 1),
                      (SELECT f2.media_uri FROM faces f2
                       WHERE f2.person_id = p.id AND f2.engine_id = p.engine_id
                       ORDER BY f2.quality_score DESC, f2.detection_confidence DESC,
                                ((f2.box_right - f2.box_left) * (f2.box_bottom - f2.box_top)) DESC,
                                f2.media_uri ASC, f2.face_index ASC LIMIT 1)
                  ) AS sample_uri,
                  COALESCE(
                      (SELECT portrait.media_uri FROM faces portrait
                       WHERE portrait.face_id = p.cover_face_id AND portrait.person_id = p.id
                         AND portrait.engine_id = p.engine_id LIMIT 1),
                      (SELECT f2.media_uri FROM faces f2
                       WHERE f2.person_id = p.id AND f2.engine_id = p.engine_id
                       ORDER BY f2.quality_score DESC, f2.detection_confidence DESC,
                                ((f2.box_right - f2.box_left) * (f2.box_bottom - f2.box_top)) DESC,
                                f2.media_uri ASC, f2.face_index ASC LIMIT 1)
                  ) AS portrait_uri,
                  COALESCE(
                      (SELECT portrait.face_index FROM faces portrait
                       WHERE portrait.face_id = p.cover_face_id AND portrait.person_id = p.id
                         AND portrait.engine_id = p.engine_id LIMIT 1),
                      (SELECT f2.face_index FROM faces f2
                       WHERE f2.person_id = p.id AND f2.engine_id = p.engine_id
                       ORDER BY f2.quality_score DESC, f2.detection_confidence DESC,
                                ((f2.box_right - f2.box_left) * (f2.box_bottom - f2.box_top)) DESC,
                                f2.media_uri ASC, f2.face_index ASC LIMIT 1)
                  ) AS portrait_face_index,
                  p.is_hidden,
                  COUNT(DISTINCT c.id) AS cluster_count,
                  p.updated_at,
                  p.engine_id
           FROM people p
           LEFT JOIN clusters c ON c.person_id = p.id AND c.engine_id = p.engine_id
           LEFT JOIN faces f ON f.person_id = p.id AND f.engine_id = p.engine_id
           $hiddenClause
           GROUP BY p.id
           HAVING COUNT(DISTINCT f.media_uri || '#' || f.face_index) > 0
           ORDER BY face_count DESC, p.updated_at DESC""".trimIndent(),
        arrayOf(engineId)
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getString(0)
            val rawName = cursor.getString(1).orEmpty()
            val faceCount = cursor.getInt(2)
            val sampleUri = if (cursor.isNull(3)) null else cursor.getString(3)
            val portraitUri = if (cursor.isNull(4)) null else cursor.getString(4)
            val portraitFaceIndex = if (cursor.isNull(5)) null else cursor.getInt(5)
            val isHidden = cursor.getInt(6) == 1
            val clusterCount = cursor.getInt(7)
            val updatedAt = cursor.getLong(8)
            val engId = cursor.getString(9).orEmpty()
            val isNamed = rawName.isNotBlank() && !rawName.equals("Unknown", ignoreCase = true)
            result += PersonSummary(
                id = id,
                displayName = rawName,
                faceCount = faceCount,
                sampleUri = sampleUri,
                isHidden = isHidden,
                isNamed = isNamed,
                clusterCount = clusterCount,
                updatedAt = updatedAt,
                engineId = engId,
                portraitUri = portraitUri,
                portraitFaceIndex = portraitFaceIndex
            )
        }
    }
    return result
}
internal fun FaceClusterStore.summariesImpl(engineId: String = BuffaloLFaceEngine.ENGINE_ID): List<FaceClusterSummary> {
    val people = personSummaries(includeHidden = false, engineId = engineId)
    return people.map { p ->
        FaceClusterSummary(
            id = p.id,
            displayName = p.displayName,
            faceCount = p.faceCount,
            sampleUri = p.sampleUri
        )
    }
}


/** Lightweight per-person photo counts/URIs; never reads embedding BLOBs. */
internal fun FaceClusterStore.personMediaIndexImpl(engineId: String, mediaUris: Collection<String>): Map<String, PersonMediaIndex> {
    if (mediaUris.isEmpty()) return emptyMap()
    val counts = linkedMapOf<String, Int>()
    val uris = linkedMapOf<String, MutableSet<String>>()
    for (chunk in mediaUris.toSet().toList().chunked(800)) {
        val marks = List(chunk.size) { "?" }.joinToString(",")
        readableDatabase.rawQuery(
            """SELECT person_id, media_uri, COUNT(*) FROM faces
               WHERE engine_id=? AND person_id IS NOT NULL AND media_uri IN ($marks)
               GROUP BY person_id, media_uri""".trimIndent(),
            arrayOf(engineId, *chunk.toTypedArray())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val personId = cursor.getString(0) ?: continue
                val uri = cursor.getString(1) ?: continue
                counts[personId] = (counts[personId] ?: 0) + cursor.getInt(2)
                uris.getOrPut(personId) { linkedSetOf() }.add(uri)
            }
        }
    }
    return counts.mapValues { (id, count) -> PersonMediaIndex(count, uris[id].orEmpty().toSet()) }
}
internal fun FaceClusterStore.facesForClusterImpl(clusterOrPersonId: String, engineId: String? = null): List<StoredFace> {
    val result = mutableListOf<StoredFace>()
    val engineClause = if (engineId != null) "AND engine_id=?" else ""
    val args = if (engineId != null) arrayOf(clusterOrPersonId, clusterOrPersonId, engineId)
        else arrayOf(clusterOrPersonId, clusterOrPersonId)

    readableDatabase.rawQuery(
        """SELECT media_uri, face_index, cluster_id, embedding, person_id,
                  box_left, box_top, box_right, box_bottom, detection_confidence,
                  quality_score, engine_id, model_version
           FROM faces
           WHERE (person_id=? OR cluster_id=?) $engineClause""",
        args
    ).use { cursor ->
        while (cursor.moveToNext()) {
            result += StoredFace(
                mediaUri = cursor.getString(0),
                faceIndex = cursor.getInt(1),
                clusterId = cursor.getString(2).orEmpty(),
                embedding = bytesToFloats(cursor.getBlob(3)),
                personId = if (cursor.isNull(4)) null else cursor.getString(4),
                boxLeft = cursor.getFloat(5),
                boxTop = cursor.getFloat(6),
                boxRight = cursor.getFloat(7),
                boxBottom = cursor.getFloat(8),
                confidence = cursor.getFloat(9),
                quality = cursor.getFloat(10),
                engineId = cursor.getString(11).orEmpty(),
                modelVersion = cursor.getString(12).orEmpty()
            )
        }
    }
    return result
}
internal fun FaceClusterStore.facesForMediaImpl(mediaUri: String, engineId: String = BuffaloLFaceEngine.ENGINE_ID): List<DetectedMediaFace> {
    ensurePersonCovers(engineId)
    val result = mutableListOf<DetectedMediaFace>()
    readableDatabase.rawQuery(
        """SELECT f.media_uri, f.face_index, f.cluster_id, COALESCE(NULLIF(TRIM(p.name), ''), ''),
                  COALESCE(f.person_id, c.person_id, NULLIF(f.cluster_id, '')), f.box_left, f.box_top, f.box_right, f.box_bottom, f.engine_id,
                  portrait.media_uri, portrait.face_index
           FROM faces f
           LEFT JOIN clusters c ON f.cluster_id = c.id AND f.engine_id = c.engine_id
           LEFT JOIN people p ON COALESCE(f.person_id, c.person_id, NULLIF(f.cluster_id, '')) = p.id AND f.engine_id = p.engine_id
           LEFT JOIN faces portrait ON portrait.face_id = p.cover_face_id
                AND portrait.person_id = p.id AND portrait.engine_id = p.engine_id
           WHERE f.media_uri = ? AND f.engine_id = ?
           ORDER BY f.face_index""".trimIndent(),
        arrayOf(mediaUri, engineId)
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val boxLeft = cursor.getFloat(5)
            val boxTop = cursor.getFloat(6)
            val boxRight = cursor.getFloat(7)
            val boxBottom = cursor.getFloat(8)
            if (boxRight <= boxLeft || boxBottom <= boxTop) continue
            result += DetectedMediaFace(
                mediaUri = cursor.getString(0),
                faceIndex = cursor.getInt(1),
                clusterId = cursor.getString(2).orEmpty(),
                displayName = cursor.getString(3).orEmpty().trim(),
                personId = if (cursor.isNull(4)) null else cursor.getString(4),
                boxLeft = boxLeft,
                boxTop = boxTop,
                boxRight = boxRight,
                boxBottom = boxBottom,
                engineId = cursor.getString(9).orEmpty(),
                portraitMediaUri = if (cursor.isNull(10)) null else cursor.getString(10),
                portraitFaceIndex = if (cursor.isNull(11)) null else cursor.getInt(11)
            )
        }
    }
    return result
}

// --- Cross-Engine Detection Comparison Mechanism ---
internal fun FaceClusterStore.findCommonIndexedMediaUriImpl(
    engineA: String = BuffaloLFaceEngine.ENGINE_ID,
    engineB: String = EnteFaceConstants.ENGINE_ID
): String? {
    val sql = """
        SELECT m1.media_uri 
        FROM indexed_media m1 
        INNER JOIN indexed_media m2 ON m1.media_uri = m2.media_uri 
        WHERE m1.engine_id = ? AND m2.engine_id = ? AND m1.status='done' AND m2.status='done'
        LIMIT 1
    """
    return readableDatabase.rawQuery(sql, arrayOf(engineA, engineB)).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}
internal fun FaceClusterStore.pruneStaleFacesForMediaImpl(mediaUri: String, engineId: String, currentFaceCount: Int) {
    val db = writableDatabase
    db.delete(
        "faces",
        "media_uri=? AND engine_id=? AND face_index >= ?",
        arrayOf(mediaUri, engineId, currentFaceCount.toString())
    )
}
internal fun FaceClusterStore.pruneStaleFacesForMediaImpl(mediaUri: String, engineId: String, keptIndices: Collection<Int>) {
    val db = writableDatabase
    if (keptIndices.isEmpty()) {
        db.delete("faces", "media_uri=? AND engine_id=?", arrayOf(mediaUri, engineId))
    } else {
        val placeholders = keptIndices.joinToString(",") { "?" }
        val args = arrayOf(mediaUri, engineId) + keptIndices.map { it.toString() }.toTypedArray()
        db.delete("faces", "media_uri=? AND engine_id=? AND face_index NOT IN ($placeholders)", args)
    }
}
internal fun FaceClusterStore.deleteMediaCompletelyImpl(mediaUris: Collection<String>) {
    if (mediaUris.isEmpty()) return
    val db = writableDatabase
    db.beginTransaction()
    try {
        for (uri in mediaUris) {
            db.delete("faces", "media_uri=?", arrayOf(uri))
            db.delete("indexed_media", "media_uri=?", arrayOf(uri))
            db.delete("review_suggestions", "media_uri=?", arrayOf(uri))
            db.delete("face_metrics_v2", "media_uri=?", arrayOf(uri))
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
    FaceStoreEvents.notifyAllUpdated()
}
