package com.amresalehin.emregallery.face

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.sqrt

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
    val embedding: FloatArray
)

/**
 * Persistent, incremental face grouping. Embeddings and identity groups stay in app-private storage.
 * Recognition uses cosine distance (1 - cosine similarity); smaller distances mean closer matches.
 */
class FaceClusterStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "face_index.db", null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE clusters (
                id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                centroid BLOB NOT NULL,
                face_count INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE faces (
                media_uri TEXT NOT NULL,
                face_index INTEGER NOT NULL,
                cluster_id TEXT NOT NULL,
                embedding BLOB NOT NULL,
                PRIMARY KEY(media_uri, face_index),
                FOREIGN KEY(cluster_id) REFERENCES clusters(id)
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX faces_cluster_idx ON faces(cluster_id)")
        db.execSQL(
            """CREATE TABLE indexed_media (
                media_uri TEXT PRIMARY KEY,
                indexed_at INTEGER NOT NULL,
                face_count INTEGER NOT NULL,
                status TEXT NOT NULL
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Keep existing face data when schema versions are added in future.
    }

    @Synchronized
    fun isMediaIndexed(uri: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM indexed_media WHERE media_uri=? AND status='done' LIMIT 1",
            arrayOf(uri)
        ).use { it.moveToFirst() }

    @Synchronized
    fun markMediaFailed(uri: String) {
        val values = ContentValues().apply {
            put("media_uri", uri)
            put("indexed_at", System.currentTimeMillis())
            put("face_count", 0)
            put("status", "failed")
        }
        writableDatabase.insertWithOnConflict("indexed_media", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Inserts one face and assigns it to the closest existing centroid, or creates a new person. */
    @Synchronized
    fun addFace(mediaUri: String, faceIndex: Int, rawEmbedding: FloatArray, maxDistance: Float = 0.40f): String {
        require(rawEmbedding.isNotEmpty()) { "Face embedding is empty." }
        val embedding = normalize(rawEmbedding)
        val db = writableDatabase
        val existing = db.rawQuery(
            "SELECT cluster_id FROM faces WHERE media_uri=? AND face_index=?",
            arrayOf(mediaUri, faceIndex.toString())
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (existing != null) return existing

        var bestId: String? = null
        var bestDistance = Float.POSITIVE_INFINITY
        db.rawQuery("SELECT id, centroid FROM clusters", null).use { cursor ->
            while (cursor.moveToNext()) {
                val centroid = bytesToFloats(cursor.getBlob(1))
                val distance = cosineDistance(embedding, centroid)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestId = cursor.getString(0)
                }
            }
        }

        val clusterId = if (bestId != null && bestDistance <= maxDistance) {
            bestId!!
        } else {
            UUID.randomUUID().toString().also { id ->
                val values = ContentValues().apply {
                    put("id", id)
                    put("display_name", "Person ${nextPersonNumber(db)}")
                    put("centroid", floatsToBytes(embedding))
                    put("face_count", 0)
                    put("updated_at", System.currentTimeMillis())
                }
                db.insertOrThrow("clusters", null, values)
            }
        }

        db.beginTransaction()
        try {
            val faceValues = ContentValues().apply {
                put("media_uri", mediaUri)
                put("face_index", faceIndex)
                put("cluster_id", clusterId)
                put("embedding", floatsToBytes(embedding))
            }
            db.insertOrThrow("faces", null, faceValues)
            recomputeCentroid(db, clusterId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return clusterId
    }

    @Synchronized
    fun markMediaIndexed(uri: String, detectedFaces: Int) {
        val values = ContentValues().apply {
            put("media_uri", uri)
            put("indexed_at", System.currentTimeMillis())
            put("face_count", detectedFaces)
            put("status", "done")
        }
        writableDatabase.insertWithOnConflict("indexed_media", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun summaries(): List<FaceClusterSummary> {
        val result = mutableListOf<FaceClusterSummary>()
        readableDatabase.rawQuery(
            """SELECT c.id, c.display_name, c.face_count,
               (SELECT f.media_uri FROM faces f WHERE f.cluster_id=c.id LIMIT 1)
               FROM clusters c ORDER BY c.face_count DESC, c.display_name""".trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += FaceClusterSummary(
                    id = cursor.getString(0),
                    displayName = cursor.getString(1),
                    faceCount = cursor.getInt(2),
                    sampleUri = if (cursor.isNull(3)) null else cursor.getString(3)
                )
            }
        }
        return result
    }

    @Synchronized
    fun facesForCluster(clusterId: String): List<StoredFace> {
        val result = mutableListOf<StoredFace>()
        readableDatabase.rawQuery(
            "SELECT media_uri, face_index, cluster_id, embedding FROM faces WHERE cluster_id=?",
            arrayOf(clusterId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += StoredFace(
                    mediaUri = cursor.getString(0),
                    faceIndex = cursor.getInt(1),
                    clusterId = cursor.getString(2),
                    embedding = bytesToFloats(cursor.getBlob(3))
                )
            }
        }
        return result
    }

    @Synchronized
    fun renameCluster(clusterId: String, name: String) {
        val cleanName = name.trim().take(80)
        if (cleanName.isEmpty()) return
        writableDatabase.update(
            "clusters",
            ContentValues().apply { put("display_name", cleanName) },
            "id=?",
            arrayOf(clusterId)
        )
    }

    @Synchronized
    fun indexedMediaCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM indexed_media WHERE status='done'", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Counts successfully indexed media only within the supplied library scope. */
    @Synchronized
    fun indexedMediaCount(mediaUris: List<String>): Int {
        val uniqueUris = mediaUris.distinct()
        if (uniqueUris.isEmpty()) return 0
        return uniqueUris.chunked(800).sumOf { batch ->
            val placeholders = batch.joinToString(",") { "?" }
            readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM indexed_media WHERE status='done' AND media_uri IN ($placeholders)",
                batch.toTypedArray()
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        }
    }

    @Synchronized
    fun indexedFaceCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM faces", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    @Synchronized
    fun failedMediaCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM indexed_media WHERE status='failed'", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    @Synchronized
    fun clusterCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM clusters", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    @Synchronized
    fun reclusterAll(maxDistance: Float = 0.40f): Int {
        val db = writableDatabase
        db.beginTransaction()
        try {
            data class StoredFaceRow(val mediaUri: String, val faceIndex: Int, val embedding: FloatArray)
            val faceRows = mutableListOf<StoredFaceRow>()
            db.rawQuery("SELECT media_uri, face_index, embedding FROM faces", null).use { cursor ->
                while (cursor.moveToNext()) {
                    faceRows += StoredFaceRow(
                        mediaUri = cursor.getString(0),
                        faceIndex = cursor.getInt(1),
                        embedding = bytesToFloats(cursor.getBlob(2))
                    )
                }
            }
            if (faceRows.isEmpty()) {
                db.setTransactionSuccessful()
                return 0
            }

            db.delete("faces", null, null)
            db.delete("clusters", null, null)

            class WorkingCluster(val id: String, var centroid: FloatArray, val faces: MutableList<StoredFaceRow>)
            val clusterList = mutableListOf<WorkingCluster>()
            var personIdx = 1

            for (face in faceRows) {
                val normVec = normalize(face.embedding)
                var bestCluster: WorkingCluster? = null
                var bestDistance = Float.POSITIVE_INFINITY
                for (c in clusterList) {
                    val distance = cosineDistance(normVec, c.centroid)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestCluster = c
                    }
                }

                if (bestCluster != null && bestDistance <= maxDistance) {
                    bestCluster.faces.add(face)
                    val dim = normVec.size
                    val mean = FloatArray(dim)
                    for (f in bestCluster.faces) {
                        val v = normalize(f.embedding)
                        for (i in 0 until dim) mean[i] += v[i]
                    }
                    bestCluster.centroid = normalize(mean)
                } else {
                    val newId = UUID.randomUUID().toString()
                    clusterList.add(WorkingCluster(newId, normVec.copyOf(), mutableListOf(face)))
                }
            }

            for (c in clusterList) {
                val clusterVals = ContentValues().apply {
                    put("id", c.id)
                    put("display_name", "Person ${personIdx++}")
                    put("centroid", floatsToBytes(c.centroid))
                    put("face_count", c.faces.size)
                    put("updated_at", System.currentTimeMillis())
                }
                db.insertOrThrow("clusters", null, clusterVals)
                for (f in c.faces) {
                    val faceVals = ContentValues().apply {
                        put("media_uri", f.mediaUri)
                        put("face_index", f.faceIndex)
                        put("cluster_id", c.id)
                        put("embedding", floatsToBytes(normalize(f.embedding)))
                    }
                    db.insertOrThrow("faces", null, faceVals)
                }
            }

            db.setTransactionSuccessful()
            return clusterList.size
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun resetClustersOnly() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("clusters", null, null)
            db.delete("faces", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun clearAllFaceData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("faces", null, null)
            db.delete("clusters", null, null)
            db.delete("indexed_media", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun recomputeCentroid(db: SQLiteDatabase, clusterId: String) {
        val vectors = mutableListOf<FloatArray>()
        db.rawQuery("SELECT embedding FROM faces WHERE cluster_id=?", arrayOf(clusterId)).use { cursor ->
            while (cursor.moveToNext()) vectors += bytesToFloats(cursor.getBlob(0))
        }
        if (vectors.isEmpty()) {
            db.delete("clusters", "id=?", arrayOf(clusterId))
            return
        }
        val dimensions = vectors.first().size
        val mean = FloatArray(dimensions)
        vectors.forEach { vector ->
            if (vector.size == dimensions) for (i in vector.indices) mean[i] += vector[i]
        }
        val centroid = normalize(mean)
        db.update(
            "clusters",
            ContentValues().apply {
                put("centroid", floatsToBytes(centroid))
                put("face_count", vectors.size)
                put("updated_at", System.currentTimeMillis())
            },
            "id=?",
            arrayOf(clusterId)
        )
    }

    private fun nextPersonNumber(db: SQLiteDatabase): Int =
        db.rawQuery("SELECT COUNT(*) FROM clusters", null).use {
            if (it.moveToFirst()) it.getInt(0) + 1 else 1
        }

    private fun normalize(values: FloatArray): FloatArray {
        val norm = sqrt(values.sumOf { it.toDouble() * it.toDouble() }).toFloat()
        if (norm <= 1e-12f) return values.copyOf()
        return FloatArray(values.size) { values[it] / norm }
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return Float.POSITIVE_INFINITY
        var similarity = 0f
        for (i in a.indices) similarity += a[i] * b[i]
        return (1f - similarity).coerceAtLeast(0f)
    }

    private fun floatsToBytes(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        values.forEach(buffer::putFloat)
        return buffer.array()
    }

    private fun bytesToFloats(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.getFloat() }
    }

    companion object {
        private const val DATABASE_VERSION = 1
    }
}
